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
 * </ul>
 *
 * A window opened from here has an empty context, so its callbacks fire at nothing; callbacks are put on
 * a context in java, the same as on a client.
 */
public final class CTMLCommand implements TabExecutor {
    private final Path ui;

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

            }
            default -> {
                return false;
            }
        }
        return true;
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
        if (args.length == 1) return matching(List.of("open", "run", "demo", "close", "list"), args[0]);
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
