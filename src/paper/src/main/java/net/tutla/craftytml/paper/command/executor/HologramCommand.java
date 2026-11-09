package net.tutla.craftytml.paper.command.executor;

import net.tutla.craftytml.client.CTMLAPI;
import net.tutla.craftytml.client.demo.DemoIntro;
import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.UIRegistry;
import net.tutla.craftytml.paper.CraftytmlPaper;
import net.tutla.craftytml.paper.command.Args;
import net.tutla.craftytml.paper.command.ExecutedUIs;
import net.tutla.craftytml.paper.command.Reply;
import net.tutla.craftytml.paper.command.Subcommand;
import net.tutla.craftytml.paper.render.CTMLHologram;
import net.tutla.craftytml.paper.render.CTMLHolograms;
import net.tutla.craftytml.paper.render.CTMLSurface;
import net.tutla.craftytml.paper.render.HologramRenderer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * {@code /ctml hologram create|open|demo|move|remove|list}: uis standing in the world for everyone. Each
 * action reads its arguments in its own method below, and the place they take is read by
 * {@link Args#place}: three coordinates and a yaw, any of them left out.
 */
public final class HologramCommand extends Subcommand {
    private static final String CREATE = "/ctml hologram create <id> [x y z] [yaw] [--options] [json] <ctml>";
    private static final String OPEN = "/ctml hologram open <id> <ui> [x y z] [yaw] [--options]";
    private static final String DEMO = "/ctml hologram demo [id] [x y z] [yaw] [--options]";
    private static final String MOVE = "/ctml hologram move <id> [x y z] [yaw] [--pitch=] [--roll=]";
    private static final String REMOVE = "/ctml hologram remove <id>";
    private static final String LIST = "/ctml hologram list";
    private static final List<String> ACTIONS = List.of("create", "open", "demo", "move", "remove", "list");
    private static final List<String> YAWS = List.of("0", "90", "180", "-90");
    private static final List<String> OPTIONS = List.of("--face=fixed", "--face=vertical", "--face=camera",
            "--pitch=", "--roll=", "--per-player", "--shared");
    private static final List<String> TURNS = List.of("--pitch=", "--roll=");

    /**
     * What the --options in a hologram command set, each null where it was not given: which way it faces, its
     * pitch and its roll in degrees, and whether every player gets their own.
     */
    private record Options(CTMLSurface.Facing facing, Double pitch, Double roll, Boolean perPlayer) {
        static final Options NONE = new Options(null, null, null, null);

        CTMLHologram.Settings settings(){
            CTMLHologram.Settings settings = CTMLHologram.Settings.DEFAULT;
            if (facing != null) settings = settings.withFacing(facing);
            if (roll != null) settings = settings.withRoll(roll);
            if (perPlayer != null) settings = settings.withPerPlayer(perPlayer);
            return settings;
        }

        /** The place with the pitch set, where one was given. */
        Location turned(Location at){
            if (pitch != null) at.setPitch(pitch.floatValue());
            return at;
        }
    }

    private final Path ui;
    private final ExecutedUIs executed;

    public HologramCommand(Path ui, ExecutedUIs executed){
        super("hologram", "/ctml hologram create|open|demo|move|remove|list <id> [x y z] [yaw] [--options] ...",
                "Stands a ui in the world for everyone to see.",
                "create <id> [x y z] [yaw] [--options] [json] <ctml>: ctml written into the command, with callbacks like execute's.",
                "open <id> <ui> [x y z] [yaw] [--options]: a registered ui.",
                "demo [id] [x y z] [yaw] [--options]: the intro and the demo, with all their callbacks, under the id demo unless named.",
                "move <id> [x y z] [yaw] [--pitch=] [--roll=], remove <id>, and list.",
                "Coordinates take ~ like any command; without them it goes 3 blocks in front of you.",
                "yaw is the way its front faces; without it, it faces you. --pitch= tilts it and --roll= rolls it, in degrees.",
                "--face=camera turns it to face every viewer, --face=vertical only about the upright, --face=fixed keeps it still.",
                "--per-player gives each player near it a ui of their own that only they see and use; --shared, the default, is one for everyone.",
                "Anyone looking at it within 6 blocks gets a cursor and can click it; hover follows the nearest of them.",
                "variable and callback reach it by its id.",
                "Holograms are gone after a restart.");
        this.ui = ui;
        this.executed = executed;
    }

    @Override
    public boolean run(CommandSender sender, String[] args){
        if (args.length < 2) return usage(sender, "Say what to do with a hologram.");
        Options[] options = { Options.NONE };
        String[] plain = withoutOptions(sender, args, options);
        if (plain == null) return true;
        Options set = options[0];
        return switch (plain[1].toLowerCase()) {
            case "list" -> plain.length > 2 ? Reply.usage(sender, "list takes nothing more.", LIST) : list(sender);
            case "demo" -> demo(sender, plain, set);
            case "create" -> !withId(sender, plain, CREATE) || create(sender, plain, set);
            case "open" -> !withId(sender, plain, OPEN) || open(sender, plain, set);
            case "move" -> !withId(sender, plain, MOVE) || move(sender, plain, set);
            case "remove" -> !withId(sender, plain, REMOVE) || remove(sender, plain);
            default -> usage(sender, plain[1] + " is not create, open, demo, move, remove or list.");
        };
    }

    /**
     * The arguments with the --options taken out of them, read into options[0]. Only the words before the
     * json or the ctml are options: a -- inside the markup is markup. Null where an option cannot be read,
     * which it says.
     */
    private static String[] withoutOptions(CommandSender sender, String[] args, Options[] options){
        List<String> plain = new ArrayList<>();
        CTMLSurface.Facing facing = null;
        Double pitch = null, roll = null;
        Boolean perPlayer = null;
        boolean markup = false;
        for (String arg : args) {
            markup |= arg.startsWith("<") || arg.startsWith("{");
            if (markup || !arg.startsWith("--")) {
                plain.add(arg);
                continue;
            }
            String name = arg.contains("=") ? arg.substring(2, arg.indexOf('=')) : arg.substring(2);
            String value = arg.contains("=") ? arg.substring(arg.indexOf('=') + 1) : "";
            switch (name.toLowerCase()) {
                case "face" -> {
                    try {
                        facing = CTMLSurface.Facing.valueOf(value.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        Reply.fail(sender, "--face is fixed, vertical or camera, not " + value + ".");
                        return null;
                    }
                }
                case "pitch", "roll" -> {
                    if (!Args.NUMBER.matcher(value).matches()) {
                        Reply.fail(sender, "--" + name + " takes a number of degrees, like --" + name + "=15.");
                        return null;
                    }
                    if (name.equalsIgnoreCase("pitch")) pitch = Double.parseDouble(value);
                    else roll = Double.parseDouble(value);
                }
                case "per-player" -> perPlayer = true;
                case "shared" -> perPlayer = false;
                default -> {
                    Reply.fail(sender, arg + " is not an option. They are --face=, --pitch=, --roll=, --per-player and --shared.");
                    return null;
                }
            }
        }
        options[0] = new Options(facing, pitch, roll, perPlayer);
        return plain.toArray(String[]::new);
    }

    /** Whether the action was given an id, saying how it is written where it was not, which ends the command. */
    private static boolean withId(CommandSender sender, String[] args, String form){
        if (args.length >= 3) return true;
        Reply.usage(sender, "Give the hologram an id.", form);
        return false;
    }

    // ---- the actions ----

    private boolean create(CommandSender sender, String[] args, Options options){
        int[] next = { 3 };
        Location at = Args.place(sender, args, next, usage());
        if (at == null) return true;
        Args.Markup markup = Args.markup(sender, Args.rest(args, next[0]));
        if (markup == null) return true;
        if (markup.ctml().isEmpty()) return Reply.usage(sender, "There is no ctml to stand there.", CREATE);
        if (!markup.ctml().startsWith("<")) return Reply.fail(sender, "The ctml has to start with a tag, like <ctml>.");
        DOMTreeElement tree = CTMLAPI.buildUI(markup.ctml(), Map.of());
        if (tree == null) return Reply.fail(sender, "That ctml built no ui. The console has its errors.");
        String id = args[2], ctml = markup.ctml();
        Map<String, String> callbacks = markup.callbacks();
        if (options.settings().perPlayer()) {
            return standEach(sender, id, options.turned(at), options.settings(),
                    player -> CTMLAPI.buildUI(ctml, Map.of("player", player)), callbacks);
        }
        return stand(sender, id, options.turned(at), tree, options.settings(), callbacks);
    }

    private boolean open(CommandSender sender, String[] args, Options options){
        if (args.length < 4) return Reply.usage(sender, "Name the registered ui to stand there.", OPEN);
        DOMTreeElement tree = UIRegistry.getUIFromName(args[3]);
        if (tree == null) {
            return Reply.fail(sender, "No ui called " + args[3] + " is registered. /ctml list registered names the ones that are.");
        }
        int[] next = { 4 };
        Location at = Args.place(sender, args, next, usage());
        if (at == null) return true;
        if (next[0] < args.length) return Reply.usage(sender, "open takes only a ui, a place, a yaw and options.", OPEN);
        if (options.settings().perPlayer()) {
            // a tree a player is read from the file the registered one came from, since one cannot be shared
            Path file = ui.resolve(args[3]).normalize();
            if (!file.startsWith(ui) || !Files.isRegularFile(file)) {
                return Reply.fail(sender, "--per-player builds each player's own from the file, and " + args[3]
                        + " is not a file in " + ui + ".");
            }
            return standEach(sender, args[2], options.turned(at), options.settings(),
                    player -> CTMLAPI.buildUI(file, Map.of("player", player)), Map.of());
        }
        return stand(sender, args[2], options.turned(at), tree, options.settings(), Map.of());
    }

    /**
     * The intro as a hologram, with the callbacks the demo brings, the way /ctml demo shows it in a window.
     * Its button moves the same hologram on to the full demo, since everything goes up under the one id.
     */
    private boolean demo(CommandSender sender, String[] args, Options options){
        if (options.settings().perPlayer()) {
            return Reply.fail(sender, "The demo shows the registered intro, and one tree cannot be every player's own: it can only be --shared.");
        }
        int[] next = { 2 };
        String id = "demo";
        if (args.length > 2 && !Args.COORDINATE.matcher(args[2]).matches()) {
            id = args[2];
            next[0] = 3;
        }
        Location at = Args.place(sender, args, next, usage());
        if (at == null) return true;
        if (next[0] < args.length) return Reply.usage(sender, "demo takes only an id, a place, a yaw and options.", DEMO);
        options.turned(at);
        boolean replacing = CraftytmlPaper.holograms().get(id) != null;
        try {
            if (!DemoIntro.open(new HologramRenderer(at, id, options.settings()))) {
                return Reply.fail(sender, DemoIntro.INTRO_UI + " is not loaded. Put it in " + ui + " and restart.");
            }
        } catch (RuntimeException e) {
            return Reply.fail(sender, "Could not put up the demo: " + e.getMessage());
        }
        return Reply.done(sender, (replacing ? "Replaced hologram " : "Put up the demo as hologram ") + id
                + " at " + Reply.describe(at) + ".");
    }

    private static boolean move(CommandSender sender, String[] args, Options options){
        String id = args[2];
        CTMLHolograms holograms = CraftytmlPaper.holograms();
        if (holograms.get(id) == null) return Reply.fail(sender, "There is no hologram called " + id + ".");
        if (options.facing() != null || options.perPlayer() != null) {
            return Reply.usage(sender, "Which way a hologram faces and whether it is per player are set when it is put up: "
                    + "put it up again under the same id to change them.", MOVE);
        }
        int[] next = { 3 };
        Location at = Args.place(sender, args, next, MOVE);
        if (at == null) return true;
        if (next[0] < args.length) return Reply.usage(sender, "move takes only a place, a yaw, a pitch and a roll.", MOVE);
        options.turned(at);
        if (options.roll() != null) holograms.move(id, at, options.roll());
        else holograms.move(id, at);
        return Reply.done(sender, "Moved hologram " + id + " to " + describe(holograms.get(id)) + ".");
    }

    private static boolean remove(CommandSender sender, String[] args){
        if (args.length > 3) return Reply.usage(sender, "remove takes only the id.", REMOVE);
        if (!CraftytmlPaper.holograms().remove(args[2])) return Reply.fail(sender, "There is no hologram called " + args[2] + ".");
        return Reply.done(sender, "Removed hologram " + args[2] + ".");
    }

    /** Every hologram standing, with where it is: also what /ctml list running shows under the windows. */
    static boolean list(CommandSender sender){
        List<CTMLHologram> standing = CraftytmlPaper.holograms().all();
        if (standing.isEmpty()) return Reply.fail(sender, "No holograms are standing.");
        Reply.heading(sender, "Holograms (" + standing.size() + "):");
        for (CTMLHologram hologram : standing) Reply.entry(sender, hologram.id(), describe(hologram));
        return true;
    }

    /** Where a hologram stands, and how: its pitch and roll where it has them, its facing, and who it is for. */
    private static String describe(CTMLHologram hologram){
        CTMLHologram.Settings settings = hologram.settings();
        Location at = hologram.location();
        StringBuilder out = new StringBuilder(Reply.describe(at));
        if (at.getPitch() != 0) out.append(String.format(", pitch %.0f", at.getPitch()));
        if (settings.roll() != 0) out.append(String.format(", roll %.0f", settings.roll()));
        if (settings.facing() != CTMLSurface.Facing.FIXED) out.append(", turning to face ")
                .append(settings.facing() == CTMLSurface.Facing.CAMERA ? "each viewer" : "each viewer upright");
        out.append(hologram.isPerPlayer() ? ", one for each player" : ", shared");
        return out.toString();
    }

    /** Puts a tree up under an id for everyone, with whatever variables and callbacks the id has and the ones given now. */
    private boolean stand(CommandSender sender, String id, Location at, DOMTreeElement tree,
                          CTMLHologram.Settings settings, Map<String, String> callbacks){
        CTMLHolograms holograms = CraftytmlPaper.holograms();
        boolean replacing = holograms.get(id) != null;
        DOMContext ctx = executed.context(id, tree, () -> {
            CTMLHologram standing = CraftytmlPaper.holograms().get(id);
            return standing == null ? null : standing.presser();
        }, callbacks);
        return put(sender, id, replacing, () -> holograms.spawn(id, at, tree, ctx, settings));
    }

    /**
     * Puts up a hologram that builds every player near it a tree of their own, each with the variables and
     * callbacks the id has and the ones given now, and its callbacks' {player} always that player.
     */
    private boolean standEach(CommandSender sender, String id, Location at, CTMLHologram.Settings settings,
                              Function<Player, DOMTreeElement> build, Map<String, String> callbacks){
        CTMLHolograms holograms = CraftytmlPaper.holograms();
        boolean replacing = holograms.get(id) != null;
        return put(sender, id, replacing, () -> holograms.spawn(id, at, player -> {
            DOMTreeElement tree = build.apply(player);
            if (tree == null) return null;
            UUID own = player.getUniqueId();
            return new CTMLHologram.Content(tree, executed.context(id, tree, () -> Bukkit.getPlayer(own), callbacks));
        }, settings));
    }

    /** Puts one up, and says how it went. */
    private static boolean put(CommandSender sender, String id, boolean replacing, Supplier<CTMLHologram> spawn){
        CTMLHologram hologram;
        try {
            hologram = spawn.get();
        } catch (RuntimeException e) {
            CraftytmlPaper.getPlugin(CraftytmlPaper.class).getLogger().log(Level.SEVERE, "could not put up hologram " + id, e);
            return Reply.fail(sender, "Could not put up hologram " + id + ": " + e.getMessage());
        }
        return Reply.done(sender, (replacing ? "Replaced hologram " : "Put up hologram ") + id + " at " + describe(hologram) + ".");
    }

    // ---- tab completion ----

    /** The action, an id, then a place, a yaw, and for create the json and ctml. */
    @Override
    public List<String> complete(CommandSender sender, String[] args){
        String typed = args[args.length - 1];
        if (args.length > 2 && typed.startsWith("-")) {
            return Args.matching(args[1].equalsIgnoreCase("move") ? TURNS : OPTIONS, typed);
        }
        // the options already written take no part in where the rest of the arguments fall
        List<String> kept = new ArrayList<>();
        for (int i = 0; i < args.length - 1; i++) if (!args[i].startsWith("--")) kept.add(args[i]);
        kept.add(typed);
        args = kept.toArray(String[]::new);
        int at = args.length - 1;
        if (at == 1) return Args.matching(ACTIONS, typed);
        String action = args[1].toLowerCase();
        if (action.equals("demo")) {
            List<String> options = new ArrayList<>(List.of("~"));
            options.addAll(YAWS);
            if (at == 2) options.add("demo");
            return Args.matching(options, typed);
        }
        if (at == 2) return action.equals("list") ? List.of() : Args.matching(Args.hologramIds(), typed);
        if (action.equals("remove") || action.equals("list")) return List.of();

        int from = 3;
        if (action.equals("open")) {
            if (at == 3) return Args.matching(List.copyOf(UIRegistry.getAllNames()), typed);
            from = 4;
        }
        // a place is three coordinates, then a yaw
        int placed = 0;
        while (from + placed < at && placed < 3 && Args.COORDINATE.matcher(args[from + placed]).matches()) placed++;
        List<String> options = new ArrayList<>();
        if (placed < 3 && (placed > 0 || at == from)) options.add("~");
        if (placed == 0 || placed == 3) {
            boolean yawGiven = from + placed < at && Args.NUMBER.matcher(args[from + placed]).matches();
            if (!yawGiven) options.addAll(YAWS);
            if (action.equals("create")) {
                options.add("{\"callback\":\"/command\"}");
                options.add("<ctml");
            }
        }
        return Args.matching(options, typed);
    }
}
