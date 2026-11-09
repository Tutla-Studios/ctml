package net.tutla.craftytml.paper.render;

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
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One ctml tree open in front of one player: the server's answer to a {@code CTMLScreen}. The tree is drawn
 * on a {@link CTMLSurface} only that player can see, floating in front of their eyes. Looking around moves
 * a cursor over it, and clicks, hovers and the scroll wheel reach the tree the way the mouse does on a
 * client; while it is open the player is held still. Created and driven only by {@link CTMLWindows}.
 *
 * <p>Clicks go through {@link CTMLClicks}, so controls and forms work the same as on a hologram.
 *
 * <h2>Dragging</h2>
 * A server hears a click but never the release after it, so a drag is two clicks: one on a draggable box,
 * or on a scrollbar, picks it up, it follows the cursor from then on, and the next click puts it down.
 */
public final class CTMLWindow {

    /**
     * How big a window is: the gui pixels its tree is laid out in, how many blocks one of those pixels is
     * across, and how far in front of the eyes the window floats. A hologram is sized by the same record
     * and has no use for the distance.
     */
    public record Frame(int width, int height, double blocksPerPixel, double distance) {
        /** A 320 by 180 window, the size of a small screen, 3.2 by 1.8 blocks across at 2.5 blocks away. */
        public static final Frame DEFAULT = new Frame(320, 180, 0.01, 2.5);

        public Frame {
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("a window needs a size");
            if (blocksPerPixel <= 0) throw new IllegalArgumentException("a pixel needs a size");
            if (distance < 0.5) throw new IllegalArgumentException("a window needs to be half a block away");
        }

        double widthBlocks(){ return width * blocksPerPixel; }
        double heightBlocks(){ return height * blocksPerPixel; }
    }

    /**
     * Where a window goes, measured from the player as the window opens: this many blocks to the right
     * of and above the spot straight ahead of their eyes, at the frame's distance. A negative number goes
     * left or down. Wherever it goes, the window is turned to face their eyes.
     */
    public record Position(double right, double up) {
        /** Straight ahead, where a window goes when nothing says otherwise. */
        public static final Position AHEAD = new Position(0, 0);
    }

    private static final double PX = 0.025;
    private static final double DEPTH_STEP = 0.001;
    private static final int CLICK_DEBOUNCE_TICKS = 4;
    /** How long a press holds active: on what it landed in, since a server never hears the release. */
    private static final int PRESS_TICKS = 3;
    private static final int MOVE_TICKS = 2;
    private static final float CURSOR_SCALE = 0.35f;
    private static final String CURSOR_GLYPH = "●";
    private static final double LOOK_MARGIN = 15;

    private final CTMLWindows windows;
    private final UUID owner;
    private final String name;
    private final DOMContext ctx;
    private final Frame frame;
    private final Position position;
    private final CTMLSurface surface;
    /** How far the window's middle is from the eyes it opened in front of. */
    private double reach;
    private final CTMLStates states = new CTMLStates();
    private final List<Entity> extras = new ArrayList<>(); // the cursor and the hitbox

    private TextDisplay cursor;
    private boolean cursorShown;
    private double cursorU = Double.NaN, cursorV = Double.NaN;

    /** Where the cursor is in layout pixels, or NaN off the window. */
    private double pointerX = Double.NaN, pointerY = Double.NaN;
    private DOMTreeElement hovered;
    private int releaseAt = -1;
    /** The box or bar being carried, between the click that picked it up and the one that puts it down. */
    private CTMLHold hold;
    private int lastClickTick = Integer.MIN_VALUE / 2;

    private float savedWalkSpeed, savedFlySpeed;
    private boolean frozen;
    private Location anchor;
    private float openYaw, openPitch;

    CTMLWindow(CTMLWindows windows, Player owner, String name, DOMTreeElement tree, DOMContext ctx, Frame frame,
               Position position){
        this.windows = windows;
        this.owner = owner.getUniqueId();
        this.name = name == null ? "" : name;
        this.ctx = ctx;
        this.frame = frame;
        this.position = position == null ? Position.AHEAD : position;
        this.surface = new CTMLSurface(windows.plugin(), windows.entityKey(), tree, frame, List.of(this.owner));
    }

    /** The player this window is in front of. */
    public UUID owner(){ return owner; }

    /** The name it was opened under. */
    public String name(){ return name; }

    /** The context its tree was opened with, whose variables and callbacks can be changed while it is open. */
    public DOMContext context(){ return ctx; }
    World world(){ return surface.world(); }
    Location anchor(){ return anchor; }

    // ---- opening -------------------------------------------------------------------------------------

