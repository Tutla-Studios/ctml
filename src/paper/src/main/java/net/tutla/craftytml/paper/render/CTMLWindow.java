package net.tutla.craftytml.paper.render;

import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.element.input.FormControl;
import net.tutla.craftytml.client.ctml.element.input.Input;
import net.tutla.craftytml.client.ctml.element.input.Select;
import net.tutla.craftytml.client.dom.CTMLCallback;
import net.tutla.craftytml.client.ctml.style.Gradient;
import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.dom.DOMTreeNode;
import net.tutla.craftytml.client.render.CTMLDrag;
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
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * One ctml tree open in front of one player, drawn out of text displays: the server's answer to a
 * {@code CTMLScreen}. The tree is laid out here, against {@link GlyphMetrics}, and every box that paints
 * something becomes a flat display in a plane floating in front of the player's eyes. Looking around moves
 * a cursor over it, and clicks, hovers and the scroll wheel reach the tree the way the mouse does on a
 * client. Created and driven only by {@link CTMLWindows}.
 *
 * <h2>Coordinates</h2>
 * <ul>
 *   <li><b>Layout (x, y)</b>: the gui pixels the tree is laid out in, origin top left, y down.</li>
 *   <li><b>Plane (u, v)</b>: blocks on the window's plane, origin at its centre, +u to the player's right
 *       and +v up. {@code u = x * px - W/2}, {@code v = H/2 - y * px}.</li>
 * </ul>
 *
 * <h2>What is drawn</h2>
 * Background colours, borders as four flat sides, text in its colour, weight and slant, scrollbars, and
 * opacity. A background gradient is drawn as bands of flat colour, finer the further it travels, and a
 * text gradient colours each glyph off the ramp. A box that clips drops the text lines that do not fit
 * wholly inside it. Rounded corners, shadows, images, focus rings, letter-spacing and the insides of form
 * controls are not drawn: a display has no way to.
 *
 * <h2>Forms</h2>
 * A checkbox, a radio button and a slider answer a click straight away: a tick, a dot, or the slider set to
 * where it was aimed. Anything that needs typing into, a text field, a textarea, a select, a number, a date
 * or a colour, opens the window's form as a dialog instead, on Paper; Spigot and Bukkit have no dialogs.
 *
 * <h2>Dragging</h2>
 * A server hears a click but never the release after it, so a drag is two clicks: one on a draggable box
 * picks it up, it follows the cursor from then on, and the next click puts it down.
 */
public final class CTMLWindow {

    /**
     * How big a window is: the gui pixels its tree is laid out in, how many blocks one of those pixels is
     * across, and how far in front of the eyes the window floats.
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

    // ---- what a vanilla text display draws (the client's TextDisplayRenderer) ----------------------
    // One of its pixels is 0.025 blocks at scale 1. A display of a single space draws a background 5 px
    // wide (a 4 px advance and a pixel of padding) and 10 px tall, its bottom on the entity and its middle
    // half a pixel right of it. A line of text i px wide starts 1 - i/2 px right of the entity, its top
    // 9 px above it.
    private static final double PX = 0.025;
    private static final double SPACE_WIDTH = 5 * PX;
    private static final double SPACE_HEIGHT = 10 * PX;
    private static final double SPACE_LEFT = 2 * PX; // how far left of the entity the space's background starts
    private static final int TEXT_TOP = 9;

    /** How far each thing drawn sits in front of the one before it, so later paint covers earlier. */
    private static final double DEPTH_STEP = 0.001;

    /** Below this text opacity the client draws text fully opaque, so fainter text is not drawn at all. */
    private static final int FAINTEST_TEXT = 26;

    private static final int DEFAULT_TEXT_COLOR = 0xFFFFFFFF;
    private static final int DEFAULT_BORDER_COLOR = 0xFFFFFFFF;
    private static final int TOP = 0, RIGHT = 1, BOTTOM = 2, LEFT = 3;
    @SuppressWarnings("unchecked")
    private static final ElementStyle.Key<Integer>[] BORDER_WIDTHS = new ElementStyle.Key[]{
            ElementStyle.BORDER_TOP_WIDTH, ElementStyle.BORDER_RIGHT_WIDTH,
            ElementStyle.BORDER_BOTTOM_WIDTH, ElementStyle.BORDER_LEFT_WIDTH };
    @SuppressWarnings("unchecked")
    private static final ElementStyle.Key<Integer>[] BORDER_COLORS = new ElementStyle.Key[]{
            ElementStyle.BORDER_TOP_COLOR, ElementStyle.BORDER_RIGHT_COLOR,
            ElementStyle.BORDER_BOTTOM_COLOR, ElementStyle.BORDER_LEFT_COLOR };
    @SuppressWarnings("unchecked")
    private static final ElementStyle.Key<ElementStyle.BorderStyle>[] BORDER_STYLES = new ElementStyle.Key[]{
            ElementStyle.BORDER_TOP_STYLE, ElementStyle.BORDER_RIGHT_STYLE,
            ElementStyle.BORDER_BOTTOM_STYLE, ElementStyle.BORDER_LEFT_STYLE };

