package net.tutla.craftytml.paper.render;

import net.tutla.craftytml.client.render.CTMLEvents;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * What a player does while a window is open: the clicks and the hotbar turned into the mouse, the rest of
 * what they would do to the world held back, and the window closed by whatever takes them away from it.
 * Only Bukkit events, so it works the same on Spigot.
 */
public final class CTMLWindowListener implements Listener {
    private static final int LEFT = CTMLEvents.MouseButton.LEFT;
    private static final int RIGHT = CTMLEvents.MouseButton.RIGHT;

    private final CTMLWindows windows;

    public CTMLWindowListener(CTMLWindows windows){
        this.windows = windows;
    }

    // ---- the mouse -----------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event){
        CTMLWindow window = windows.window(event.getPlayer());
        if (window == null) return;
        event.setCancelled(true); // nothing is broken, placed or used through a window
        if (event.getHand() == EquipmentSlot.OFF_HAND || event.getAction() == Action.PHYSICAL) return;
        boolean right = event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK;
        window.click(event.getPlayer(), right ? RIGHT : LEFT, windows.now());
    }

    /** A right click on an entity, the window's own hitbox included. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event){
        CTMLWindow window = windows.window(event.getPlayer());
        if (window == null) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        window.click(event.getPlayer(), RIGHT, windows.now());
    }

    /**
     * A left click, wherever it lands: the client swings its arm for every one, at the air as much as at
     * the hitbox. A right click that swings as well has already been counted, and the swing after it falls
     * inside the debounce.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwing(PlayerAnimationEvent event){
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        CTMLWindow window = windows.window(event.getPlayer());
        if (window == null) return;
        window.click(event.getPlayer(), LEFT, windows.now());
    }

    /** A mob that happens to be in the way of a click is not hit by it. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAttack(EntityDamageByEntityEvent event){
        if (event.getDamager() instanceof Player player && windows.isOpen(player)) event.setCancelled(true);
    }

    /** The hotbar is the scroll wheel, and stays on the slot it was on. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onHotbar(PlayerItemHeldEvent event){
        CTMLWindow window = windows.window(event.getPlayer());
        if (window == null) return;
        event.setCancelled(true);
        int notches = event.getNewSlot() - event.getPreviousSlot();
        if (notches > 4) notches -= 9; // round the end of the bar from the first slot to the last
        if (notches < -4) notches += 9;
        window.scroll(event.getPlayer(), notches);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event){
        CTMLWindow window = windows.window(event.getPlayer());
        if (window == null) return;
        event.setCancelled(true);
        window.suppressClicks(windows.now()); // dropping swings the arm too
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event){
        if (windows.isOpen(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event){
        if (windows.isOpen(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event){
        if (event.isSneaking()) windows.close(event.getPlayer());
    }

    // ---- holding still -------------------------------------------------------------------------------

    /**
     * A walk speed of 0 stops walking; this catches the rest, knockback and pistons among it. The player
     * is pinned to where the window opened across the ground but may still fall, and may only turn as
     * far as the window reaches and a margin past it.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event){
        CTMLWindow window = windows.window(event.getPlayer());
        if (window == null || event.getTo() == null) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        Location corrected = null;

        Location anchor = window.anchor();
        if (Math.abs(to.getX() - anchor.getX()) >= 1e-3 || Math.abs(to.getZ() - anchor.getZ()) >= 1e-3) {
            corrected = to.clone();
            corrected.setX(anchor.getX());
            corrected.setZ(anchor.getZ());
        }

        if (to.getYaw() != from.getYaw() || to.getPitch() != from.getPitch()) {
            float[] look = window.clampLook(to.getYaw(), to.getPitch());
            if (look != null) {
                if (corrected == null) corrected = to.clone();
                corrected.setYaw(look[0]);
                corrected.setPitch(look[1]);
            }
        }

        if (corrected != null) event.setTo(corrected);
    }

    // ---- closing -------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event){
        CTMLWindow window = windows.window(event.getPlayer());
        if (window == null || event.getTo() == null) return;
        Location to = event.getTo();
        // holding the player still can show up as a tiny teleport of its own
        if (to.getWorld() == window.world() && to.distanceSquared(window.anchor()) < 1.0) return;
        windows.close(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event){
        windows.close(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event){
        windows.close(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event){
        windows.close(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event){
        windows.restoreLeftovers(event.getPlayer());
    }
}
