package net.tutla.craftytml.paper.command.executor;

import net.tutla.craftytml.client.CTMLAPI;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.paper.command.Args;
import net.tutla.craftytml.paper.command.ExecutedUIs;
import net.tutla.craftytml.paper.command.Reply;
import net.tutla.craftytml.paper.command.Subcommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * {@code /ctml execute [targets] [name] [json] <ctml>}: markup written into the command itself. Targets are
 * a player's name or a selector such as {@code @a}, and whoever runs the command where there are none. The
 * json, where there is one, maps callback names to the commands they run. Everything after it is the
 * markup, built afresh for each target so every one of them has a tree of their own, never registered, and
 * gone once their window is.
 */
public final class ExecuteCommand extends Subcommand {
    /** The name an executed ui goes under when the command gives it none. */
    public static final String DEFAULT_NAME = "default";

    private final ExecutedUIs executed;

    public ExecuteCommand(ExecutedUIs executed){
        super("execute", "/ctml execute [targets] [name] [json] <ctml>", "Shows ctml written into the command itself.",
                "targets is a player or a selector like @a, and defaults to you.",
                "name is what variable and callback find it by, and defaults to default.",
                "json maps callbacks to commands run from the console: {\"buy\":\"give {player} diamond 1\"}.",
                "Everything after that is the ctml, built afresh for every target.");
        this.executed = executed;
    }

    @Override
    public boolean run(CommandSender sender, String[] args){
        if (args.length < 2) return usage(sender, "Give the ctml to show.");
        int at = 1;
        List<Player> targets;
        if (args[1].startsWith("@")) {
            try {
                targets = Bukkit.selectEntities(sender, args[1]).stream()
                        .filter(Player.class::isInstance).map(Player.class::cast).toList();
            } catch (IllegalArgumentException e) {
                return Reply.fail(sender, args[1] + " is not a valid selector: " + e.getMessage());
            }
            if (targets.isEmpty()) return Reply.fail(sender, args[1] + " matched no players.");
            at = 2;
        } else if (args.length > 2 && Bukkit.getPlayerExact(args[1]) != null) {
            targets = List.of(Bukkit.getPlayerExact(args[1]));
            at = 2;
        } else if (sender instanceof Player player) {
            targets = List.of(player);
        } else {
            return usage(sender, "The console has no window: name a player or a selector to show it to.");
        }

        if (args.length <= at) return usage(sender, "There is no ctml to show.");
        // the name is the word before the json or the ctml, where there is one: both of those start with a bracket
        String name = DEFAULT_NAME;
        if (!args[at].startsWith("<") && !args[at].startsWith("{")) {
            name = args[at];
            at++;
        }

        Args.Markup markup = Args.markup(sender, Args.rest(args, at));
        if (markup == null) return true;
        String ctml = markup.ctml();
        Map<String, String> callbacks = markup.callbacks();
        if (ctml.isEmpty()) return usage(sender, "There is no ctml to show.");
        if (!ctml.startsWith("<")) return Reply.fail(sender, "The ctml has to start with a tag, like <ctml>.");

        int shown = 0;
        for (Player target : targets) {
            DOMTreeElement tree = CTMLAPI.buildUI(ctml, Map.of("player", target));
            if (tree == null) {
                return Reply.fail(sender, "That ctml built no ui. The console has its errors."
                        + (shown > 0 ? " It was shown to " + shown + " player(s) before that." : ""));
            }
            if (Reply.show(sender, target, name, tree, executed.context(name, tree, target, callbacks))) shown++;
        }
        if (shown == 0) return true; // each failure has already been said
        String callbacksSaid = callbacks.isEmpty() ? "" : " with " + callbacks.size()
                + (callbacks.size() == 1 ? " callback" : " callbacks");
        return Reply.done(sender, "Showing " + name + callbacksSaid + " to "
                + (targets.size() == 1 ? Reply.whose(sender, targets.getFirst()) : shown + " players") + ".");
    }

    /**
     * Whatever can come next is offered, since any part can be left out: targets, then names, then the json
     * and the ctml, each only where the arguments before it leave room for it.
     */
    @Override
    public List<String> complete(CommandSender sender, String[] args){
        String typed = args[args.length - 1];
        int at = 1;
        List<String> options = new ArrayList<>();
        // the targets, the first time round
        if (args.length - 1 == at) {
            options.addAll(Args.SELECTORS);
            options.addAll(Args.players());
        }
        if (args.length - 1 > at && (args[at].startsWith("@") || Bukkit.getPlayerExact(args[at]) != null)) at++;
        // a name, where one can still come
        if (args.length - 1 == at) options.addAll(Args.uiNames(executed));
        if (args.length - 1 > at && !args[at].startsWith("<") && !args[at].startsWith("{")) at++;
        // then the json and the ctml, which both start with a bracket
        if (args.length - 1 == at) {
            options.add("{\"callback\":\"/command\"}");
            options.add("<ctml");
        } else if (args.length - 1 > at && args[at].startsWith("{")
                && !String.join(" ", Arrays.copyOfRange(args, at, args.length - 1)).contains("}")) {
            return List.of(); // still inside the json
        } else if (args.length - 1 > at) {
            if (!typed.isEmpty() && !typed.contains("<")) options.add(typed.contains("=") ? typed : "class=\"");
            options.addAll(List.of("<div", "<span", "<button", "</ctml>"));
        }
        return Args.matching(options, typed);
    }
}
