package net.tutla.craftytml.paper.render;

import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.element.input.FormControl;
import net.tutla.craftytml.client.ctml.element.input.Input;
import net.tutla.craftytml.client.ctml.element.input.Select;
import net.tutla.craftytml.client.ctml.style.Gradient;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.dom.DOMTreeNode;
import net.tutla.craftytml.client.render.CTMLLayout;
import net.tutla.craftytml.client.render.CTMLScroll;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A ctml tree drawn into the world out of text displays: laid out against {@link GlyphMetrics}, and every
 * box that paints something turned into a flat display on one plane. A surface knows where it is, which way
 * it faces and who can see it, and nothing about who is looking at it or what they do: a window in front of
 * one player and a hologram anyone can walk past are both a surface with something else on top.
 *
 * <h2>Coordinates</h2>
 * <ul>
 *   <li><b>Layout (x, y)</b>: the gui pixels the tree is laid out in, origin top left, y down.</li>
 *   <li><b>Plane (u, v)</b>: blocks on the plane, origin at its centre, +u to the right as you face it and
 *       +v up. {@code u = x * px - W/2}, {@code v = H/2 - y * px}.</li>
 * </ul>
 *
 * <h2>What is drawn</h2>
 * Background colours, borders as four flat sides, text in its colour, weight and slant, scrollbars, form
 * controls and opacity. A background gradient is drawn as bands of flat colour, finer the further it
 * travels, and a text gradient colours each glyph off the ramp. A box that clips drops the text lines that
 * do not fit wholly inside it. Rounded corners, shadows, images, focus rings and letter-spacing are not
 * drawn: a display has no way to.
 */
public final class CTMLSurface {
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

    /** How fine a gradient is cut: a band every this many pixels, and no more than so many bands a side. */
    private static final int GRADIENT_BAND = 4;
    private static final int GRADIENT_BANDS = 24;
    private static final int GRADIENT_CELLS = 12;

    private static final float SECONDS_PER_TICK = 0.05f;

    private final Plugin plugin;
    private final NamespacedKey entityKey;
    private final DOMTreeElement tree;
    private final CTMLWindow.Frame frame;
    /** Who can see it, or null for everyone, the ones who join later included. */
    private final Set<UUID> viewers;

    private final List<TextDisplay> displays = new ArrayList<>();
    private final List<Paint> drawn = new ArrayList<>();

    private World world;
    private Vector center;
    private Vector right, up, normal;
    private Quaternionf rotation;

    private CTMLLayout.Box layout;
    private long revision = -1;

    /** One display's worth of paint: where it sits on the plane, how it is scaled, and what it shows. */
    private record Paint(double u, double v, float scaleX, float scaleY, String text, int background,
                         byte opacity, boolean shadow) {}

    /**
     * A surface for a tree, owned by a plugin and marked with its key, so whatever a crash leaves behind can
     * be found again. Seen by the viewers named, or by everyone where they are null.
     */
    public CTMLSurface(Plugin plugin, NamespacedKey entityKey, DOMTreeElement tree, CTMLWindow.Frame frame,
                       Collection<UUID> viewers){
        this.plugin = plugin;
        this.entityKey = entityKey;
        this.tree = tree;
        this.frame = frame;
        this.viewers = viewers == null ? null : Set.copyOf(viewers);
    }

    /**
     * Puts the plane in the world: its middle at center, turned to face someone looking along {@code along}.
     * Right is level and across that line, up is across the two, and the normal points back along it.
     * {@code fallbackRight} is the right to use when along is straight up or down and has no level side.
     * Anything already drawn is drawn again in the new place.
     */
    public void place(World world, Vector center, Vector along, Vector fallbackRight){
        this.world = world;
        this.center = center.clone();
        Vector look = along.clone().normalize();
        boolean level = look.getX() * look.getX() + look.getZ() * look.getZ() > 1e-6;
        right = level ? new Vector(-look.getZ(), 0, look.getX()).normalize() : fallbackRight.clone().normalize();
        up = right.getCrossProduct(look).normalize();
        normal = look.clone().multiply(-1);

        // a display with no yaw or pitch is turned by its transformation alone, whose columns are where
        // its own x, y and z end up; text faces its own +z
        rotation = new Quaternionf().setFromNormalized(new Matrix3f(
                (float) right.getX(), (float) right.getY(), (float) right.getZ(),
                (float) up.getX(), (float) up.getY(), (float) up.getZ(),
                (float) normal.getX(), (float) normal.getY(), (float) normal.getZ()));

        // the displays stand on the old middle, so they go, and the next paint puts new ones down
        removeDisplays();
        if (layout != null) repaint();
    }

    /**
     * Moves any smooth scroll on and lays the tree out again where anything in it changed since. Says
     * whether it did, which is when the boxes may have moved under whoever is pointing at them.
     */
    public boolean refresh(){
        CTMLScroll.animate(layout, SECONDS_PER_TICK);
        if (layout != null && DOMTreeNode.revision() == revision) return false;
        relayout();
        return true;
    }

