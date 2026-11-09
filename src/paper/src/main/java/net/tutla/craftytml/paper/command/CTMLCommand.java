package net.tutla.craftytml.paper.command;

import net.tutla.craftytml.client.CTMLAPI;
import net.tutla.craftytml.client.demo.DemoIntro;
import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.UIRegistry;
import net.tutla.craftytml.client.render.UIRenderer;
import net.tutla.craftytml.paper.CraftytmlPaper;
import net.tutla.craftytml.paper.render.CTMLWindow;
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
    private static final String USAGE_LIST = "/ctml list [registered|running]";
    private static final String USAGE_HELP = "/ctml help [command]";
    private static final String USAGE_EXECUTE = "/ctml execute [targets] [name] [json] <ctml>";
    private static final String USAGE_VARIABLE = "/ctml variable list|put|remove [name] [variable] [value]";
    private static final String USAGE_CALLBACK = "/ctml callback list|put|remove [name] [callback] [command]";

    /** The name an executed ui goes under when the command gives it none. */
    static final String DEFAULT_NAME = "default";

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

    /** One command for help to describe: how it is written, a line on what it does, and the detail after. */
    private record Help(String usage, String summary, List<String> detail) {}

    private static final Map<String, Help> HELP = helpPages();

    private static Map<String, Help> helpPages(){
        Map<String, Help> pages = new java.util.LinkedHashMap<>();
        pages.put("open", new Help(USAGE_OPEN, "Shows a registered ui to a player.", List.of(
                "name is a ui in the registry: /ctml list registered names them.",
                "Leave the player out to show it to yourself.")));
        pages.put("run", new Help(USAGE_RUN, "Runs a script from the ui folder again, as a ui of that player's own.", List.of(
                "script is a file in plugins/CraftyTML/ui.",
                "The script sees the player as player, and the tree it builds is not registered.")));
        pages.put("execute", new Help(USAGE_EXECUTE, "Shows ctml written into the command itself.", List.of(
                "targets is a player or a selector like @a, and defaults to you.",
                "name is what variable and callback find it by, and defaults to default.",
                "json maps callbacks to commands run from the console: {\"buy\":\"give {player} diamond 1\"}.",
                "Everything after that is the ctml, built afresh for every target.")));
        pages.put("variable", new Help(USAGE_VARIABLE, "Lists, sets or clears the variables of an executed ui.", List.of(
                "put <name> <variable> <value>: the value is the rest of the line.",
                "remove <name> <variable>, and list [name] for one ui or all of them.",
                "Every window open under the name changes straight away, and every one executed under it later.")));
        pages.put("callback", new Help(USAGE_CALLBACK, "Lists, sets or clears the callback commands of an executed ui.", List.of(
                "put <name> <callback> <command>: {player} in the command is whoever pressed it.",
                "remove <name> <callback>, and list [name] for one ui or all of them.",
                "Only a press runs the command, never a hover.")));
        pages.put("list", new Help(USAGE_LIST, "Lists the registered uis and the windows open now.", List.of(
                "registered: the uis loaded from the ui folder or registered from java.",
                "running: every window open right now, by name, with who has it open.",
                "With neither, both.")));
        pages.put("demo", new Help(USAGE_DEMO, "Shows the intro, and the full demo from its button.", List.of(
                "Needs intro.ctml and demo_screen.ctml in the ui folder.")));
        pages.put("close", new Help(USAGE_CLOSE, "Closes a player's window.", List.of(
                "Leave the player out to close your own. Sneaking closes it too.")));
        pages.put("help", new Help(USAGE_HELP, "Shows these commands, or one of them in detail.", List.of()));
        return pages;
    }

    private static boolean help(CommandSender sender){
        sender.sendMessage(ChatColor.GOLD + "CraftyTML commands " + ChatColor.GRAY + "(/ctml help <command> for more):");
        HELP.forEach((name, page) -> sender.sendMessage(ChatColor.WHITE + " " + page.usage()
                + ChatColor.GRAY + " - " + page.summary()));
        return true;
    }

    private static boolean help(CommandSender sender, String[] args){
        if (args.length == 1) return help(sender);
        if (args.length > 2) return usage(sender, "help takes at most one command.", USAGE_HELP);
        Help page = HELP.get(args[1].toLowerCase());
        if (page == null) {
            fail(sender, "There is no /ctml " + args[1] + ".");
            return help(sender);
        }
        sender.sendMessage(ChatColor.GOLD + page.usage());
        sender.sendMessage(ChatColor.WHITE + page.summary());
        for (String line : page.detail()) sender.sendMessage(ChatColor.GRAY + " " + line);
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
            case "list" -> list(sender, args);
            case "help" -> help(sender, args);
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

    private boolean list(CommandSender sender, String[] args){
        if (args.length > 2) return usage(sender, "list takes at most one of registered or running.", USAGE_LIST);
        String which = args.length == 2 ? args[1].toLowerCase() : "both";
        return switch (which) {
            case "registered" -> registered(sender);
            case "running" -> running(sender);
            case "both" -> {
                registered(sender);
                yield running(sender);
            }
            default -> usage(sender, args[1] + " is not registered or running.", USAGE_LIST);
        };
    }

    /** The uis in the registry, which open and demo show. */
    private boolean registered(CommandSender sender){
        if (UIRegistry.isEmpty()) return fail(sender, "No uis are registered. Put .ctml files in " + ui + " and restart.");
        sender.sendMessage(ChatColor.GOLD + "Registered uis (" + UIRegistry.getAllNames().size() + "): "
                + ChatColor.WHITE + String.join(", ", UIRegistry.getAllNames()));
        return true;
    }

    /** Every window open right now, by the name it was opened under, with who has it open. */
    private static boolean running(CommandSender sender){
        List<CTMLWindow> open = CraftytmlPaper.windows().all();
        if (open.isEmpty()) return fail(sender, "No uis are running: nobody has a window open.");
        Map<String, List<String>> byName = new java.util.TreeMap<>();
        for (CTMLWindow window : open) {
            Player owner = Bukkit.getPlayer(window.owner());
            byName.computeIfAbsent(window.name().isEmpty() ? "(unnamed)" : window.name(), n -> new java.util.ArrayList<>())
                    .add(owner == null ? window.owner().toString() : owner.getName());
        }
        sender.sendMessage(ChatColor.GOLD + "Running uis (" + open.size() + (open.size() == 1 ? " window" : " windows") + "):");
        byName.forEach((name, viewers) -> sender.sendMessage(ChatColor.WHITE + " " + name + ChatColor.GRAY
                + " - " + String.join(", ", viewers)));
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
        if (args.length < 2) return usage(sender, "Say whether to list, put or remove variables.", USAGE_VARIABLE);
        String action = args[1].toLowerCase();
        if (action.equals("list")) {
            if (args.length > 3) return usage(sender, "list takes at most a ui name.", USAGE_VARIABLE);
            return listing(sender, "variable", args.length == 3 ? args[2] : null, executed::variables, " = ");
        }
        if (!action.equals("put") && !action.equals("remove")) {
            return usage(sender, args[1] + " is not list, put or remove.", USAGE_VARIABLE);
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
        if (args.length < 2) return usage(sender, "Say whether to list, put or remove callbacks.", USAGE_CALLBACK);
        String action = args[1].toLowerCase();
        if (action.equals("list")) {
            if (args.length > 3) return usage(sender, "list takes at most a ui name.", USAGE_CALLBACK);
            return listing(sender, "callback", args.length == 3 ? args[2] : null, executed::callbacks, " runs /");
        }
        if (!action.equals("put") && !action.equals("remove")) {
            return usage(sender, args[1] + " is not list, put or remove.", USAGE_CALLBACK);
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

    /**
     * What has been put on one ui name, or on every name where none is given: each entry as its key, the
     * joiner and its value, under a heading saying whose they are.
     */
    private boolean listing(CommandSender sender, String kind, String name,
                            java.util.function.Function<String, Map<String, String>> of, String joiner){
        List<String> names = new java.util.ArrayList<>();
        if (name != null) names.add(name);
        else executed.names().forEach(names::add);

        boolean any = false;
        for (String each : names) {
            Map<String, String> entries = of.apply(each);
            if (entries == null) continue;
            any = true;
            sender.sendMessage(ChatColor.GOLD + each + ChatColor.GRAY + " (" + entries.size() + " "
                    + kind + (entries.size() == 1 ? "" : "s") + "):");
            entries.forEach((key, value) -> sender.sendMessage(ChatColor.GRAY + "  " + ChatColor.WHITE + key
                    + ChatColor.GRAY + joiner + ChatColor.WHITE + (joiner.endsWith("/") && value.startsWith("/") ? value.substring(1) : value)));
        }
        if (!any) {
            return fail(sender, name == null ? "No ui has any " + kind + "s." : name + " has no " + kind + "s.");
        }
        return true;
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
        if (args.length < 2) return usage(sender, "Give the ctml to show.", USAGE_EXECUTE);
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
        } else if (args.length > 2 && Bukkit.getPlayerExact(args[1]) != null) {
            targets = List.of(Bukkit.getPlayerExact(args[1]));
            at = 2;
        } else if (sender instanceof Player player) {
            targets = List.of(player);
        } else {
            return usage(sender, "The console has no window: name a player or a selector to show it to.", USAGE_EXECUTE);
        }

        if (args.length <= at) return usage(sender, "There is no ctml to show.", USAGE_EXECUTE);
        // the name is the word before the json or the ctml, where there is one: both of those start with a bracket
        String name = DEFAULT_NAME;
        if (!args[at].startsWith("<") && !args[at].startsWith("{")) {
            name = args[at];
            at++;
        }

        String rest = rest(args, at).strip();
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
        if (rest.isEmpty()) return usage(sender, "There is no ctml to show.", USAGE_EXECUTE);
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

    // ---- tab completion: every argument of every command offers what can go there ----

    private static final List<String> SUBCOMMANDS =
            List.of("open", "run", "execute", "variable", "callback", "demo", "close", "list", "help");
    private static final List<String> SELECTORS = List.of("@a", "@p", "@s", "@r");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args){
        if (args.length == 1) return matching(SUBCOMMANDS, args[0]);
        String typed = args[args.length - 1];
        int at = args.length - 1; // the argument being typed, counted after the subcommand's own name at 0
        return switch (args[0].toLowerCase()) {
            case "open" -> at == 1 ? matching(List.copyOf(UIRegistry.getAllNames()), typed)
                    : at == 2 ? matching(players(), typed) : List.of();
            case "run" -> at == 1 ? matching(scripts(), typed)
                    : at == 2 ? matching(players(), typed) : List.of();
            case "demo", "close" -> at == 1 ? matching(players(), typed) : List.of();
            case "list" -> at == 1 ? matching(List.of("registered", "running"), typed) : List.of();
            case "help" -> at == 1 ? matching(List.copyOf(HELP.keySet()), typed) : List.of();
            case "execute" -> executeCompletions(args, typed);
            case "variable" -> storeCompletions(args, typed, false);
            case "callback" -> storeCompletions(args, typed, true);
            default -> List.of();
        };
    }

    /**
     * execute's arguments, which can be left out: whatever can come next is offered, targets, then names,
     * then the json and the ctml, each only where the arguments before it leave room for it.
     */
    private List<String> executeCompletions(String[] args, String typed){
        int at = 1;
        List<String> options = new java.util.ArrayList<>();
        // the targets, the first time round
        if (args.length - 1 == at) {
            options.addAll(SELECTORS);
            options.addAll(players());
        }
        if (args.length - 1 > at && (args[at].startsWith("@") || Bukkit.getPlayerExact(args[at]) != null)) at++;
        // a name, where one can still come
        if (args.length - 1 == at) options.addAll(uiNames());
        if (args.length - 1 > at && !args[at].startsWith("<") && !args[at].startsWith("{")) at++;
        // then the json and the ctml, which both start with a bracket
        if (args.length - 1 == at) {
            options.add("{\"callback\":\"/command\"}");
            options.add("<ctml");
        } else if (args.length - 1 > at && args[at].startsWith("{") && !String.join(" ", java.util.Arrays.copyOfRange(args, at, args.length - 1)).contains("}")) {
            return List.of(); // still inside the json
        } else if (args.length - 1 > at) {
            if (!typed.isEmpty() && !typed.contains("<")) options.add(typed.contains("=") ? typed : "class=\"");
            options.addAll(List.of("<div", "<span", "<button", "</ctml>"));
        }
        return matching(options, typed);
    }

    /**
     * variable and callback: list, put or remove, then the ui name, then what that ui already has, and for a
     * callback's command, the server's commands followed by {player} and the players online.
     */
    private List<String> storeCompletions(String[] args, String typed, boolean callbacks){
        int at = args.length - 1;
        if (at == 1) return matching(List.of("list", "put", "remove"), typed);
        String action = args[1].toLowerCase();
        if (at == 2) return matching(uiNames(), typed);
        if (action.equals("list")) return List.of();

        String name = args[2];
        if (at == 3) {
            java.util.Set<String> keys = new java.util.TreeSet<>();
            Map<String, String> had = callbacks ? executed.callbacks(name) : executed.variables(name);
            if (had != null) keys.addAll(had.keySet());
            // a callback can also be one the open windows of this ui name in their markup and have nothing for yet
            if (callbacks && action.equals("put")) keys.addAll(callbackNames(name));
            return matching(List.copyOf(keys), typed);
        }
        if (!action.equals("put")) return List.of();
        if (!callbacks) {
            Map<String, String> had = executed.variables(name);
            String now = had == null ? null : had.get(args[3]);
            return at == 4 && now != null ? matching(List.of(now), typed) : List.of();
        }
        if (at == 4) return matching(serverCommands(), typed);
        List<String> options = new java.util.ArrayList<>(List.of("{player}"));
        options.addAll(SELECTORS);
        options.addAll(players());
        return matching(options, typed);
    }

    private static List<String> players(){
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    /** The scripts in the ui folder, which run reads from. */
    private List<String> scripts(){
        try (java.util.stream.Stream<Path> files = Files.list(ui)) {
            return files.filter(Files::isRegularFile).map(file -> file.getFileName().toString()).sorted().toList();
        } catch (java.io.IOException e) {
            return List.of();
        }
    }

    /** The ui names an executed ui can have: default, every name something was put on, and every open one. */
    private List<String> uiNames(){
        java.util.Set<String> names = new java.util.TreeSet<>();
        names.add(DEFAULT_NAME);
        executed.names().forEach(names::add);
        for (CTMLWindow window : CraftytmlPaper.windows().all()) names.add(window.name());
        return List.copyOf(names);
    }

    /** The callback names written in the markup of the windows open under a ui name. */
    private static java.util.Set<String> callbackNames(String name){
        java.util.Set<String> found = new java.util.TreeSet<>();
        for (CTMLWindow window : CraftytmlPaper.windows().named(name)) {
            DOMTreeElement root = window.context().getHeadNode();
            if (root == null) continue;
            root.walk(node -> {
                if (node instanceof DOMTreeElement element && element.getCallback() != null) found.add(element.getCallback());
            });
        }
        return found;
    }

    /** Every command the server knows, with its slash, for a callback to run. */
    private static List<String> serverCommands(){
        java.util.Set<String> commands = new java.util.TreeSet<>();
        for (org.bukkit.help.HelpTopic topic : Bukkit.getHelpMap().getHelpTopics()) {
            if (topic.getName().startsWith("/")) commands.add(topic.getName());
        }
        return List.copyOf(commands);
    }

    private static List<String> matching(List<String> options, String typed){
        return options.stream().filter(option -> option.startsWith(typed)).toList();
    }
}
