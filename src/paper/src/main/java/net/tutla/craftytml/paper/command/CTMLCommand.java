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
import org.bukkit.ChatColor;
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

    // ---- what every message looks like ----

    private static final String USAGE_OPEN = "/ctml open <name> [player]";
    private static final String USAGE_RUN = "/ctml run <script> [player]";
    private static final String USAGE_DEMO = "/ctml demo [player]";
    private static final String USAGE_CLOSE = "/ctml close [player]";
    private static final String USAGE_LIST = "/ctml list";
    private static final String USAGE_EXECUTE = "/ctml execute [targets] <name> [json] <ctml>";
    private static final String USAGE_VARIABLE = "/ctml variable put|remove <name> <variable> [value]";
    private static final String USAGE_CALLBACK = "/ctml callback put|remove <name> <callback> [command]";

    /** A success, in green. Always true, so a branch can end on it. */
    private static boolean done(CommandSender sender, String message){
        sender.sendMessage(ChatColor.GREEN + message);
        return true;
    }

    /** A failure and why, in red. Always true: the usage bukkit prints for false would say nothing more. */
    private static boolean fail(CommandSender sender, String message){
        sender.sendMessage(ChatColor.RED + message);
        return true;
    }

    /** Input the command could not read, and how it is written. */
    private static boolean usage(CommandSender sender, String problem, String form){
        sender.sendMessage(ChatColor.RED + problem);
        sender.sendMessage(ChatColor.GRAY + "Usage: " + ChatColor.WHITE + form);
        return true;
    }

    private static boolean help(CommandSender sender){
        sender.sendMessage(ChatColor.GOLD + "CraftyTML commands:");
        for (String form : List.of(USAGE_OPEN, USAGE_RUN, USAGE_EXECUTE, USAGE_VARIABLE, USAGE_CALLBACK,
                USAGE_DEMO, USAGE_CLOSE, USAGE_LIST)) {
            sender.sendMessage(ChatColor.GRAY + " " + form);
        }
        return true;
    }

    /** Hands a tree to a player's window, and says whether it opened. */
    private static boolean show(CommandSender sender, Player target, String name, DOMTreeElement tree, DOMContext ctx){
        try {
            new WindowRenderer(target).show(name, tree, ctx);
            return true;
        } catch (RuntimeException e) {
            CraftytmlPaper.getPlugin(CraftytmlPaper.class).getLogger()
                    .log(java.util.logging.Level.SEVERE, "could not open " + name + " for " + target.getName(), e);
            fail(sender, "Could not open " + name + " for " + target.getName() + ": " + e.getMessage());
            return false;
        }
    }

    /** "you" where the window is the sender's own, and the player's name where it is someone else's. */
    private static String whose(CommandSender sender, Player target){
        return sender == target ? "you" : target.getName();
    }

    // ---- the commands ----

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        if (args.length == 0) return help(sender);
        return switch (args[0].toLowerCase()) {
            case "list" -> list(sender);
            case "open" -> open(sender, args);
            case "run" -> run(sender, args);
            case "demo" -> demo(sender, args);
            case "close" -> close(sender, args);
            case "execute" -> execute(sender, args);
            case "variable" -> variable(sender, args);
            case "callback" -> callback(sender, args);
            default -> {
                fail(sender, "There is no /ctml " + args[0] + ".");
                yield help(sender);
            }
        };
    }

    private boolean list(CommandSender sender){
        if (UIRegistry.isEmpty()) return fail(sender, "No ctml uis are loaded. Put .ctml files in " + ui + " and restart.");
        sender.sendMessage(ChatColor.GOLD + "Loaded ctml uis (" + UIRegistry.getAllNames().size() + "): "
                + ChatColor.WHITE + String.join(", ", UIRegistry.getAllNames()));
        return true;
    }

    private boolean open(CommandSender sender, String[] args){
        if (args.length < 2) return usage(sender, "Name the ui to open.", USAGE_OPEN);
        if (args.length > 3) return usage(sender, "Too many arguments.", USAGE_OPEN);
        DOMTreeElement tree = UIRegistry.getUIFromName(args[1]);
        if (tree == null) return fail(sender, "No ui called " + args[1] + " is loaded. /ctml list names the ones that are.");
        Player target = target(sender, args, 2);
        if (target == null) return true;
        if (!show(sender, target, args[1], tree, new DOMContext(tree))) return true;
        return done(sender, "Opened " + args[1] + " for " + whose(sender, target) + ".");
    }

    private boolean run(CommandSender sender, String[] args){
        if (args.length < 2) return usage(sender, "Name the script to run.", USAGE_RUN);
        if (args.length > 3) return usage(sender, "Too many arguments.", USAGE_RUN);
        Path file = ui.resolve(args[1]).normalize();
        if (!file.startsWith(ui)) return fail(sender, "A script has to be inside " + ui + ".");
        if (!Files.isRegularFile(file)) return fail(sender, "There is no script called " + args[1] + " in " + ui + ".");
        Player target = target(sender, args, 2);
        if (target == null) return true;
        DOMTreeElement tree = CTMLAPI.buildUI(file, Map.of("player", target));
        if (tree == null) return fail(sender, args[1] + " ran but built no ui. The console has its errors.");
        if (!show(sender, target, args[1], tree, new DOMContext(tree))) return true;
        return done(sender, "Ran " + args[1] + " for " + whose(sender, target) + ".");
    }

    private boolean demo(CommandSender sender, String[] args){
        if (args.length > 2) return usage(sender, "Too many arguments.", USAGE_DEMO);
        Player target = target(sender, args, 1);
        if (target == null) return true;
        try {
            if (!DemoIntro.open(new WindowRenderer(target))) {
                return fail(sender, DemoIntro.INTRO_UI + " is not loaded. Put it in " + ui + " and restart.");
            }
        } catch (RuntimeException e) {
            return fail(sender, "Could not open the demo for " + target.getName() + ": " + e.getMessage());
        }
        return done(sender, "Opened the demo for " + whose(sender, target) + ".");
    }

    private boolean close(CommandSender sender, String[] args){
        if (args.length > 2) return usage(sender, "Too many arguments.", USAGE_CLOSE);
        Player target = target(sender, args, 1);
        if (target == null) return true;
        if (!CraftytmlPaper.windows().close(target)) {
            return fail(sender, (sender == target ? "You have" : target.getName() + " has") + " no window open.");
        }
        return done(sender, "Closed " + (sender == target ? "your" : target.getName() + "'s") + " window.");
    }

    private boolean variable(CommandSender sender, String[] args){
        if (args.length < 2) return usage(sender, "Say whether to put or remove a variable.", USAGE_VARIABLE);
        String action = args[1].toLowerCase();
        if (!action.equals("put") && !action.equals("remove")) {
            return usage(sender, args[1] + " is not put or remove.", USAGE_VARIABLE);
        }
        if (args.length < 3) return usage(sender, "Name the ui the variable is for.", USAGE_VARIABLE);
        if (args.length < 4) return usage(sender, "Name the variable.", USAGE_VARIABLE);
        String name = args[2], variable = args[3];

        if (action.equals("remove")) {
            if (args.length > 4) return usage(sender, "remove takes no value.", USAGE_VARIABLE);
            if (!executed.removeVariable(name, variable)) return fail(sender, name + " has no variable " + variable + ".");
            return done(sender, "Removed variable " + variable + " from " + name + ".");
        }
        if (args.length < 5) return usage(sender, "Give the variable a value.", USAGE_VARIABLE);
        String value = rest(args, 4);
        int open = executed.putVariable(name, variable, value);
        return done(sender, "Set " + variable + " on " + name + " to \"" + value + "\"" + reached(open) + ".");
    }

    private boolean callback(CommandSender sender, String[] args){
        if (args.length < 2) return usage(sender, "Say whether to put or remove a callback.", USAGE_CALLBACK);
        String action = args[1].toLowerCase();
        if (!action.equals("put") && !action.equals("remove")) {
            return usage(sender, args[1] + " is not put or remove.", USAGE_CALLBACK);
        }
        if (args.length < 3) return usage(sender, "Name the ui the callback is for.", USAGE_CALLBACK);
        if (args.length < 4) return usage(sender, "Name the callback, as the markup's callback property names it.", USAGE_CALLBACK);
        String name = args[2], callback = args[3];

        if (action.equals("remove")) {
            if (args.length > 4) return usage(sender, "remove takes no command.", USAGE_CALLBACK);
            if (!executed.removeCallback(name, callback)) return fail(sender, name + " has no callback " + callback + ".");
            return done(sender, "Removed callback " + callback + " from " + name + ".");
        }
        if (args.length < 5) return usage(sender, "Give the command the callback runs.", USAGE_CALLBACK);
        String run = rest(args, 4);
        int open = executed.putCallback(name, callback, run);
        return done(sender, "Pressing " + callback + " on " + name + " now runs /"
                + (run.startsWith("/") ? run.substring(1) : run) + reached(open) + ".");
    }

    /** ", in 3 open windows", or nothing where none are open to reach. */
    private static String reached(int open){
        return open == 0 ? "" : ", in " + open + (open == 1 ? " open window" : " open windows");
    }

    /**
     * {@code /ctml execute [targets] <name> [json] <ctml>}. Targets are a player's name or a selector such as
     * {@code @a}, and whoever runs the command where there are none. The json, where there is one, maps
     * callback names to the commands they run. Everything after it is the markup, built afresh for each
     * target so every one of them has a tree of their own, never registered, and gone once their window is.
     */
    private boolean execute(CommandSender sender, String[] args){
        if (args.length < 2) return usage(sender, "Give the ui a name and its ctml.", USAGE_EXECUTE);
        int at = 1;
        List<Player> targets;
        if (args[1].startsWith("@")) {
            try {
                targets = Bukkit.selectEntities(sender, args[1]).stream()
                        .filter(Player.class::isInstance).map(Player.class::cast).toList();
            } catch (IllegalArgumentException e) {
                return fail(sender, args[1] + " is not a valid selector: " + e.getMessage());
            }
            if (targets.isEmpty()) return fail(sender, args[1] + " matched no players.");
            at = 2;
        } else if (args.length > 3 && Bukkit.getPlayerExact(args[1]) != null) {
            targets = List.of(Bukkit.getPlayerExact(args[1]));
            at = 2;
        } else if (sender instanceof Player player) {
            targets = List.of(player);
        } else {
            return usage(sender, "The console has no window: name a player or a selector to show it to.", USAGE_EXECUTE);
        }

        if (args.length <= at) return usage(sender, "Give the ui a name.", USAGE_EXECUTE);
        String name = args[at];
        if (name.startsWith("<") || name.startsWith("{")) {
            return usage(sender, "Give the ui a name before its json and ctml.", USAGE_EXECUTE);
        }

        String rest = rest(args, at + 1).strip();
        Map<String, String> callbacks = Map.of();
        if (rest.startsWith("{")) {
            int end = jsonEnd(rest);
            if (end < 0) return fail(sender, "The callback json is missing its closing }.");
            try {
                callbacks = new com.google.gson.Gson().fromJson(rest.substring(0, end),
                        new com.google.gson.reflect.TypeToken<Map<String, String>>(){}.getType());
            } catch (RuntimeException e) {
                return fail(sender, "The callback json has to map callback names to commands, like "
                        + "{\"buy\":\"give {player} diamond 1\"}: " + e.getMessage());
            }
            if (callbacks == null) callbacks = Map.of();
            rest = rest.substring(end).strip();
        }
        if (rest.isEmpty()) return usage(sender, "There is no ctml to show after the name.", USAGE_EXECUTE);
        if (!rest.startsWith("<")) return fail(sender, "The ctml has to start with a tag, like <ctml>.");

        int shown = 0;
        for (Player target : targets) {
            DOMTreeElement tree = CTMLAPI.buildUI(rest, Map.of("player", target));
            if (tree == null) {
                return fail(sender, "That ctml built no ui. The console has its errors."
                        + (shown > 0 ? " It was shown to " + shown + " player(s) before that." : ""));
            }
            if (show(sender, target, name, tree, executed.context(name, tree, target, callbacks))) shown++;
        }
        if (shown == 0) return true; // each failure has already been said
        String callbacksSaid = callbacks.isEmpty() ? "" : " with " + callbacks.size()
                + (callbacks.size() == 1 ? " callback" : " callbacks");
        return done(sender, "Showing " + name + callbacksSaid + " to "
                + (targets.size() == 1 ? whose(sender, targets.getFirst()) : shown + " players") + ".");
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

    /**
     * The player named at that place in the command, or the one running it where none is named. Says why
     * where there is no one, and hands back null.
     */
    private static Player target(CommandSender sender, String[] args, int at){
        if (args.length > at) {
            Player named = Bukkit.getPlayerExact(args[at]);
            if (named == null) fail(sender, "No player called " + args[at] + " is online.");
            return named;
        }
        if (sender instanceof Player player) return player;
        fail(sender, "The console has no window: name the player to show it to.");
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
