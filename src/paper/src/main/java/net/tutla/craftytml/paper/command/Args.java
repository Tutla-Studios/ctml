package net.tutla.craftytml.paper.command;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.tutla.craftytml.paper.CraftytmlPaper;
import net.tutla.craftytml.paper.command.executor.ExecuteCommand;
import net.tutla.craftytml.paper.render.CTMLHologram;
import net.tutla.craftytml.paper.render.CTMLWindow;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.help.HelpTopic;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Reading {@code /ctml}'s arguments, and the lists tab completion offers for them. */
public final class Args {
    public static final List<String> SELECTORS = List.of("@a", "@p", "@s", "@r");
    /** A coordinate: a number, or ~ with an optional number after it. */
    public static final Pattern COORDINATE = Pattern.compile("~(-?\\d+(\\.\\d+)?)?|-?\\d+(\\.\\d+)?");
    public static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");

    private Args(){}

    /** The arguments from one on, put back together the way they were typed. */
    public static String rest(String[] args, int from){
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    /**
     * The player named at that place in the command, or the one running it where none is named. Says why
     * where there is no one, and hands back null.
     */
    public static Player target(CommandSender sender, String[] args, int at){
        if (args.length > at) {
            Player named = Bukkit.getPlayerExact(args[at]);
            if (named == null) Reply.fail(sender, "No player called " + args[at] + " is online.");
            return named;
        }
        if (sender instanceof Player player) return player;
        Reply.fail(sender, "The console has no window: name the player to show it to.");
        return null;
    }

    // ---- the json in front of markup ----

    /** What comes after the name in execute and hologram create: the callback json, where there is one, and the ctml. */
    public record Markup(Map<String, String> callbacks, String ctml) {}

    /** Splits the callback json off the front of the text. Null where the json is broken, which it says. */
    public static Markup markup(CommandSender sender, String text){
        String rest = text.strip();
        Map<String, String> callbacks = Map.of();
        if (rest.startsWith("{")) {
            int end = jsonEnd(rest);
            if (end < 0) {
                Reply.fail(sender, "The callback json is missing its closing }.");
                return null;
            }
            try {
                callbacks = new Gson().fromJson(rest.substring(0, end), new TypeToken<Map<String, String>>(){}.getType());
            } catch (RuntimeException e) {
                Reply.fail(sender, "The callback json has to map callback names to commands, like "
                        + "{\"buy\":\"give {player} diamond 1\"}: " + e.getMessage());
                return null;
            }
            if (callbacks == null) callbacks = Map.of();
            rest = rest.substring(end).strip();
        }
        return new Markup(callbacks, rest);
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

    // ---- places ----

    /**
     * A place read from the arguments starting at next[0], which is moved past them. Three coordinates, each
     * a number or ~ relative to the sender, then a yaw; either can be left out. With no coordinates it is 3
     * blocks in front of whoever runs the command, and with no yaw it faces them. Null where there is nowhere
     * it could be, which it says, with the usage given.
     */
    public static Location place(CommandSender sender, String[] args, int[] next, String usage){
        Location base = switch (sender) {
            case Player player -> player.getLocation();
            case BlockCommandSender block -> block.getBlock().getLocation().add(0.5, 0, 0.5);
            case Entity entity -> entity.getLocation();
            default -> null;
        };
        int i = next[0];
        Location at;
        if (args.length >= i + 3 && COORDINATE.matcher(args[i]).matches()
                && COORDINATE.matcher(args[i + 1]).matches() && COORDINATE.matcher(args[i + 2]).matches()) {
            boolean relative = args[i].startsWith("~") || args[i + 1].startsWith("~") || args[i + 2].startsWith("~");
            if (relative && base == null) {
                Reply.fail(sender, "The console has no place for ~ to be measured from: give the coordinates in full.");
                return null;
            }
            World world = base != null ? base.getWorld() : Bukkit.getWorlds().getFirst();
            at = new Location(world, coordinate(args[i], base == null ? 0 : base.getX()),
                    coordinate(args[i + 1], base == null ? 0 : base.getY()),
                    coordinate(args[i + 2], base == null ? 0 : base.getZ()));
            i += 3;
        } else if (sender instanceof Player player) {
            Location eye = player.getEyeLocation();
            at = eye.clone().add(eye.getDirection().multiply(3));
        } else if (base != null) {
            at = base.clone().add(0, 1.5, 0);
        } else {
            Reply.usage(sender, "The console has to say where it goes: x y z.", usage);
            return null;
        }

        if (i < args.length && NUMBER.matcher(args[i]).matches()) {
            at.setYaw(Float.parseFloat(args[i]));
            at.setPitch(0);
            i++;
        } else {
            // turned to face whoever put it there, across the ground
            Location from = sender instanceof Player player ? player.getEyeLocation() : base;
            Vector towards = from == null ? null : from.toVector().subtract(at.toVector()).setY(0);
            if (towards != null && towards.lengthSquared() > 1e-4) at.setDirection(towards);
            else at.setYaw(0);
            at.setPitch(0);
        }
        next[0] = i;
        return at;
    }

    private static double coordinate(String written, double base){
        if (!written.startsWith("~")) return Double.parseDouble(written);
        return written.length() == 1 ? base : base + Double.parseDouble(written.substring(1));
    }

    // ---- what tab completion offers ----

    /** The options that start with what has been typed so far. */
    public static List<String> matching(List<String> options, String typed){
        return options.stream().filter(option -> option.startsWith(typed)).toList();
    }

    public static List<String> players(){
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    public static List<String> hologramIds(){
        return CraftytmlPaper.holograms().all().stream().map(CTMLHologram::id).toList();
    }

    /** The ui names an executed ui can have: default, every name something was put on, every open one and every hologram. */
    public static List<String> uiNames(ExecutedUIs executed){
        Set<String> names = new TreeSet<>();
        names.add(ExecuteCommand.DEFAULT_NAME);
        executed.names().forEach(names::add);
        for (CTMLWindow window : CraftytmlPaper.windows().all()) names.add(window.name());
        names.addAll(hologramIds());
        return List.copyOf(names);
    }

    /** Every command the server knows, with its slash, for a callback to run. */
    public static List<String> serverCommands(){
        Set<String> commands = new TreeSet<>();
        for (HelpTopic topic : Bukkit.getHelpMap().getHelpTopics()) {
            if (topic.getName().startsWith("/")) commands.add(topic.getName());
        }
        return List.copyOf(commands);
    }
}
