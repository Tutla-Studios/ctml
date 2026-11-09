package net.tutla.craftytml.paper;

import net.tutla.craftytml.client.CTMLAPI;
import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.UIRegistry;
import net.tutla.craftytml.client.tusan.CTMLTusan;
import net.tutla.tums.tusan.MinecraftEnvironment;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The Spigot/Paper/Bukkit entry point. It runs every script in {@code plugins/CraftyTML/ui} on enable, the
 * way the Fabric client runs {@code ctml/ui}, and shows the trees they build as {@link CTMLWindows}.
 *
 * <p>{@code /ctml open <name>} opens one of them in front of whoever runs it, {@code /ctml close} puts it
 * away and {@code /ctml list} names them. A window opened from the command has no callbacks: those come
 * from the java that opens a tree with {@link #windows()}.
 */
public final class CraftytmlPaper extends JavaPlugin {
    private static CTMLWindows windows;

    /** The windows, for another plugin to open a tree with. */
    public static CTMLWindows windows(){
        if (windows == null) throw new IllegalStateException("CraftyTML is not enabled");
        return windows;
    }

    @Override
    public void onEnable(){
        Path ui = getDataFolder().toPath().resolve("ui");
        try {
            Files.createDirectories(ui);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        CTMLTusan.init();
        CTMLAPI.runAllScripts(ui, MinecraftEnvironment.SERVER);

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
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        if (args.length == 0) return false;
        switch (args[0]) {
            case "list" -> sender.sendMessage(UIRegistry.isEmpty()
                    ? "No ctml uis loaded."
                    : "ctml uis: " + String.join(", ", UIRegistry.getAllNames()));
            case "open" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can open a window.");
                    return true;
                }
                if (args.length < 2) return false;
                DOMTreeElement tree = UIRegistry.getUIFromName(args[1]);
                if (tree == null) {
                    sender.sendMessage("No ctml ui called " + args[1] + ".");
                    return true;
                }
                windows.open(player, tree, new DOMContext(tree));
            }
            case "close" -> {
                if (sender instanceof Player player && !windows.close(player)) sender.sendMessage("No window open.");
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args){
        if (args.length == 1) return matching(List.of("open", "close", "list"), args[0]);
        if (args.length == 2 && args[0].equals("open")) return matching(List.copyOf(UIRegistry.getAllNames()), args[1]);
        return List.of();
    }

    private static List<String> matching(List<String> options, String typed){
        return options.stream().filter(option -> option.startsWith(typed)).toList();
    }
}
