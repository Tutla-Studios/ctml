package net.tutla.craftytml.paper.render;

import net.tutla.craftytml.client.ctml.element.input.FormControl;
import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.CTMLEvents;
import net.tutla.craftytml.client.render.CTMLFocus;
import net.tutla.craftytml.client.render.CTMLLayout;
import net.tutla.craftytml.client.render.CTMLScroll;
import net.tutla.craftytml.client.render.CTMLStates;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A ctml tree standing in the world where everyone can see it: a {@link CTMLSurface} with no one player
 * behind it. Its middle is at the location it was put at, and its front faces the way that location looks,
 * so a hologram put down facing yaw 0 is read from the south, by someone looking north at it.
 *
 * <p>Nobody is held still. Where anything in the tree can be clicked, everyone looking at it from within
 * reach gets a cursor of their own, seen by them alone, and their clicks work the way they do in a window: a
 * control takes the click, and otherwise the callback under it hears it. hover: and the hover callback follow
 * the nearest player looking at it, and active: lights up on a press, since a hologram has one tree and so
 * one hover for everyone. Turning the hotbar while looking at it scrolls the box under their look, the way
 * it does in a window, and the slot only stays put where something scrolled.
 *
 * <p>Everyone who sees a hologram shares that one tree, so a callback that changes it changes it for all of
 * them. {@link #presser()} is whoever clicked it last, which is how a callback knows who pressed it.
 *
 * <p>Holograms are not saved: a restart or a reload takes them all away.
 */
public final class CTMLHologram {
    private static final int CLICK_DEBOUNCE_TICKS = 4;
    /** How long a press holds active: on what it landed in, since a server never hears the release. */
    private static final int PRESS_TICKS = 3;
    private static final double PX = 0.025;
    private static final double DEPTH_STEP = 0.001;
    private static final float CURSOR_SCALE = 0.35f;
    private static final String CURSOR_GLYPH = "●";
    private static final int MOVE_TICKS = 2;

    private final CTMLHolograms holograms;
    private final String id;
    private final DOMContext ctx;
    private final CTMLSurface surface;
    /** Whether anything in the tree can be clicked, which is when it has a hitbox and cursors. */
    private final boolean interactive;
    private final CTMLStates states = new CTMLStates();

    private Location location;
    private Interaction hitbox;
    private UUID presser;
    private final Map<UUID, Integer> lastClick = new HashMap<>();
    /** The cursor of each player looking at it, seen by that player alone. */
    private final Map<UUID, TextDisplay> cursors = new HashMap<>();
    private DOMTreeElement hovered;
    private int releaseAt = -1;

    CTMLHologram(CTMLHolograms holograms, String id, DOMTreeElement tree, DOMContext ctx, CTMLWindow.Frame frame){
        this.holograms = holograms;
        this.id = id;
        this.ctx = ctx;
        this.surface = new CTMLSurface(holograms.plugin(), holograms.entityKey(), tree, frame, null);
        this.interactive = clickable(tree);
    }

    /** The id it was put down under. */
    public String id(){ return id; }

    /** The context its tree was put down with, whose variables and callbacks can be changed while it stands. */
    public DOMContext context(){ return ctx; }

    /** Where its middle is, and the way its front faces. */
    public Location location(){ return location.clone(); }

    /** Whether anything in its tree can be clicked. */
    public boolean isInteractive(){ return interactive; }

    /** Whoever clicked it last, or null where nobody has or they have left. */
    public Player presser(){
        return presser == null ? null : holograms.plugin().getServer().getPlayer(presser);
    }

    /** Moves it: its middle to the location, its front facing the way the location looks. */
    void place(Location at){
        location = at.clone();
        Vector facing = at.getDirection().normalize();
        Vector along = facing.clone().multiply(-1); // the way someone reading it looks
        double yaw = Math.toRadians(at.getYaw() + 180);
        Vector fallbackRight = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw));
        surface.place(at.getWorld(), at.toVector(), along, fallbackRight);
        removeCursors(); // they were turned to face the old plane

        if (hitbox != null && hitbox.isValid()) hitbox.remove();
        hitbox = null;
        if (interactive) {
            // empty handed right clicks at the air send the server nothing, so there is a box to aim at
            float size = (float) Math.max(surface.frame().widthBlocks(), surface.frame().heightBlocks());
            Location box = at.clone().subtract(0, size / 2, 0);
            box.setYaw(0);
            box.setPitch(0);
            hitbox = surface.spawn(box, Interaction.class, i -> {
                i.setInteractionWidth(size);
                i.setInteractionHeight(size);
                i.setResponsive(false);
            });
        }
        if (surface.layout() == null) surface.relayout();
    }

    /**
     * Keeps it up to date, keeps it standing, and follows who is looking at it. Once a second it checks
     * its chunk: where the chunk was unloaded and came back without its displays, they are put up again, and
     * a chunk nobody is near is left alone until someone comes back to it.
     */
    void tick(int now){
        if (now % 20 == 0) {
            if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return;
            if (!surface.intact() || (interactive && (hitbox == null || !hitbox.isValid()))) place(location);
        }
        surface.refresh();
        if (!interactive) return;

        // every player looking at it gets their cursor moved, and the nearest one is who hover follows
        Player nearest = null;
        double[] nearestPoint = null;
        double best = Double.MAX_VALUE;
        Set<UUID> looking = new HashSet<>();
        double near = CTMLHolograms.REACH + Math.max(surface.frame().widthBlocks(), surface.frame().heightBlocks());
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) > near * near) continue;
            Location eye = player.getEyeLocation();
            double[] hit = surface.hit(eye.toVector(), eye.getDirection());
            if (hit == null || hit[2] > CTMLHolograms.REACH) continue;
            double[] point = surface.toLayout(hit[0], hit[1]);
            if (point == null) continue;
            looking.add(player.getUniqueId());
            moveCursor(player, eye.toVector(), hit[0], hit[1]);
            if (hit[2] < best) {
                best = hit[2];
                nearest = player;
                nearestPoint = point;
            }
        }
        for (UUID gone : Set.copyOf(cursors.keySet())) {
            if (looking.contains(gone)) continue;
            TextDisplay cursor = cursors.remove(gone);
            if (cursor.isValid()) cursor.remove();
        }

        if (releaseAt >= 0 && now >= releaseAt) {
            releaseAt = -1;
            states.release();
        }
        CTMLLayout.Box layout = surface.layout();
        List<CTMLLayout.Box> under = nearestPoint == null || layout == null
                ? List.of() : CTMLFocus.path(layout, nearestPoint[0], nearestPoint[1]);
        states.cursor(under);

        DOMTreeElement over = CTMLEvents.at(under, ctx);
        if (over != hovered) {
            hovered = over;
            CTMLClicks.fire(holograms.plugin(), () -> CTMLEvents.hover(ctx, over));
            if (over != null && nearest != null) {
                nearest.playSound(nearest.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.15f, 2.0f);
            }
        }
    }

    /**
     * Puts a player's dot over the point they are looking at, just in front of everything painted there,
     * brought towards their eye along their line of sight so it covers that very point from where they stand.
     */
    private void moveCursor(Player player, Vector eye, double u, double v){
        TextDisplay cursor = cursors.get(player.getUniqueId());
        if (cursor == null || !cursor.isValid()) {
            Quaternionf rotation = surface.rotation();
            cursor = surface.spawnFor(player, surface.onPlane(u, v, 0).toLocation(surface.world(), 0f, 0f),
                    TextDisplay.class, d -> {
                        d.setBillboard(Display.Billboard.FIXED);
                        d.setBrightness(new Display.Brightness(15, 15));
                        d.setDefaultBackground(false);
                        d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                        d.setShadowed(false);
                        d.setText(CURSOR_GLYPH);
                        d.setTeleportDuration(MOVE_TICKS);
                        d.setTransformation(new Transformation(new Vector3f(), rotation,
                                new Vector3f(CURSOR_SCALE, CURSOR_SCALE, CURSOR_SCALE), new Quaternionf()));
                    });
            cursors.put(player.getUniqueId(), cursor);
        }
        // the dot's middle is 1 px right of the entity and its glyph about 5 px up
        double s = CURSOR_SCALE * PX;
        Vector point = surface.onPlane(u - s, v - 4.5 * s, 0);
        Vector toEye = eye.clone().subtract(point).normalize();
        double depth = (surface.size() + 2) * DEPTH_STEP / Math.max(0.1, toEye.dot(surface.normal()));
        cursor.teleport(point.add(toEye.multiply(depth)).toLocation(surface.world()));
    }

    private void removeCursors(){
        for (TextDisplay cursor : cursors.values()) if (cursor.isValid()) cursor.remove();
        cursors.clear();
    }

    /** Whether an entity is this hologram's own hitbox. */
    boolean owns(org.bukkit.entity.Entity entity){
        return hitbox != null && hitbox.equals(entity);
    }

    /**
     * Where a player's look meets it, as { x, y, distance } with the point in layout pixels, or null where
     * they are not looking at it from within reach.
     */
    double[] lookedAt(Player player, double reach){
        if (player.getWorld() != surface.world()) return null;
        Location eye = player.getEyeLocation();
        double[] hit = surface.hit(eye.toVector(), eye.getDirection());
        if (hit == null || hit[2] > reach) return null;
        double[] point = surface.toLayout(hit[0], hit[1]);
        return point == null ? null : new double[]{ point[0], point[1], hit[2] };
    }

    /** A click from a player, at wherever they are looking on it. */
    void click(Player player, int button, int now){
        Integer last = lastClick.get(player.getUniqueId());
        if (last != null && now - last < CLICK_DEBOUNCE_TICKS) return;
        lastClick.put(player.getUniqueId(), now);

        double[] point = lookedAt(player, CTMLHolograms.REACH);
        if (point == null || surface.layout() == null) return;
        presser = player.getUniqueId();
        if (button == CTMLEvents.MouseButton.LEFT) {
            states.press(CTMLFocus.path(surface.layout(), point[0], point[1]));
            releaseAt = now + PRESS_TICKS;
        }
        CTMLClicks.press(holograms.plugin(), player, surface.layout(), ctx, button, point[0], point[1]);
    }

    /**
     * A turn of the hotbar from a player looking at it, which scrolls the box under where they look the way
     * the wheel does: a step to the right is a notch down. Says whether anything scrolled.
     */
    boolean scroll(Player player, int notches){
        if (notches == 0 || surface.layout() == null) return false;
        double[] point = lookedAt(player, CTMLHolograms.REACH);
        return point != null && CTMLScroll.wheel(surface.layout(), point[0], point[1], 0, -notches);
    }

    /** Forgets a player who has left, so their clicks and their cursor are not kept. */
    void forget(UUID player){
        lastClick.remove(player);
        TextDisplay cursor = cursors.remove(player);
        if (cursor != null && cursor.isValid()) cursor.remove();
    }

    void remove(){
        surface.remove();
        removeCursors();
        if (hitbox != null && hitbox.isValid()) hitbox.remove();
        hitbox = null;
        states.clear(); // nothing on the tree should look hovered or pressed if it is shown again
    }

    /** Whether a tree has anything a click could reach: a callback, or a control a form fills in. */
    private static boolean clickable(DOMTreeElement tree){
        boolean[] found = { false };
        tree.walk(node -> {
            if (node instanceof DOMTreeElement element && (element.getCallback() != null
                    || element.getElement() instanceof FormControl control && CTMLClicks.isControl(control))) {
                found[0] = true;
            }
        });
        return found[0];
    }
}