    /** The boxes as they were last laid out, or null before the first time. */
    public CTMLLayout.Box layout(){ return layout; }
    public DOMTreeElement tree(){ return tree; }
    public World world(){ return world; }
    public Vector center(){ return center.clone(); }
    public Vector normal(){ return normal.clone(); }
    public Quaternionf rotation(){ return new Quaternionf(rotation); }
    public CTMLWindow.Frame frame(){ return frame; }

    /** How many displays it is drawn with, which is how far in front of the plane its paint reaches. */
    public int size(){ return displays.size(); }

    /**
     * Where a ray from origin along dir meets the plane, as { u, v, t }: the point on the plane in blocks and
     * how far along the ray it is. Null where the ray runs away from the front of the plane or never meets
     * it. A ray O + tD meets the plane (P - C) . N = 0 at t = ((C - O) . N) / (D . N); N faces out of the
     * front, so a ray looking at the front makes D . N negative.
     */
    public double[] hit(Vector origin, Vector dir){
        double facing = dir.dot(normal);
        if (facing > -1e-4) return null;
        double t = center.clone().subtract(origin).dot(normal) / facing;
        if (t <= 0) return null;
        Vector rel = origin.clone().add(dir.clone().multiply(t)).subtract(center);
        return new double[]{ rel.dot(right), rel.dot(up), t };
    }

    /** A point on the plane in layout pixels, { x, y }, or null where it is off the edge. */
    public double[] toLayout(double u, double v){
        double halfW = frame.widthBlocks() / 2, halfH = frame.heightBlocks() / 2;
        if (Math.abs(u) > halfW || Math.abs(v) > halfH) return null;
        return new double[]{ (u + halfW) / frame.blocksPerPixel(), (halfH - v) / frame.blocksPerPixel() };
    }

    /** A point on the plane, pushed out of its front by depth. */
    public Vector onPlane(double u, double v, double depth){
        return center.clone()
                .add(right.clone().multiply(u))
                .add(up.clone().multiply(v))
                .add(normal.clone().multiply(depth));
    }

    /**
     * Whether every display it drew is still in the world. An unloaded chunk throws away what is not saved
     * with it, which these never are, so a surface left behind by everyone can come back to find them gone.
     */
    public boolean intact(){
        for (Entity e : displays) if (!e.isValid()) return false;
        return true;
    }

    /** Removes everything it drew. It draws again on the next layout. */
    public void remove(){
        removeDisplays();
        layout = null;
        revision = -1;
    }

    private void removeDisplays(){
        for (Entity e : displays) if (e.isValid()) e.remove();
        displays.clear();
        drawn.clear();
    }

    // ---- painting ------------------------------------------------------------------------------------

    /** Lays the tree out again and repaints whatever came out different. */
    public void relayout(){
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
        List<Paint> paints = new ArrayList<>();
        if (layout != null) paint(layout, null, 1.0f, paints);

        for (int i = 0; i < paints.size(); i++) {
            Paint paint = paints.get(i);
            if (i == displays.size()) {
                // dressed before it is added to the world, so its first frame is already where it goes
                displays.add(spawnDisplay(i * DEPTH_STEP, d -> apply(d, paint, null)));
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

    // ---- entities ------------------------------------------------------------------------------------

    /**
     * Spawns something that belongs to this surface, set up before it is added to the world: never saved
     * with the chunk, marked with the key, and seen by this surface's viewers alone.
     */
    public <T extends Entity> T spawn(Location at, Class<T> type, Consumer<T> setup){
        T entity = world.spawn(at, type, e -> {
            e.setVisibleByDefault(viewers == null);
            e.setPersistent(false);
            e.getPersistentDataContainer().set(entityKey, PersistentDataType.BYTE, (byte) 1);
            setup.accept(e);
        });
        if (viewers != null) {
            for (UUID id : viewers) {
                Player viewer = Bukkit.getPlayer(id);
                if (viewer != null) viewer.showEntity(plugin, entity);
            }
        }
        return entity;
    }

    /**
     * Spawns something only one player sees, whoever else sees the surface: a cursor of their own on a
     * hologram everyone is looking at. Never saved with the chunk, and marked with the key like the rest.
     */
    public <T extends Entity> T spawnFor(Player viewer, Location at, Class<T> type, Consumer<T> setup){
        T entity = world.spawn(at, type, e -> {
            e.setVisibleByDefault(false);
            e.setPersistent(false);
            e.getPersistentDataContainer().set(entityKey, PersistentDataType.BYTE, (byte) 1);
            setup.accept(e);
        });
        viewer.showEntity(plugin, entity);
        return entity;
    }

    /**
     * A display on the plane's middle, pushed out of the front by depth. Its transformation alone moves it
     * about the plane, so a repaint never has to teleport it.
     */
    public TextDisplay spawnDisplay(double depth, Consumer<TextDisplay> dress){
        Location at = onPlane(0, 0, depth).toLocation(world, 0f, 0f);
        return spawn(at, TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setShadowRadius(0f);
            d.setShadowStrength(0f);
            d.setViewRange(viewers == null ? 1.0f : 0.5f);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setAlignment(TextDisplay.TextAlignment.LEFT);
            d.setLineWidth(Integer.MAX_VALUE / 2); // a laid out line is already as wide as it may be
            dress.accept(d);
        });
    }
}
