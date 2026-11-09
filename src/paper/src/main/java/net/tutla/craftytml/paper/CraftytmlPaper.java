package net.tutla.craftytml.paper;

import net.tutla.craftytml.client.CTMLAPI;
import net.tutla.craftytml.client.tusan.CTMLTusan;
import net.tutla.craftytml.paper.command.CTMLCommand;
import net.tutla.craftytml.paper.render.CTMLHolograms;
import net.tutla.craftytml.paper.render.CTMLWindowListener;
import net.tutla.craftytml.paper.render.CTMLWindows;
import net.tutla.craftytml.paper.render.WindowRenderer;
import net.tutla.tums.tusan.MinecraftEnvironment;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The Spigot/Paper/Bukkit entry point. It starts CraftyTML the way the Fabric client does, running every
 * script in {@code plugins/CraftyTML/ui} through {@code CTMLAPI} into the {@code UIRegistry}, and adds the
 * renderers a server has of its own: {@link WindowRenderer}, which shows a tree to one player, and
 * {@code HologramRenderer}, which stands one in the world for everyone. The {@code /ctml} command lives in
 * {@link CTMLCommand}.
 */
public final class CraftytmlPaper extends JavaPlugin {
    private static CTMLWindows windows;
    private static CTMLHolograms holograms;

    /** The windows a {@link WindowRenderer} opens trees in. */
    public static CTMLWindows windows(){
        if (windows == null) throw new IllegalStateException("CraftyTML is not enabled");
        return windows;
    }

    /** The holograms standing in the world, which a {@code HologramRenderer} puts trees into. */
    public static CTMLHolograms holograms(){
        if (holograms == null) throw new IllegalStateException("CraftyTML is not enabled");
        return holograms;
    }

    @Override
    public void onEnable(){
        Path ui = getDataFolder().toPath().resolve("ui").toAbsolutePath().normalize();
        try {
            Files.createDirectories(ui);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        CTMLAPI.setEnvironment(MinecraftEnvironment.SERVER);
        CTMLTusan.init();
        CTMLAPI.runAllScripts(ui);

        // Window event handler
        windows = new CTMLWindows(this);
        windows.start();
        holograms = new CTMLHolograms(this);
        holograms.start();
        getServer().getPluginManager().registerEvents(new CTMLWindowListener(windows, holograms), this);

        // ctml command
        PluginCommand command = getCommand("ctml");
        if (command != null) {
            CTMLCommand ctml = new CTMLCommand(ui);
            command.setExecutor(ctml);
            command.setTabCompleter(ctml);
        }
    }

    @Override
    public void onDisable(){
        if (windows != null) windows.shutdown();
        if (holograms != null) holograms.shutdown();
        windows = null;
        holograms = null;
    }
}
