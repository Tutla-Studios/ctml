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
import net.tutla.craftytml.paper.render.HologramRenderer;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * {@code /ctml hologram create|open|demo|move|remove|list}: uis standing in the world for everyone. Each
 * action reads its arguments in its own method below, and the place they take is read by
 * {@link Args#place}: three coordinates and a yaw, any of them left out.
 */
public final class HologramCommand extends Subcommand {
    private static final String CREATE = "/ctml hologram create <id> [x y z] [yaw] [json] <ctml>";
    private static final String OPEN = "/ctml hologram open <id> <ui> [x y z] [yaw]";
    private static final String DEMO = "/ctml hologram demo [id] [x y z] [yaw]";
    private static final String MOVE = "/ctml hologram move <id> [x y z] [yaw]";
    private static final String REMOVE = "/ctml hologram remove <id>";
    private static final String LIST = "/ctml hologram list";
    private static final List<String> ACTIONS = List.of("create", "open", "demo", "move", "remove", "list");
    private static final List<String> YAWS = List.of("0", "90", "180", "-90");

    private final Path ui;
    private final ExecutedUIs executed;

    public HologramCommand(Path ui, ExecutedUIs executed){
        super("hologram", "/ctml hologram create|open|demo|move|remove|list <id> [x y z] [yaw] ...",
                "Stands a ui in the world for everyone to see.",
                "create <id> [x y z] [yaw] [json] <ctml>: ctml written into the command, with callbacks like execute's.",
                "open <id> <ui> [x y z] [yaw]: a registered ui.",
                "demo [id] [x y z] [yaw]: the intro and the demo, with all their callbacks, under the id demo unless named.",
                "move <id> [x y z] [yaw], remove <id>, and list.",
                "Coordinates take ~ like any command; without them it goes 3 blocks in front of you.",
                "yaw is the way its front faces; without it, it faces you.",
                "Anyone looking at it within 6 blocks gets a cursor and can click it; hover follows the nearest of them.",
                "variable and callback reach it by its id.",
                "Holograms are gone after a restart.");
        this.ui = ui;
        this.executed = executed;
    }

    @Override
    public boolean run(CommandSender sender, String[] args){
        if (args.length < 2) return usage(sender, "Say what to do with a hologram.");
        return switch (args[1].toLowerCase()) {
            case "list" -> args.length > 2 ? Reply.usage(sender, "list takes nothing more.", LIST) : list(sender);
            case "demo" -> demo(sender, args);
            case "create" -> !withId(sender, args, CREATE) || create(sender, args);
            case "open" -> !withId(sender, args, OPEN) || open(sender, args);
            case "move" -> !withId(sender, args, MOVE) || move(sender, args);
            case "remove" -> !withId(sender, args, REMOVE) || remove(sender, args);
            default -> usage(sender, args[1] + " is not create, open, demo, move, remove or list.");
        };
    }

    /** Whether the action was given an id, saying how it is written where it was not, which ends the command. */
    private static boolean withId(CommandSender sender, String[] args, String form){
        if (args.length >= 3) return true;
        Reply.usage(sender, "Give the hologram an id.", form);
        return false;
    }

    // ---- the actions ----

    private boolean create(CommandSender sender, String[] args){
        int[] next = { 3 };
        Location at = Args.place(sender, args, next, usage());
        if (at == null) return true;
        Args.Markup markup = Args.markup(sender, Args.rest(args, next[0]));
        if (markup == null) return true;
        if (markup.ctml().isEmpty()) return Reply.usage(sender, "There is no ctml to stand there.", CREATE);
        if (!markup.ctml().startsWith("<")) return Reply.fail(sender, "The ctml has to start with a tag, like <ctml>.");
        DOMTreeElement tree = CTMLAPI.buildUI(markup.ctml(), Map.of());
        if (tree == null) return Reply.fail(sender, "That ctml built no ui. The console has its errors.");
        return stand(sender, args[2], at, tree, markup.callbacks());
    }

    private boolean open(CommandSender sender, String[] args){
        if (args.length < 4) return Reply.usage(sender, "Name the registered ui to stand there.", OPEN);
        DOMTreeElement tree = UIRegistry.getUIFromName(args[3]);
        if (tree == null) {
            return Reply.fail(sender, "No ui called " + args[3] + " is registered. /ctml list registered names the ones that are.");
        }
        int[] next = { 4 };
        Location at = Args.place(sender, args, next, usage());
        if (at == null) return true;
        if (next[0] < args.length) return Reply.usage(sender, "open takes only a ui, a place and a yaw.", OPEN);
        return stand(sender, args[2], at, tree, Map.of());
    }

    /**
     * The intro as a hologram, with the callbacks the demo brings, the way /ctml demo shows it in a window.
     * Its button moves the same hologram on to the full demo, since everything goes up under the one id.
     */
    private boolean demo(CommandSender sender, String[] args){
        int[] next = { 2 };
        String id = "demo";
        if (args.length > 2 && !Args.COORDINATE.matcher(args[2]).matches()) {
            id = args[2];
            next[0] = 3;
        }
        Location at = Args.place(sender, args, next, usage());
        if (at == null) return true;
        if (next[0] < args.length) return Reply.usage(sender, "demo takes only an id, a place and a yaw.", DEMO);
        boolean replacing = CraftytmlPaper.holograms().get(id) != null;
        try {
            if (!DemoIntro.open(new HologramRenderer(at, id))) {
                return Reply.fail(sender, DemoIntro.INTRO_UI + " is not loaded. Put it in " + ui + " and restart.");
            }
        } catch (RuntimeException e) {
            return Reply.fail(sender, "Could not put up the demo: " + e.getMessage());
        }
        return Reply.done(sender, (replacing ? "Replaced hologram " : "Put up the demo as hologram ") + id
                + " at " + Reply.describe(at) + ".");
    }

    private static boolean move(CommandSender sender, String[] args){
        String id = args[2];
        CTMLHolograms holograms = CraftytmlPaper.holograms();
        if (holograms.get(id) == null) return Reply.fail(sender, "There is no hologram called " + id + ".");
        int[] next = { 3 };
        Location at = Args.place(sender, args, next, MOVE);
        if (at == null) return true;
        if (next[0] < args.length) return Reply.usage(sender, "move takes only a place and a yaw.", MOVE);
        holograms.move(id, at);
        return Reply.done(sender, "Moved hologram " + id + " to " + Reply.describe(at) + ".");
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
        for (CTMLHologram hologram : standing) Reply.entry(sender, hologram.id(), Reply.describe(hologram.location()));
        return true;
    }

    /** Puts a tree up under an id, with whatever variables and callbacks the id has and the ones given now. */
    private boolean stand(CommandSender sender, String id, Location at, DOMTreeElement tree, Map<String, String> callbacks){
        CTMLHolograms holograms = CraftytmlPaper.holograms();
        boolean replacing = holograms.get(id) != null;
        DOMContext ctx = executed.context(id, tree, () -> {
            CTMLHologram standing = CraftytmlPaper.holograms().get(id);
            return standing == null ? null : standing.presser();
        }, callbacks);
        try {
            holograms.spawn(id, at, tree, ctx);
        } catch (RuntimeException e) {
            CraftytmlPaper.getPlugin(CraftytmlPaper.class).getLogger().log(Level.SEVERE, "could not put up hologram " + id, e);
            return Reply.fail(sender, "Could not put up hologram " + id + ": " + e.getMessage());
        }
        return Reply.done(sender, (replacing ? "Replaced hologram " : "Put up hologram ") + id + " at " + Reply.describe(at) + ".");
    }

    // ---- tab completion ----

    /** The action, an id, then a place, a yaw, and for create the json and ctml. */
    @Override
    public List<String> complete(CommandSender sender, String[] args){
        String typed = args[args.length - 1];
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
