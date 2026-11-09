package net.tutla.craftytml.paper.render;

import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The holograms standing on a server, by id, all driven from one task that runs every tick. A
 * {@link HologramRenderer} puts trees in here, and so can a plugin straight away, with {@link #spawn}.
 *
 * <p>The location is the hologram's middle, and its front faces the way the location looks. Putting a
 * hologram down under an id that is already standing replaces it. None of them survive a restart.
 */
public final class CTMLHolograms {
    /** How far away a hologram can be clicked from, the same as reaching for a block. */
    static final double REACH = 6.0;

    private final Plugin plugin;
    private final NamespacedKey entityKey;
    private final Map<String, CTMLHologram> holograms = new LinkedHashMap<>();
    private BukkitTask task;
    private int ticks;

    public CTMLHolograms(Plugin plugin){
        this.plugin = plugin;
        this.entityKey = new NamespacedKey(plugin, "hologram_entity");
    }

    /** Starts ticking, after clearing out anything a crash or a reload left standing. */
    public void start(){
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e.getPersistentDataContainer().has(entityKey, PersistentDataType.BYTE)) {
                    e.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) plugin.getLogger().info("Removed " + removed + " orphaned ctml hologram entities.");
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /** Takes every hologram down and stops ticking. */
    public void shutdown(){
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (CTMLHologram hologram : List.copyOf(holograms.values())) hologram.remove();
        holograms.clear();
    }

    public CTMLHologram spawn(String id, Location at, DOMTreeElement tree, DOMContext ctx){
        return spawn(id, at, tree, ctx, CTMLWindow.Frame.DEFAULT);
    }

    /** Puts a tree into the world under an id, in place of any hologram already standing under it. */
    public CTMLHologram spawn(String id, Location at, DOMTreeElement tree, DOMContext ctx, CTMLWindow.Frame frame){
        if (at.getWorld() == null) throw new IllegalArgumentException("a hologram needs a world to stand in");
        remove(id);
        CTMLHologram hologram = new CTMLHologram(this, id, tree, ctx, frame);
        try {
            hologram.place(at);
        } catch (RuntimeException e) {
            hologram.remove();
            throw e;
        }
        holograms.put(id, hologram);
        return hologram;
    }

    /** Moves a standing hologram, and says false where there is none under the id. */
    public boolean move(String id, Location to){
        CTMLHologram hologram = holograms.get(id);
        if (hologram == null) return false;
        hologram.place(to);
        return true;
    }

    /** Takes a hologram down, and says false where there was none under the id. */
    public boolean remove(String id){
        CTMLHologram hologram = holograms.remove(id);
        if (hologram == null) return false;
        hologram.remove();
        return true;
    }

    /** The hologram under an id, or null. */
    public CTMLHologram get(String id){
        return holograms.get(id);
    }

    /** Every hologram standing, in the order they were put down. */
    public List<CTMLHologram> all(){
        return List.copyOf(holograms.values());
    }

    /** The nearest hologram a player is looking at from within reach, or null. */
    CTMLHologram lookedAt(Player player){
        CTMLHologram nearest = null;
        double best = REACH;
        for (CTMLHologram hologram : holograms.values()) {
            double[] hit = hologram.lookedAt(player, REACH);
            if (hit != null && hit[2] <= best) {
                best = hit[2];
                nearest = hologram;
            }
        }
        return nearest;
    }

    /** The hologram whose hitbox an entity is, or null. */
    CTMLHologram owning(Entity entity){
        for (CTMLHologram hologram : holograms.values()) if (hologram.owns(entity)) return hologram;
        return null;
    }

    void forget(UUID player){
        for (CTMLHologram hologram : holograms.values()) hologram.forget(player);
    }

    int now(){
        return ticks;
    }

    private void tick(){
        ticks++;
        for (CTMLHologram hologram : List.copyOf(holograms.values())) {
            try {
                hologram.tick(ticks);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "ctml hologram " + hologram.id() + " failed to tick, so it was taken down", e);
                remove(hologram.id());
            }
        }
    }

    Plugin plugin(){ return plugin; }
    NamespacedKey entityKey(){ return entityKey; }
}
