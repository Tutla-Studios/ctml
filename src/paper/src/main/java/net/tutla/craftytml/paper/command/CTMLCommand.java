package net.tutla.craftytml.paper.command;

import net.tutla.craftytml.client.CTMLAPI;
import net.tutla.craftytml.client.demo.DemoIntro;
import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.UIRegistry;
import net.tutla.craftytml.client.render.UIRenderer;
import net.tutla.craftytml.paper.CraftytmlPaper;
import net.tutla.craftytml.paper.render.WindowRenderer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * {@code /ctml}, for showing trees to players from the console or in game.
 *
 * <ul>
 *   <li>{@code /ctml open <name> [player]} shows a registered tree.
 *   <li>{@code /ctml run <script> [player]} runs a script in the ui folder afresh, for that player alone.
 *   <li>{@code /ctml demo [player]} shows the intro, and the full demo from it.
 *   <li>{@code /ctml close [player]} puts a window away, and {@code /ctml list} names the trees.
 *   <li>{@code /ctml execute [targets] <name> [json] <ctml>} shows markup written into the command itself.
 *   <li>{@code /ctml variable put|remove <name> <variable> [value]} and
 *       {@code /ctml callback put|remove <name> <callback> [command]} change what an executed ui has.
 * </ul>
 *
 * <p>A window opened with open, run or demo has an empty context, so its callbacks fire at nothing;
 * callbacks are put on a context in java, the same as on a client. An executed one is the exception: its
 * callbacks are commands, given as json in front of the markup, or kept for its name by
 * {@code /ctml callback}, with {@code {player}} in a command standing for whoever pressed it.
 */
public final class CTMLCommand implements TabExecutor {
    private final Path ui;
    private final ExecutedUIs executed = new ExecutedUIs();