    /** What a control's own marks are drawn in, the same as on a client. */
    private static final int THUMB = 4;
    private static final int TRACK = 0xFF6B7280;
    private static final int FIELD_BACKGROUND = 0xFF111827;

    private static final int CLICK_DEBOUNCE_TICKS = 4;
    /** How long a press holds active: on what it landed in, since a server never hears the release. */
    private static final int PRESS_TICKS = 3;
    private static final int MOVE_TICKS = 2;
    private static final float SECONDS_PER_TICK = 0.05f;
    private static final float CURSOR_SCALE = 0.35f;
    private static final String CURSOR_GLYPH = "●";
    private static final double LOOK_MARGIN = 15;
    /** How fine a gradient is cut: a band every this many pixels, and no more than so many bands a side. */
    private static final int GRADIENT_BAND = 4;
    private static final int GRADIENT_BANDS = 24;
    private static final int GRADIENT_CELLS = 12;

    private final CTMLWindows windows;
    private final UUID owner;
    private final String name;
    private final DOMTreeElement tree;
    private final DOMContext ctx;
    private final Frame frame;
    private final CTMLStates states = new CTMLStates();

    private final List<TextDisplay> displays = new ArrayList<>();
    private final List<Paint> drawn = new ArrayList<>();
    private final List<Entity> extras = new ArrayList<>(); // the cursor and the hitbox

    private World world;
    private Vector center;
    private Vector right, up, normal;
    private Quaternionf rotation;

    private TextDisplay cursor;
    private boolean cursorShown;
    private double cursorU = Double.NaN, cursorV = Double.NaN;

    private CTMLLayout.Box layout;
    private long revision = -1;
    /** Where the cursor is in layout pixels, or NaN off the window. */
    private double pointerX = Double.NaN, pointerY = Double.NaN;
    private DOMTreeElement hovered;
    private int releaseAt = -1;
    /** The box being carried, between the click that picked it up and the one that puts it down. */
    private CTMLDrag.Grip grip;
    private int lastClickTick = Integer.MIN_VALUE / 2;

    private float savedWalkSpeed, savedFlySpeed;
    private boolean frozen;
    private Location anchor;
    private float openYaw, openPitch;

    /** One display's worth of paint: where it sits on the plane, how it is scaled, and what it shows. */
    private record Paint(double u, double v, float scaleX, float scaleY, String text, int background,
                         byte opacity, boolean shadow) {}

    CTMLWindow(CTMLWindows windows, Player owner, String name, DOMTreeElement tree, DOMContext ctx, Frame frame){
        this.windows = windows;
        this.owner = owner.getUniqueId();
        this.name = name == null ? "" : name;
        this.tree = tree;
        this.ctx = ctx;
        this.frame = frame;
    }

    /** The player this window is in front of. */
    public UUID owner(){ return owner; }

    /** The name it was opened under. */
    public String name(){ return name; }

    /** The context its tree was opened with, whose variables and callbacks can be changed while it is open. */
    public DOMContext context(){ return ctx; }
    World world(){ return world; }
    Location anchor(){ return anchor; }

    // ---- opening -------------------------------------------------------------------------------------

