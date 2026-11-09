package net.tutla.craftytml.paper;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.tutla.craftytml.client.CTMLAPI;
import net.tutla.craftytml.client.demo.DemoHost;
import net.tutla.craftytml.client.demo.DemoIntro;
import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.tusan.CTMLTusan;
import net.tutla.tums.tusan.MinecraftEnvironment;
import net.tutla.tums.tusan.Variable;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * The Spigot/Paper/Bukkit entry point. The scripts in {@code plugins/CraftyTML/ui} are not run on enable,
 * the way a client runs its ui folder: a server has many players, so a script runs for one of them at a
 * time, with that player in its scope as {@code player}, and the tree it builds is shown to them alone.
 *
 * <ul>
 *   <li>{@code /ctml run <script> [player]} runs a script for a player and shows what it builds.
 *   <li>{@code /ctml demo [player]} shows the intro, and the full demo from it.
 *   <li>{@code /ctml close [player]} puts a window away, and {@code /ctml list} names the scripts.
 * </ul>
 *
 * <p>A script's callbacks are answered by java, the same as on a client. A plugin registers them for a
 * script by name with {@link #callbacks}, and they are put on the context every time that script is
 * opened for a player, from the command or from {@link #open}, with that player to hand. The Paper and
 * Spigot page of the tutorial walks through it.
 */
public final class CraftytmlPaper extends JavaPlugin {
    private static CraftytmlPaper instance;
    private static CTMLWindows windows;

    private static final Map<String, List<BiConsumer<Player, DOMContext>>> CALLBACKS = new HashMap<>();

    private Path ui;

    /** The windows, for another plugin to open a tree with. */
    public static CTMLWindows windows(){
        if (windows == null) throw new IllegalStateException("CraftyTML is not enabled");
        return windows;
    }

    /**
     * Runs a script in the ui folder for one player and hands back the tree it builds, or null where there
     * is no such script or it builds nothing. The script sees the player as {@code player}.
     */
    public static DOMTreeElement run(Player player, String script){
        if (instance == null) throw new IllegalStateException("CraftyTML is not enabled");
        Path file = instance.ui.resolve(script).normalize();
        if (!file.startsWith(instance.ui) || !Files.isRegularFile(file)) return null;
        return CTMLAPI.runFor(file, MinecraftEnvironment.SERVER, Map.of("player", player(player)));
    }

    /**
     * Callbacks for a script, put on the context of every window that script is opened in. Registered
     * more than once, they are all put on, in the order they were registered.
     */
    public static void callbacks(String script, BiConsumer<Player, DOMContext> callbacks){
        CALLBACKS.computeIfAbsent(script, name -> new ArrayList<>()).add(callbacks);
    }

    /**
     * Runs a script for a player and opens what it builds in front of them, with the callbacks registered
     * for it. Says false where there is no such script or it builds nothing.
     */
    public static boolean open(Player player, String script){
        DOMTreeElement tree = run(player, script);
        if (tree == null) return false;
        DOMContext ctx = new DOMContext(tree);
        for (BiConsumer<Player, DOMContext> each : CALLBACKS.getOrDefault(script, List.of())) each.accept(player, ctx);
        windows().open(player, tree, ctx);
        return true;
    }

    /** A player the way a Tums script reads one: the player itself, with a few things about it to hand. */
    private static Variable player(Player player){
        Variable variable = new Variable("player", player, null);
        variable.updateProperty("name", player.getName());
        variable.updateProperty("uuid", player.getUniqueId());
        variable.updateProperty("javaclass", player);
        return variable;
    }

    @Override
    public void onEnable(){
        instance = this;
        ui = getDataFolder().toPath().resolve("ui").toAbsolutePath().normalize();
        try {
            Files.createDirectories(ui);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        CTMLTusan.init();

        windows = new CTMLWindows(this);
        windows.start();
        getServer().getPluginManager().registerEvents(new CTMLWindowListener(windows), this);

        PluginCommand command = getCommand("ctml");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    @Override
    public void onDisable(){
        // removes every window entity and gives frozen players back, a reload included
        if (windows != null) windows.shutdown();
        windows = null;
        instance = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        if (args.length == 0) return false;
        switch (args[0]) {
            case "list" -> {
                List<String> scripts = scripts();
                sender.sendMessage(scripts.isEmpty()
                        ? "No scripts in " + ui + "."
                        : "ctml scripts: " + String.join(", ", scripts));
            }
            case "run" -> {
                if (args.length < 2) return false;
                Player target = target(sender, args, 2);
                if (target == null) return true;
                if (!open(target, args[1])) {
                    sender.sendMessage(args[1] + " is not a script in the ui folder, or it built no ui.");
                }
            }
            case "demo" -> {
                Player target = target(sender, args, 1);
                if (target == null) return true;
                if (!DemoIntro.open(new PlayerDemo(target))) {
                    sender.sendMessage(DemoIntro.INTRO_UI + " is not in " + ui + ".");
                }
            }
            case "close" -> {
                Player target = target(sender, args, 1);
                if (target != null && !windows.close(target)) sender.sendMessage("No window open.");
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
        if (args.length == 1) return matching(List.of("run", "demo", "close", "list"), args[0]);
        if (args.length == 2 && args[0].equals("run")) return matching(scripts(), args[1]);
        boolean named = (args.length == 3 && args[0].equals("run"))
                || (args.length == 2 && (args[0].equals("demo") || args[0].equals("close")));
        if (named) {
            return matching(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[args.length - 1]);
        }
        return List.of();
    }

    private List<String> scripts(){
        try (Stream<Path> files = Files.list(ui)) {
            return files.filter(Files::isRegularFile).map(file -> file.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static List<String> matching(List<String> options, String typed){
        return options.stream().filter(option -> option.startsWith(typed)).toList();
    }

    /** The demos for one player: each tree built for them, shown as their window, a link sent to their chat. */
    private record PlayerDemo(Player player) implements DemoHost {
        @Override
        public DOMTreeElement tree(String name){
            return run(player, name);
        }

        @Override
        public void show(String name, DOMTreeElement tree, DOMContext ctx){
            windows.open(player, tree, ctx);
        }

        @Override
        public void openLink(String url){
            TextComponent link = new TextComponent(url);
            link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
            player.spigot().sendMessage(link);
        }
    }
}