    void open(Player player){
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection().normalize();

        // the player's own right and up, from the yaw alone so they hold looking straight up or down,
        // which is what the position is measured along
        double yaw = Math.toRadians(eye.getYaw());
        Vector viewRight = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vector viewUp = viewRight.getCrossProduct(look).normalize();
        Vector center = eye.toVector().add(look.clone().multiply(frame.distance()))
                .add(viewRight.clone().multiply(position.right()))
                .add(viewUp.clone().multiply(position.up()));

        // the plane faces the eye from wherever it ended up
        Vector toWindow = center.clone().subtract(eye.toVector());
        reach = toWindow.length();
        Vector along = toWindow.clone().multiply(1 / reach);
        surface.place(world, center, along, viewRight);

        // the camera is held around the window's middle, not around where the player happened to look
        Location facing = eye.clone();
        if (along.getX() * along.getX() + along.getZ() * along.getZ() > 1e-6) facing.setDirection(along);
        openYaw = facing.getYaw();
        openPitch = facing.getPitch();

        cursor = surface.spawnDisplay(0, d -> {
            d.setText("");
            d.setShadowed(false);
            d.setTeleportDuration(MOVE_TICKS);
            d.setTransformation(new Transformation(new Vector3f(), surface.rotation(),
                    new Vector3f(CURSOR_SCALE, CURSOR_SCALE, CURSOR_SCALE), new org.joml.Quaternionf()));
        });
        cursor.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        extras.add(cursor);

        // empty handed right clicks at the air send the server nothing, so there is a hitbox to aim at:
        // a box around the window, kept small enough that the player is not standing in it
        float box = (float) clamp(Math.max(frame.widthBlocks(), frame.heightBlocks()) + 0.2,
                0.5, reach * 2 - 0.8);
        Location at = center.clone().subtract(new Vector(0, box / 2, 0)).toLocation(world);
        extras.add(surface.spawn(at, Interaction.class, i -> {
            i.setInteractionWidth(box);
            i.setInteractionHeight(box);
            i.setResponsive(false);
        }));

        freeze(player);
        surface.relayout();
    }

    // ---- every tick ----------------------------------------------------------------------------------

