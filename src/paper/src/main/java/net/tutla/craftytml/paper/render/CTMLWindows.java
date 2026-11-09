package net.tutla.craftytml.paper.render;

import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The ctml windows open on a server, at most one a player, all driven from one task that runs every tick.
 * A {@link WindowRenderer} opens trees here; nothing else needs to.
 *
 * <p>A tree from the {@code UIRegistry} is one tree, so everyone it is opened for is looking at the same
 * elements: a callback that changes it changes it for all of them, and so does any of their cursors,
 * since hover: and active: are kept on the elements. {@code CTMLAPI.buildUI} builds a tree of its own
 * every time it is called, for a player who should have theirs.
 */
public final class CTMLWindows {
    private final Plugin plugin;
    private final Map<UUID, CTMLWindow> windows = new HashMap<>();
    private final NamespacedKey entityKey;
    private final NamespacedKey walkSpeedKey;
    private final NamespacedKey flySpeedKey;
    private final NamespacedKey jumpModifierKey;
    private BukkitTask task;
    /** Ticks counted here, since only paper can say what tick the server is on. */
    private int ticks;

    public CTMLWindows(Plugin plugin){
        this.plugin = plugin;
        this.entityKey = new NamespacedKey(plugin, "window_entity");
        this.walkSpeedKey = new NamespacedKey(plugin, "saved_walk_speed");
        this.flySpeedKey = new NamespacedKey(plugin, "saved_fly_speed");
        this.jumpModifierKey = new NamespacedKey(plugin, "freeze_jump");
    }

    /** Starts the tick task, and undoes whatever a crash or a reload left behind. */
    public void start(){
        purgeOrphanEntities();
        for (Player p : Bukkit.getOnlinePlayers()) restoreLeftovers(p);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /** Closes every window and stops ticking. */
    public void shutdown(){
        if (task != null) {
            task.cancel();
            task = null;
        }
        closeAll();
    }

    public void open(Player player, DOMTreeElement tree, DOMContext ctx){
        open(player, tree, ctx, CTMLWindow.Frame.DEFAULT);
    }

    /** Opens a tree in front of a player, in place of any window they already had open. */
    public void open(Player player, DOMTreeElement tree, DOMContext ctx, CTMLWindow.Frame frame){
        close(player); // the old one goes first, which also gives the player their speed back
        CTMLWindow window = new CTMLWindow(this, player, tree, ctx, frame);
        try {
            window.open(player);
        } catch (RuntimeException e) {
            window.destroy(player);
            throw e;
        }
        windows.put(player.getUniqueId(), window);
        window.tick(player, ticks); // the cursor goes where they are looking straight away
    }

    /** Closes the player's window, and says false where they had none. */
    public boolean close(Player player){
        CTMLWindow window = windows.remove(player.getUniqueId());
        if (window == null) return false;
        window.destroy(player);
        return true;
    }

    public void closeAll(){
        for (CTMLWindow window : List.copyOf(windows.values())) {
            windows.remove(window.owner());
            window.destroy(Bukkit.getPlayer(window.owner()));
        }
    }

    public boolean isOpen(Player player){
        return windows.containsKey(player.getUniqueId());
    }

    CTMLWindow window(Player player){
        return windows.get(player.getUniqueId());
    }

    int now(){
        return ticks;
    }

    private void tick(){
        ticks++;
        if (windows.isEmpty()) return;
        for (CTMLWindow window : List.copyOf(windows.values())) { // a window can close part way through
            Player player = Bukkit.getPlayer(window.owner());
            if (player == null || !player.isValid() || player.getWorld() != window.world()) {
                windows.remove(window.owner());
                window.destroy(player);
                continue;
            }
            boolean ok;
            try {
                ok = window.tick(player, ticks);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "a ctml window failed to tick, so it was closed", e);
                ok = false;
            }
            if (!ok) close(player);
        }
    }

    /**
     * Gives back the speeds a window that never closed properly left written on a player, and takes its
     * jump modifier off. Done for everyone online on enable and for everyone who joins.
     */
    void restoreLeftovers(Player player){
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        Float walk = pdc.get(walkSpeedKey, PersistentDataType.FLOAT);
        Float fly = pdc.get(flySpeedKey, PersistentDataType.FLOAT);
        if (walk != null) player.setWalkSpeed(walk);
        if (fly != null) player.setFlySpeed(fly);
        clearFreezeData(player);
    }

    void clearFreezeData(Player player){
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.remove(walkSpeedKey);
        pdc.remove(flySpeedKey);
        AttributeInstance jump = player.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump != null) removeJumpModifier(jump);
    }

    /** Takes the freeze's jump modifier off, found by its key, the one way Spigot and Paper both have. */
    void removeJumpModifier(AttributeInstance jump){
        for (AttributeModifier modifier : List.copyOf(jump.getModifiers())) {
            if (jumpModifierKey.equals(modifier.getKey())) jump.removeModifier(modifier);
        }
    }

    /**
     * A window's entities are never saved, so none should outlive a restart. This catches any left in
     * loaded chunks anyway, by a reload that never called onDisable.
     */
    private void purgeOrphanEntities(){
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e.getPersistentDataContainer().has(entityKey, PersistentDataType.BYTE)) {
                    e.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) plugin.getLogger().info("Removed " + removed + " orphaned ctml window entities.");
    }

    Plugin plugin(){ return plugin; }
    NamespacedKey entityKey(){ return entityKey; }
    NamespacedKey walkSpeedKey(){ return walkSpeedKey; }
    NamespacedKey flySpeedKey(){ return flySpeedKey; }
    NamespacedKey jumpModifierKey(){ return jumpModifierKey; }
}