    void open(Player player){
        world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        openYaw = eye.getYaw();
        openPitch = eye.getPitch();

        // the plane faces the eye: right from the yaw alone, so it holds up looking straight up or down,
        // up across right and the look, and the normal back towards the player
        double yaw = Math.toRadians(eye.getYaw());
        right = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw));
        up = right.getCrossProduct(look).normalize();
        normal = look.clone().multiply(-1);
        center = eye.toVector().add(look.clone().multiply(frame.distance()));

        // a display with no yaw or pitch is turned by its transformation alone, whose columns are where
        // its own x, y and z end up; text faces its own +z
        rotation = new Quaternionf().setFromNormalized(new Matrix3f(
                (float) right.getX(), (float) right.getY(), (float) right.getZ(),
                (float) up.getX(), (float) up.getY(), (float) up.getZ(),
                (float) normal.getX(), (float) normal.getY(), (float) normal.getZ()));

        cursor = spawnDisplay(player, 0, d -> {});
        cursor.setText("");
        cursor.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        cursor.setShadowed(false);
        cursor.setTeleportDuration(MOVE_TICKS);
        cursor.setTransformation(new Transformation(new Vector3f(), new Quaternionf(rotation),
                new Vector3f(CURSOR_SCALE, CURSOR_SCALE, CURSOR_SCALE), new Quaternionf()));
        extras.add(cursor);

        // empty handed right clicks at the air send the server nothing, so there is a hitbox to aim at:
        // a box around the window, kept small enough that the player is not standing in it
        float box = (float) clamp(Math.max(frame.widthBlocks(), frame.heightBlocks()) + 0.2,
                0.5, frame.distance() * 2 - 0.8);
        Location at = center.clone().subtract(new Vector(0, box / 2, 0)).toLocation(world);
        Interaction hitbox = world.spawn(at, Interaction.class, i -> {
            prepare(i);
            i.setInteractionWidth(box);
            i.setInteractionHeight(box);
            i.setResponsive(false);
        });
        show(player, hitbox);
        extras.add(hitbox);

        freeze(player);
        relayout();
    }

    // ---- every tick ----------------------------------------------------------------------------------

    /** Says false once the window is broken, its displays removed from under it, and should be closed. */
    boolean tick(Player player, int now){
        if (now % 20 == 0 && !cursor.isValid()) return false;

        aim(player);
        List<CTMLLayout.Box> under = under();
        states.cursor(under);
        if (releaseAt >= 0 && now >= releaseAt && grip == null) {
            releaseAt = -1;
            states.release();
        }
        if (grip != null && !Double.isNaN(pointerX)) {
            CTMLDrag.Grip held = grip;
            held.follow(pointerX, pointerY);
            fire(() -> CTMLEvents.drag(ctx, held.element()));
        }

        CTMLScroll.animate(layout, SECONDS_PER_TICK);
        if (DOMTreeNode.revision() != revision) {
            relayout();
            under = under(); // the boxes moved, so what is under the cursor may have too
            states.cursor(under);
        }

        DOMTreeElement over = CTMLEvents.at(under, ctx);
        if (over != hovered) {
            hovered = over;
            fire(() -> CTMLEvents.hover(ctx, over));
            if (over != null) player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.15f, 2.0f);
        }
        return true;
    }

    /** A click with one of {@link CTMLEvents.MouseButton}'s buttons, at wherever the cursor is now. */
    void click(Player player, int button, int now){
        if (now - lastClickTick < CLICK_DEBOUNCE_TICKS) return;
        lastClickTick = now;
        aim(player); // from exactly where the player is looking now, not where they were last tick
        if (grip != null) { // the click that puts a carried box down is for nothing else
            grip = null;
            states.release();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5f, 0.8f);
            return;
        }
        if (Double.isNaN(pointerX)) return;

        if (button == CTMLEvents.MouseButton.LEFT) {
            states.press(under());
            releaseAt = now + PRESS_TICKS;
        }
        double x = pointerX, y = pointerY;

        // a control under the click takes it, the way a client asks its controls before anything else
        CTMLLayout.Box control = button == CTMLEvents.MouseButton.LEFT ? control(under()) : null;
        if (control != null) {
            operate(player, control, x);
            return;
        }

        boolean[] taken = { false };
        fire(() -> taken[0] = CTMLEvents.click(layout, ctx, button, x, y));
        if (taken[0]) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5f, 1.2f);
            return;
        }
        // what a click is not for, a draggable box under it is picked up by, the order a screen asks in
        if (button == CTMLEvents.MouseButton.LEFT) {
            grip = CTMLDrag.grab(layout, x, y);
            if (grip != null) player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5f, 1.0f);
        }
    }

    /** The scroll wheel, which on a server is the hotbar: a step to the right is a notch down. */
    void scroll(Player player, int notches){
        aim(player);
        if (Double.isNaN(pointerX) || notches == 0) return;
        CTMLScroll.wheel(layout, pointerX, pointerY, 0, -notches);
    }

    /** Swallows clicks for a moment, for the arm swing that follows dropping an item. */
    void suppressClicks(int now){
        lastClickTick = now;
    }

    /** Whether a control is one a form fills in, rather than a button that is pressed or one never shown. */
    static boolean isControl(FormControl control){
        if (!(control instanceof Input input)) return true;
        return switch (input.kind()) {
            case BUTTON, SUBMIT, RESET, HIDDEN -> false;
            default -> true;
        };
    }

    /** The innermost box in a chain that is a control a form fills in, or null. */
    private static CTMLLayout.Box control(List<CTMLLayout.Box> chain){
        for (int i = chain.size() - 1; i >= 0; i--) {
            if (chain.get(i).element().getElement() instanceof FormControl control && isControl(control)) {
                return chain.get(i);
            }
        }
        return null;
    }

    /** A click on a control: the ones a click can work on their own, and the form for the rest. */
    private void operate(Player player, CTMLLayout.Box box, double x){
        FormControl control = (FormControl) box.element().getElement();
        if (!control.isEditable()) return;
        DOMTreeElement element = box.element();

        if (control instanceof Input input) {
            boolean changed;
            switch (input.kind()) {
                case CHECKBOX -> changed = input.setChecked(!input.isChecked());
                case RADIO -> changed = tick(ctx, element, input);
                case RANGE -> changed = input.slideTo((float) ((x - box.x() - THUMB / 2.0) / Math.max(1, box.width() - THUMB)));
                default -> {
                    form(player);
                    return;
                }
            }
            if (changed) {
                fire(() -> ctx.fire(CTMLCallback.CallbackType.INPUT, element, CTMLCallback.NO_KEY));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5f, 1.2f);
            }
            return;
        }
        form(player);
    }

    /** Whether the server has Paper's dialogs, which Spigot and Bukkit do not. Found without loading PaperForms. */
    private static final boolean DIALOGS = present("io.papermc.paper.dialog.Dialog");

    private static boolean present(String name){
        try {
            Class.forName(name, false, CTMLWindow.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void form(Player player){
        if (!DIALOGS) {
            player.sendMessage("Typing into a form needs a Paper server.");
            return;
        }
        PaperForms.open(windows.plugin(), player, ctx);
    }

    /**
     * Ticks a radio button and unticks every other one under the same name in the same tree. Says whether
     * anything changed: a button that is already ticked stays so, the way a browser has it.
     */
    static boolean tick(DOMContext ctx, DOMTreeElement element, Input radio){
        if (!radio.setChecked(true)) return false;
        String group = element.getString("name");
        if (group == null || group.isEmpty() || ctx == null || ctx.getHeadNode() == null) return true;
        ctx.getHeadNode().walk(node -> {
            if (node != element && node instanceof DOMTreeElement other
                    && other.getElement() instanceof Input input && input.kind() == Input.Kind.RADIO
                    && group.equals(other.getString("name"))) {
                input.setChecked(false);
            }
        });
        return true;
    }

    private void fire(Runnable callback){
        try {
            callback.run();
        } catch (RuntimeException e) {
            windows.plugin().getLogger().log(Level.SEVERE, "a ctml callback threw", e);
        }
    }

    private List<CTMLLayout.Box> under(){
        return Double.isNaN(pointerX) ? List.of() : CTMLFocus.path(layout, pointerX, pointerY);
    }

    /**
     * Casts the look onto the window's plane, moves the cursor there and works out where it is in layout
     * pixels. A ray O + tD meets the plane (P - C) . N = 0 at t = ((C - O) . N) / (D . N); N faces the
     * player, so looking at the front of the window makes D . N negative.
     */
    private void aim(Player player){
        Location eye = player.getEyeLocation();
        Vector origin = eye.toVector();
        Vector dir = eye.getDirection();

        double facing = dir.dot(normal);
        double t = facing > -1e-4 ? -1 : center.clone().subtract(origin).dot(normal) / facing;
        if (t <= 0) {
            hideCursor();
            pointerX = pointerY = Double.NaN;
            return;
        }

        Vector rel = origin.clone().add(dir.multiply(t)).subtract(center);
        double u = rel.dot(right), v = rel.dot(up);
        double halfW = frame.widthBlocks() / 2, halfH = frame.heightBlocks() / 2;
        double margin = 0.03; // keeps the dot on the window at its edges
        moveCursor(origin, clamp(u, -halfW + margin, halfW - margin), clamp(v, -halfH + margin, halfH - margin));

        if (Math.abs(u) <= halfW && Math.abs(v) <= halfH) {
            pointerX = (u + halfW) / frame.blocksPerPixel();
            pointerY = (halfH - v) / frame.blocksPerPixel();
        } else {
            pointerX = pointerY = Double.NaN;
        }
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
        Vector point = onPlane(u - s, v - 4.5 * s, 0);
        Vector toEye = eye.clone().subtract(point).normalize();
        double depth = (displays.size() + 2) * DEPTH_STEP / Math.max(0.1, toEye.dot(normal));
        cursor.teleport(point.add(toEye.multiply(depth)).toLocation(world));
    }

    private void hideCursor(){
        if (!cursorShown) return;
        cursor.setText("");
        cursorShown = false;
    }

    // ---- painting ------------------------------------------------------------------------------------

    private void relayout(){
        layout = new CTMLLayout(GlyphMetrics.VANILLA, frame.width(), frame.height())
                .layout(tree, 0, 0, frame.width(), frame.height());
        // laying out clamps the scroll and drag offsets it finds, which moves the revision on itself
        revision = DOMTreeNode.revision();
        repaint();
    }

    /**
     * Walks the boxes in the order they paint and hands each thing drawn its own display, the display at
     * the same place in the list as last time where there is one, so most of a repaint changes nothing.
     */
    private void repaint(){
        Player player = windows.plugin().getServer().getPlayer(owner);
        List<Paint> paints = new ArrayList<>();
        if (layout != null) paint(layout, null, 1.0f, paints);

        for (int i = 0; i < paints.size(); i++) {
            Paint paint = paints.get(i);
            if (i == displays.size()) {
                if (player == null) break;
                // dressed before it is added to the world, so its first frame is already where it goes
                displays.add(spawnDisplay(player, i * DEPTH_STEP, d -> apply(d, paint, null)));
                drawn.add(paint);
                continue;
            }
            Paint was = drawn.get(i);
            if (paint.equals(was)) continue;
            apply(displays.get(i), paint, was);
            drawn.set(i, paint);
        }
        while (displays.size() > paints.size()) {
            TextDisplay extra = displays.removeLast();
            drawn.removeLast();
            if (extra.isValid()) extra.remove();
        }
    }

    private void apply(TextDisplay display, Paint paint, Paint was){
        if (was == null || !was.text().equals(paint.text())) display.setText(paint.text());
        if (was == null || was.background() != paint.background()) {
            display.setBackgroundColor(Color.fromARGB(paint.background()));
        }
        if (was == null || was.opacity() != paint.opacity()) display.setTextOpacity(paint.opacity());
        if (was == null || was.shadow() != paint.shadow()) display.setShadowed(paint.shadow());
        if (was == null || was.u() != paint.u() || was.v() != paint.v()
                || was.scaleX() != paint.scaleX() || was.scaleY() != paint.scaleY()) {
            Vector at = right.clone().multiply(paint.u()).add(up.clone().multiply(paint.v()));
            float depth = paint.text().equals(" ") ? 1f : paint.scaleX();
            // a display is whatever paint comes at its place in the list, so after a scroll most of them are
            // showing something else entirely: sliding them there would send everything flying across the
            // window, so they jump
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(0);
            display.setTransformation(new Transformation(at.toVector3f(), new Quaternionf(rotation),
                    new Vector3f(paint.scaleX(), paint.scaleY(), depth), new Quaternionf()));
        }
    }

    /** One box and everything inside it, in paint order, under the clip it is drawn through. */
    private void paint(CTMLLayout.Box box, int[] clip, float alpha, List<Paint> out){
        ElementStyle style = box.style();
        alpha *= style.get(ElementStyle.OPACITY, 1.0f);
        if (alpha <= 0) return;
        boolean visible = style.get(ElementStyle.VISIBILITY) != ElementStyle.Visibility.HIDDEN;

        if (visible) {
            background(box, clip, alpha, out);
            borders(box, clip, alpha, out);
        }

        int[] inside = inside(box, clip);
        if (visible) {
            text(box, inside, alpha, out);
            control(box, inside, alpha, out);
        }
        for (CTMLLayout.Box child : CTMLLayout.paintOrder(box.children())) paint(child, inside, alpha, out);

        if (visible) { // over everything in the box, and outside its own clip so a thumb in its gutter shows
            bar(CTMLScroll.verticalBar(box), style, clip, alpha, out);
            bar(CTMLScroll.horizontalBar(box), style, clip, alpha, out);
        }
    }

    private void background(CTMLLayout.Box box, int[] clip, float alpha, List<Paint> out){
        ElementStyle style = box.style();
        ElementStyle.BackgroundClip area = style.get(ElementStyle.BACKGROUND_CLIP, ElementStyle.BackgroundClip.BORDER_BOX);
        if (area == ElementStyle.BackgroundClip.TEXT) return;

        int x = box.x(), y = box.y(), width = box.width(), height = box.height();
        if (area == ElementStyle.BackgroundClip.PADDING_BOX) {
            int left = borderWidth(style, LEFT), top = borderWidth(style, TOP);
            x += left;
            y += top;
            width = Math.max(0, width - left - borderWidth(style, RIGHT));
            height = Math.max(0, height - top - borderWidth(style, BOTTOM));
        } else if (area == ElementStyle.BackgroundClip.CONTENT_BOX) {
            x = box.contentX();
            y = box.contentY();
            width = box.contentWidth();
            height = box.contentHeight();
        }

        Integer color = style.get(ElementStyle.BACKGROUND_COLOR);
        if (color != null) rect(x, y, width, height, fade(color, alpha), clip, out);

        Gradient gradient = style.get(ElementStyle.BACKGROUND_GRADIENT);
        if (gradient != null && gradient.isDrawable()) gradient(gradient, x, y, width, height, alpha, clip, out);
    }

    /**
     * A gradient as bands of flat colour, each the colour the ramp has at its middle. A linear one that
     * runs straight across or down is cut along that axis only; anything else is cut both ways, coarser.
     */
    private void gradient(Gradient gradient, int x, int y, int width, int height, float alpha, int[] clip,
                          List<Paint> out){
        if (width <= 0 || height <= 0) return;
        int across, down;
        if (gradient.kind() == Gradient.Kind.LINEAR) {
            double radians = Math.toRadians(gradient.angle());
            boolean alongX = Math.abs(Math.sin(radians)) > 1e-3;
            boolean alongY = Math.abs(Math.cos(radians)) > 1e-3;
            int most = alongX && alongY ? GRADIENT_CELLS : GRADIENT_BANDS;
            across = alongX ? Math.clamp(width / GRADIENT_BAND, 1, most) : 1;
            down = alongY ? Math.clamp(height / GRADIENT_BAND, 1, most) : 1;
        } else {
            across = Math.clamp(width / GRADIENT_BAND, 1, GRADIENT_CELLS);
            down = Math.clamp(height / GRADIENT_BAND, 1, GRADIENT_CELLS);
        }

        for (int row = 0; row < down; row++) {
            int top = height * row / down, bottom = height * (row + 1) / down;
            for (int column = 0; column < across; column++) {
                int left = width * column / across, right = width * (column + 1) / across;
                float position = gradient.positionOf((left + right) / 2.0f, (top + bottom) / 2.0f, width, height);
                rect(x + left, y + top, right - left, bottom - top, fade(gradient.colorAt(position), alpha), clip, out);
            }
        }
    }

    private void borders(CTMLLayout.Box box, int[] clip, float alpha, List<Paint> out){
        ElementStyle style = box.style();
        int[] widths = new int[4];
        int[] colors = new int[4];
        for (int side = 0; side < 4; side++) {
            widths[side] = borderWidth(style, side);
            // a border starts off the colour of the text, which is what css's currentColor is
            colors[side] = fade(style.get(BORDER_COLORS[side],
                    style.get(ElementStyle.COLOR, DEFAULT_BORDER_COLOR)), alpha);
        }
        int x = box.x(), y = box.y(), w = box.width(), h = box.height();
        int top = widths[TOP], bottom = widths[BOTTOM];
        rect(x, y, w, top, colors[TOP], clip, out);
        rect(x, y + h - bottom, w, bottom, colors[BOTTOM], clip, out);
        rect(x, y + top, widths[LEFT], h - top - bottom, colors[LEFT], clip, out);
        rect(x + w - widths[RIGHT], y + top, widths[RIGHT], h - top - bottom, colors[RIGHT], clip, out);
    }

    private void text(CTMLLayout.Box box, int[] clip, float alpha, List<Paint> out){
        if (box.lines().isEmpty()) return;
        ElementStyle style = box.style();
        int color = style.get(ElementStyle.COLOR, DEFAULT_TEXT_COLOR);
        // a placeholder is drawn in its own colour where it has one, and dimmed where it does not, and so
        // are the words of a control that is out of use
        if (box.element().getElement() instanceof FormControl control) {
            Integer hint = control.showsPlaceholder() ? style.get(ElementStyle.PLACEHOLDER_COLOR) : null;
            if (hint != null) color = hint;
            else if (control.isDisabled() || control.showsPlaceholder()) alpha *= 0.5f;
        }
        Gradient ramp = style.get(ElementStyle.TEXT_GRADIENT);
        if (ramp == null && style.get(ElementStyle.BACKGROUND_CLIP) == ElementStyle.BackgroundClip.TEXT) {
            ramp = style.get(ElementStyle.BACKGROUND_GRADIENT);
        }
        if (ramp != null && !ramp.isDrawable()) ramp = null;

        int opacity = Math.round(((ramp != null ? 0xFF : (color >>> 24) & 0xFF)) * alpha);
        if (opacity < FAINTEST_TEXT) return;
        boolean shadow = style.get(ElementStyle.TEXT_SHADOW, true);

        for (CTMLLayout.TextLine line : box.lines()) {
            float scale = line.scale();
            int x = box.contentX() + line.x() - box.port().scrollX();
            int y = box.contentY() + line.y() - box.port().scrollY();
            int height = Math.round(GlyphMetrics.VANILLA.lineHeight() * scale);
            // a display cannot be cut, so a line the clip would cut is left out whole
            if (clip != null && (x < clip[0] || y < clip[1] || x + line.width() > clip[2] + 1 || y + height > clip[3] + 1)) {
                continue;
            }

            double px = frame.blocksPerPixel();
            double u = (x + line.width() / 2.0 - scale) * px - frame.widthBlocks() / 2;
            double v = frame.heightBlocks() / 2 - (y + TEXT_TOP * scale) * px;
            float size = (float) (scale * px / PX);
            String text = ramp != null ? ramped(line, ramp, box) : legacy(color, line.style()) + line.content();
            out.add(new Paint(u, v, size, size, text, 0,
                    opacity >= 255 ? (byte) -1 : (byte) opacity, shadow));
        }
    }

    /** The part of a control drawn over its box: the tick, the dot, the slider, the swatch and the arrow. */
    private void control(CTMLLayout.Box box, int[] clip, float alpha, List<Paint> out){
        if (!(box.element().getElement() instanceof FormControl control)) return;
        float faded = control.isDisabled() ? alpha * 0.5f : alpha;
        int color = fade(box.style().get(ElementStyle.COLOR, DEFAULT_TEXT_COLOR), faded);

        if (control instanceof Select) {
            int middle = box.right() - 8, top = box.y() + box.height() / 2 - 1;
            for (int row = 0; row < 4; row++) rect(middle - (3 - row), top + row, 2 * (3 - row) + 1, 1, color, clip, out);
            return;
        }
        if (!(control instanceof Input input)) return;
        switch (input.kind()) {
            case CHECKBOX -> {
                if (input.isChecked()) {
                    rect(box.contentX() + 1, box.contentY() + 1, box.contentWidth() - 2, box.contentHeight() - 2, color, clip, out);
                }
            }
            case RADIO -> {
                if (input.isChecked()) { // no round shapes on a display, so the dot is a smaller square
                    int size = Math.max(1, Math.min(box.contentWidth(), box.contentHeight()) - 4);
                    rect(box.contentX() + 2, box.contentY() + 2, size, size, color, clip, out);
                }
            }
            case COLOR -> rect(box.contentX(), box.contentY(), box.contentWidth(), box.contentHeight(),
                    fade(input.argb(), faded), clip, out);
            case DATE -> {
                int left = box.right() - 12, top = box.y() + box.height() / 2 - 4;
                rect(left, top, 9, 9, color, clip, out);
                rect(left + 1, top + 3, 7, 5, fade(FIELD_BACKGROUND, faded), clip, out);
            }
            case RANGE -> {
                int middle = box.y() + box.height() / 2;
                rect(box.x(), middle - 1, box.width(), 2, fade(TRACK, faded), clip, out);
                int reach = Math.max(0, box.width() - THUMB);
                rect(box.x() + Math.round(input.share() * reach), box.y(), THUMB, box.height(), color, clip, out);
            }
            default -> {}
        }
    }

    private void bar(CTMLScroll.Bar bar, ElementStyle style, int[] clip, float alpha, List<Paint> out){
        if (bar == null) return;
        rect(bar.x(), bar.y(), bar.width(), bar.height(), fade(CTMLScroll.trackColor(style), alpha), clip, out);
        rect(bar.thumbX(), bar.thumbY(), bar.thumbWidth(), bar.thumbHeight(),
                fade(CTMLScroll.thumbColor(style), alpha), clip, out);
    }

    /** A flat rectangle in layout pixels, cut to the clip, as a single space with its background stretched. */
    private void rect(int x, int y, int width, int height, int argb, int[] clip, List<Paint> out){
        if ((argb >>> 24) == 0) return;
        int right = x + width, bottom = y + height;
        if (clip != null) {
            x = Math.max(x, clip[0]);
            y = Math.max(y, clip[1]);
            right = Math.min(right, clip[2]);
            bottom = Math.min(bottom, clip[3]);
        }
        if (right <= x || bottom <= y) return;

        double px = frame.blocksPerPixel();
        double w = (right - x) * px, h = (bottom - y) * px;
        double left = x * px - frame.widthBlocks() / 2;
        double top = frame.heightBlocks() / 2 - y * px;
        double scaleX = w / SPACE_WIDTH, scaleY = h / SPACE_HEIGHT;
        // the background starts SPACE_LEFT left of the entity and sits on it, at whatever scale
        out.add(new Paint(left + SPACE_LEFT * scaleX, top - h, (float) scaleX, (float) scaleY, " ", argb,
                (byte) -1, false));
    }

    /** The clip everything inside a box is drawn through: its scrollport on the axes it clips. */
    private static int[] inside(CTMLLayout.Box box, int[] clip){
        if (!box.clipsX() && !box.clipsY()) return clip;
        CTMLLayout.Port port = box.port();
        int[] own = {
                box.clipsX() ? port.x() : Integer.MIN_VALUE / 2,
                box.clipsY() ? port.y() : Integer.MIN_VALUE / 2,
                box.clipsX() ? port.right() : Integer.MAX_VALUE / 2,
                box.clipsY() ? port.bottom() : Integer.MAX_VALUE / 2 };
        if (clip == null) return own;
        return new int[]{ Math.max(own[0], clip[0]), Math.max(own[1], clip[1]),
                Math.min(own[2], clip[2]), Math.min(own[3], clip[3]) };
    }

    private static int borderWidth(ElementStyle style, int side){
        return CTMLLayout.borderWidth(style, BORDER_WIDTHS[side], BORDER_STYLES[side]);
    }

    /**
     * A line with a text gradient, every glyph given the colour the ramp has under its middle. The ramp is
     * measured across the whole content box, the way a client draws it, so every line of a block agrees.
     */
    private static String ramped(CTMLLayout.TextLine line, Gradient ramp, CTMLLayout.Box box){
        boolean bold = line.style().get(ElementStyle.BOLD, false);
        int width = Math.max(1, box.contentWidth()), height = Math.max(1, box.contentHeight());
        float middleY = line.y() + GlyphMetrics.VANILLA.lineHeight() * line.scale() / 2.0f;
        StringBuilder out = new StringBuilder();
        float advance = 0;
        String content = line.content();
        for (int i = 0; i < content.length(); ) {
            int codePoint = content.codePointAt(i);
            i += Character.charCount(codePoint);
            float glyph = GlyphMetrics.advance(codePoint, bold) * line.scale();
            float middleX = line.x() + advance + glyph / 2.0f;
            out.append(legacy(ramp.colorAt(ramp.positionOf(middleX, middleY, width, height)), line.style()))
                    .appendCodePoint(codePoint);
            advance += glyph;
        }
        return out.toString();
    }

    /**
     * The colour and the weight a line is drawn in, as legacy formatting codes, which every server can
     * hand a text display: the colour goes first, since a colour code clears the formats before it.
     */
    private static String legacy(int color, ElementStyle style){
        StringBuilder out = new StringBuilder("§x");
        for (char digit : String.format("%06x", color & 0xFFFFFF).toCharArray()) out.append('§').append(digit);
        if (style.get(ElementStyle.BOLD, false)) out.append("§l");
        if (style.get(ElementStyle.ITALIC, false)) out.append("§o");
        if (style.get(ElementStyle.UNDERLINE, false)) out.append("§n");
        if (style.get(ElementStyle.STRIKETHROUGH, false)) out.append("§m");
        return out.toString();
    }

    private static int fade(int color, float alpha){
        if (alpha >= 1.0f) return color;
        int faded = Math.round(((color >>> 24) & 0xFF) * alpha);
        return (color & 0x00FFFFFF) | (faded << 24);
    }

    // ---- the camera ----------------------------------------------------------------------------------

    /**
     * The nearest { yaw, pitch } a player may look at, or null where they are already allowed to: as far
     * as the window's edge and a margin past it, from where they were looking when it opened.
     */
    float[] clampLook(float yaw, float pitch){
        double maxYaw = Math.toDegrees(Math.atan2(frame.widthBlocks() / 2, frame.distance())) + LOOK_MARGIN;
        double maxPitch = Math.toDegrees(Math.atan2(frame.heightBlocks() / 2, frame.distance())) + LOOK_MARGIN;
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
        for (Entity e : displays) if (e.isValid()) e.remove();
        for (Entity e : extras) if (e.isValid()) e.remove();
        displays.clear();
        drawn.clear();
        extras.clear();
        states.clear(); // nothing on the tree should look hovered or pressed the next time it is shown
        if (player != null) unfreeze(player);
    }

    // ---- entities ------------------------------------------------------------------------------------

    /** Every entity a window spawns: shown to its player only, and never saved with the chunk. */
    private void prepare(Entity e){
        e.setVisibleByDefault(false);
        e.setPersistent(false);
        e.getPersistentDataContainer().set(windows.entityKey(), PersistentDataType.BYTE, (byte) 1);
    }

    private void show(Player player, Entity e){
        player.showEntity(windows.plugin(), e);
    }

    /**
     * A display on the window's centre, pushed towards the player by depth. Its transformation alone
     * moves it about the plane, so a repaint never has to teleport it.
     */
    private TextDisplay spawnDisplay(Player player, double depth, java.util.function.Consumer<TextDisplay> dress){
        Location at = onPlane(0, 0, depth).toLocation(world, 0f, 0f);
        TextDisplay display = world.spawn(at, TextDisplay.class, d -> {
            prepare(d);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setShadowRadius(0f);
            d.setShadowStrength(0f);
            d.setViewRange(0.5f);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setAlignment(TextDisplay.TextAlignment.LEFT);
            d.setLineWidth(Integer.MAX_VALUE / 2); // a laid out line is already as wide as it may be
            dress.accept(d);
        });
        show(player, display);
        return display;
    }

    private Vector onPlane(double u, double v, double depth){
        return center.clone()
                .add(right.clone().multiply(u))
                .add(up.clone().multiply(v))
                .add(normal.clone().multiply(depth));
    }

    private static double clamp(double value, double min, double max){
        return Math.max(min, Math.min(max, value));
    }
}