    /** The command, running scripts out of this ui folder. */
    public CTMLCommand(Path ui){
        this.ui = ui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        if (args.length == 0) return false;
        switch (args[0]) {
            case "list" -> sender.sendMessage(UIRegistry.isEmpty()
                    ? "No ctml uis loaded."
                    : "ctml uis: " + String.join(", ", UIRegistry.getAllNames()));
            case "open" -> {
                if (args.length < 2) return false;
                Player target = target(sender, args, 2);
                if (target == null) return true;
                DOMTreeElement tree = UIRegistry.getUIFromName(args[1]);
                if (tree == null) {
                    sender.sendMessage("No ctml ui called " + args[1] + ".");
                    return true;
                }
                new WindowRenderer(target).show(args[1], tree, new DOMContext(tree));
            }
            case "run" -> {
                if (args.length < 2) return false;
                Player target = target(sender, args, 2);
                if (target == null) return true;
                Path file = ui.resolve(args[1]).normalize();
                DOMTreeElement tree = file.startsWith(ui) && Files.isRegularFile(file)
                        ? CTMLAPI.buildUI(file, Map.of("player", target))
                        : null;
                if (tree == null) {
                    sender.sendMessage(args[1] + " is not a script in the ui folder, or it built no ui.");
                    return true;
                }
                new WindowRenderer(target).show(args[1], tree, new DOMContext(tree));
            }
            case "demo" -> {
                Player target = target(sender, args, 1);
                if (target == null) return true;
                if (!DemoIntro.open(new WindowRenderer(target))) {
                    sender.sendMessage(DemoIntro.INTRO_UI + " is not in " + ui + ".");
                }
            }
            case "close" -> {
                Player target = target(sender, args, 1);
                if (target != null && !CraftytmlPaper.windows().close(target)) sender.sendMessage("No window open.");
            }
            case "execute" -> {
                return execute(sender, args);
            }
            case "variable" -> {
                if (args.length < 4) return false;
                String name = args[2], variable = args[3];
                switch (args[1]) {
                    case "put" -> {
                        if (args.length < 5) return false;
                        executed.putVariable(name, variable, rest(args, 4));
                    }
                    case "remove" -> {
                        if (!executed.removeVariable(name, variable)) sender.sendMessage(name + " has no variable " + variable + ".");
                    }
                    default -> {
                        return false;
                    }
                }
            }
            case "callback" -> {
                if (args.length < 4) return false;
                String name = args[2], callback = args[3];
                switch (args[1]) {
                    case "put" -> {
                        if (args.length < 5) return false;
                        executed.putCallback(name, callback, rest(args, 4));
                    }
                    case "remove" -> {
                        if (!executed.removeCallback(name, callback)) sender.sendMessage(name + " has no callback " + callback + ".");
                    }
                    default -> {
                        return false;
                    }
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * {@code /ctml execute [targets] <name> [json] <ctml>}. Targets are a player's name or a selector such as
     * {@code @a}, and whoever runs the command where there are none. The json, where there is one, maps
     * callback names to the commands they run. Everything after it is the markup, built afresh for each
     * target so every one of them has a tree of their own, never registered, and gone once their window is.
     */
    private boolean execute(CommandSender sender, String[] args){
        int at = 1;
        List<Player> targets;
        if (args.length > 1 && args[1].startsWith("@")) {
            try {
                targets = Bukkit.selectEntities(sender, args[1]).stream()
                        .filter(Player.class::isInstance).map(Player.class::cast).toList();
            } catch (IllegalArgumentException e) {
                sender.sendMessage(args[1] + " is not a selector: " + e.getMessage());
                return true;
            }
            at = 2;
        } else if (args.length > 3 && Bukkit.getPlayerExact(args[1]) != null) {
            targets = List.of(Bukkit.getPlayerExact(args[1]));
            at = 2;
        } else if (sender instanceof Player player) {
            targets = List.of(player);
        } else {
            sender.sendMessage("Name a player to show it to.");
            return true;
        }
        if (args.length < at + 2) return false;

        String name = args[at];
        String rest = rest(args, at + 1).strip();
        Map<String, String> callbacks = Map.of();
        if (rest.startsWith("{")) {
            int end = jsonEnd(rest);
            if (end < 0) {
                sender.sendMessage("The callback json is never closed.");
                return true;
            }
            try {
                callbacks = new com.google.gson.Gson().fromJson(rest.substring(0, end),
                        new com.google.gson.reflect.TypeToken<Map<String, String>>(){}.getType());
            } catch (RuntimeException e) {
                sender.sendMessage("The callback json should map callback names to commands: " + e.getMessage());
                return true;
            }
            rest = rest.substring(end).strip();
        }
        if (rest.isEmpty()) return false;

        int shown = 0;
        for (Player target : targets) {
            DOMTreeElement tree = CTMLAPI.buildUI(rest, Map.of("player", target));
            if (tree == null) {
                sender.sendMessage("That markup built no ui.");
                return true;
            }
            new WindowRenderer(target).show(name, tree, executed.context(name, tree, target, callbacks));
            shown++;
        }
        if (!(sender instanceof Player player && targets.equals(List.of(player)))) {
            sender.sendMessage("Showing " + name + " to " + shown + (shown == 1 ? " player." : " players."));
        }
        return true;
    }

    /** Where the json object at the front of the text ends, just past its closing brace, or -1 where it never does. */
    private static int jsonEnd(String text){
        int depth = 0;
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '\\') i++;
                else if (c == '"') quoted = false;
                continue;
            }
            if (c == '"') quoted = true;
            else if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return i + 1;
        }
        return -1;
    }

    /** The arguments from one on, put back together the way they were typed. */
    private static String rest(String[] args, int from){
        return String.join(" ", java.util.Arrays.copyOfRange(args, from, args.length));
    }

    /** The player named at that place in the command, or the one running it where none is named. */
    private static Player target(CommandSender sender, String[] args, int at){
        if (args.length > at) {
            Player named = Bukkit.getPlayerExact(args[at]);
            if (named == null) sender.sendMessage(args[at] + " is not online.");
            return named;
        }
        if (sender instanceof Player player) return player;
        sender.sendMessage("Name a player to show it to.");
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args){
        if (args.length == 1) {
            return matching(List.of("open", "run", "demo", "close", "list", "execute", "variable", "callback"), args[0]);
        }
        boolean store = args[0].equals("variable") || args[0].equals("callback");
        if (store && args.length == 2) return matching(List.of("put", "remove"), args[1]);
        if (store && args.length == 3) {
            List<String> names = new java.util.ArrayList<>();
            executed.names().forEach(names::add);
            return matching(names, args[2]);
        }
        if (args[0].equals("execute") && args.length == 2) {
            List<String> options = new java.util.ArrayList<>(List.of("@a", "@p", "@s", "@r"));
            Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
            return matching(options, args[1]);
        }
        boolean named = args.length == 2 && (args[0].equals("open") || args[0].equals("run"));
        if (named) return matching(List.copyOf(UIRegistry.getAllNames()), args[1]);
        boolean player = (args.length == 3 && (args[0].equals("open") || args[0].equals("run")))
                || (args.length == 2 && (args[0].equals("demo") || args[0].equals("close")));
        if (player) {
            return matching(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[args.length - 1]);
        }
        return List.of();
    }

    private static List<String> matching(List<String> options, String typed){
        return options.stream().filter(option -> option.startsWith(typed)).toList();
    }
}