    /** Says false once the window is broken, its displays removed from under it, and should be closed. */
    boolean tick(Player player, int now){
        if (now % 20 == 0 && !cursor.isValid()) return false;

        aim(player);
        List<CTMLLayout.Box> under = under();
        states.cursor(under);
        if (releaseAt >= 0 && now >= releaseAt && hold == null) {
            releaseAt = -1;
            states.release();
        }
        if (hold != null && !Double.isNaN(pointerX)) hold.follow(windows.plugin(), ctx, pointerX, pointerY);

        if (surface.refresh()) {
            under = under(); // the boxes moved, so what is under the cursor may have too
            states.cursor(under);
        }

        DOMTreeElement over = CTMLEvents.at(under, ctx);
        if (over != hovered) {
            hovered = over;
            CTMLClicks.fire(windows.plugin(), () -> CTMLEvents.hover(ctx, over));
            if (over != null) player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.15f, 2.0f);
        }
        return true;
    }

    /** A click with one of {@link CTMLEvents.MouseButton}'s buttons, at wherever the cursor is now. */
    void click(Player player, int button, int now){
        if (now - lastClickTick < CLICK_DEBOUNCE_TICKS) return;
        lastClickTick = now;
        aim(player); // from exactly where the player is looking now, not where they were last tick
        if (hold != null) { // the click that puts a carried box or bar down is for nothing else
            hold = null;
            states.release();
            CTMLClicks.putDown(player);
            return;
        }
        if (Double.isNaN(pointerX)) return;

        if (button == CTMLEvents.MouseButton.LEFT) {
            states.press(under());
            releaseAt = now + PRESS_TICKS;
        }
        hold = CTMLClicks.press(windows.plugin(), player, surface.layout(), ctx, states, button, pointerX, pointerY);
    }

    /** The scroll wheel, which on a server is the hotbar: a step to the right is a notch down. */
    void scroll(Player player, int notches){
        aim(player);
        if (Double.isNaN(pointerX) || notches == 0) return;
        CTMLScroll.wheel(surface.layout(), pointerX, pointerY, 0, -notches);
    }

    /** Swallows clicks for a moment, for the arm swing that follows dropping an item. */
    void suppressClicks(int now){
        lastClickTick = now;
    }

    private List<CTMLLayout.Box> under(){
        return Double.isNaN(pointerX) || surface.layout() == null ? List.of() : CTMLFocus.path(surface.layout(), pointerX, pointerY);
    }

    /** Casts the look onto the window, moves the cursor there and works out where it is in layout pixels. */
    private void aim(Player player){
        Location eye = player.getEyeLocation();
        Vector origin = eye.toVector();
        double[] hit = surface.hit(origin, eye.getDirection());
        if (hit == null) {
            hideCursor();
            pointerX = pointerY = Double.NaN;
            return;
        }

        double u = hit[0], v = hit[1];
        double halfW = frame.widthBlocks() / 2, halfH = frame.heightBlocks() / 2;
        double margin = 0.03; // keeps the dot on the window at its edges
        moveCursor(origin, clamp(u, -halfW + margin, halfW - margin), clamp(v, -halfH + margin, halfH - margin));

        double[] point = surface.toLayout(u, v);
        pointerX = point == null ? Double.NaN : point[0];
        pointerY = point == null ? Double.NaN : point[1];
    }

    /**
     * Puts the dot over a point on the plane, just in front of everything painted there. It is brought
     * towards the eye along the line of sight rather than along the plane's normal, so from where the
     * player stands it covers the very point they are looking at, however far off centre that is.
     */
    private void moveCursor(Vector eye, double u, double v){
        if (!cursorShown) {
            cursor.setText(CURSOR_GLYPH);
            cursorShown = true;
        }
        if (!Double.isNaN(cursorU) && Math.abs(u - cursorU) < 0.002 && Math.abs(v - cursorV) < 0.002) return;
        cursorU = u;
        cursorV = v;
        // the dot's middle is 1 px right of the entity and its glyph about 5 px up
        double s = CURSOR_SCALE * PX;
        Vector point = surface.onPlane(u - s, v - 4.5 * s, 0);
        Vector toEye = eye.clone().subtract(point).normalize();
        double depth = (surface.size() + 2) * DEPTH_STEP / Math.max(0.1, toEye.dot(surface.normal()));
        cursor.teleport(point.add(toEye.multiply(depth)).toLocation(surface.world()));
    }

    private void hideCursor(){
        if (!cursorShown) return;
        cursor.setText("");
        cursorShown = false;
    }

    // ---- the camera ----------------------------------------------------------------------------------

    /**
     * The nearest { yaw, pitch } a player may look at, or null where they are already allowed to: as far
     * as the window's edge and a margin past it, from where they were looking when it opened.
     */
    float[] clampLook(float yaw, float pitch){
        double maxYaw = Math.toDegrees(Math.atan2(frame.widthBlocks() / 2, reach)) + LOOK_MARGIN;
        double maxPitch = Math.toDegrees(Math.atan2(frame.heightBlocks() / 2, reach)) + LOOK_MARGIN;
        double dYaw = wrapDegrees(yaw - openYaw);
        double dPitch = pitch - openPitch;
        double cYaw = clamp(dYaw, -maxYaw, maxYaw);
        double cPitch = clamp(dPitch, -maxPitch, maxPitch);
        if (cYaw == dYaw && cPitch == dPitch) return null;
        return new float[]{ (float) wrapDegrees(openYaw + cYaw), (float) clamp(openPitch + cPitch, -90, 90) };
    }

    private static double wrapDegrees(double degrees){
        double d = degrees % 360;
        if (d >= 180) d -= 360;
        if (d < -180) d += 360;
        return d;
    }

    // ---- holding the player still --------------------------------------------------------------------

    private void freeze(Player player){
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        // speeds already written down are the real ones, left by a window a crash never let close
        Float storedWalk = pdc.get(windows.walkSpeedKey(), PersistentDataType.FLOAT);
        Float storedFly = pdc.get(windows.flySpeedKey(), PersistentDataType.FLOAT);
        savedWalkSpeed = storedWalk != null ? storedWalk : player.getWalkSpeed();
        savedFlySpeed = storedFly != null ? storedFly : player.getFlySpeed();
        // walk speed is saved with the player, so the real ones are too, for a crash to be undone on join
        pdc.set(windows.walkSpeedKey(), PersistentDataType.FLOAT, savedWalkSpeed);
        pdc.set(windows.flySpeedKey(), PersistentDataType.FLOAT, savedFlySpeed);

        // a walk speed of exactly 0 also stops the client's fov from zooming
        player.setWalkSpeed(0f);
        player.setFlySpeed(0f);

        AttributeInstance jump = player.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump != null) {
            windows.removeJumpModifier(jump);
            jump.addModifier(new AttributeModifier(windows.jumpModifierKey(), -1.0,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        }

        anchor = player.getLocation();
        frozen = true;
    }

    private void unfreeze(Player player){
        if (!frozen) return;
        frozen = false;
        player.setWalkSpeed(savedWalkSpeed);
        player.setFlySpeed(savedFlySpeed);
        windows.clearFreezeData(player);
    }

    // ---- closing -------------------------------------------------------------------------------------

    /** Removes every display and gives the player back, where they are still online. Safe to call twice. */
    void destroy(Player player){
        surface.remove();
        for (Entity e : extras) if (e.isValid()) e.remove();
        extras.clear();
        states.clear(); // nothing on the tree should look hovered or pressed the next time it is shown
        if (player != null) unfreeze(player);
    }

    private static double clamp(double value, double min, double max){
        return Math.max(min, Math.min(max, value));
    }
}
