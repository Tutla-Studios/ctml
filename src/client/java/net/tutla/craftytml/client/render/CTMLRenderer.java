package net.tutla.craftytml.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.style.BackgroundImage;
import net.tutla.craftytml.client.ctml.style.Gradient;
import net.tutla.craftytml.client.dom.DOMTreeElement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CTMLRenderer {
    private static final int DEFAULT_TEXT_COLOR = 0xFFFFFFFF;
    private static final int DEFAULT_BORDER_COLOR = 0xFFFFFFFF;

    /** The four sides, in the order every border array is written in. */
    private static final int TOP = 0, RIGHT = 1, BOTTOM = 2, LEFT = 3;

    private static final ElementStyle.Key<Integer>[] BORDER_WIDTHS = widths();
    private static final ElementStyle.Key<Integer>[] BORDER_COLORS = colors();
    private static final ElementStyle.Key<ElementStyle.BorderStyle>[] BORDER_STYLES = styles();

    /** Where a clip reaches on an axis that is not clipped at all, which is anywhere the screen goes. */
    private static final int UNBOUNDED = 1 << 20;

    public static CTMLLayout.Box renderScreen(DOMTreeElement domTreeHeadElement, Screen screen, GuiGraphicsExtractor graphics){
        return render(domTreeHeadElement, graphics, screen.width, screen.height);
    }

    public static CTMLLayout.Box render(DOMTreeElement domHeadElement, GuiGraphicsExtractor graphics, int viewWidth, int viewHeight){
        CTMLLayout.Box root = layout(domHeadElement, viewWidth, viewHeight);
        paint(root, graphics);
        return root;
    }

    /** Works out where everything goes without drawing any of it, so a screen can keep the answer. */
    public static CTMLLayout.Box layout(DOMTreeElement domHeadElement, int viewWidth, int viewHeight){
        return new CTMLLayout(Minecraft.getInstance().font, viewWidth, viewHeight)
                .layout(domHeadElement, 0, 0, viewWidth, viewHeight);
    }

    /** Draws a tree that has already been laid out, which is every frame's half of the work. */
    public static void paint(CTMLLayout.Box root, GuiGraphicsExtractor graphics){
        if (root == null) return;
        paintContext(root, graphics, Minecraft.getInstance().font, null,
                root.style().get(ElementStyle.OPACITY, 1.0f));
    }

    // ---- stacking contexts ----

    /** A rectangle in screen pixels: a clip, or the part of the screen something is known to cover. */
    private record Rect(int x, int y, int right, int bottom) {
        boolean isEmpty(){ return right <= x || bottom <= y; }

        boolean covers(Rect other){
            return other.isEmpty()
                    || (x <= other.x && y <= other.y && right >= other.right && bottom >= other.bottom);
        }

        Rect and(Rect other){
            return other == null ? this : new Rect(Math.max(x, other.x), Math.max(y, other.y),
                    Math.min(right, other.right), Math.min(bottom, other.bottom));
        }
    }

    /**
     * One box waiting to be drawn, as the stacking context above it sees it: the clip it is under, the
     * group opacity it inherited, and the keys it is sorted by. order is where it came in the tree walk,
     * so everything with the same z-index keeps the order it was written in.
     */
    private record Layer(CTMLLayout.Box box, Rect clip, float alpha, int z, int drag, int order) {}

    /**
     * Paints one stacking context: the box itself first, then everything under it that does not start a
     * context of its own, all of it sorted by z-index rather than by where it sits in the tree. That is
     * what lets a z-index lift a box over an uncle box drawn later, since the whole context is sorted at
     * once instead of each parent sorting its own children.
     */
    private static void paintContext(CTMLLayout.Box box, GuiGraphicsExtractor graphics, Font font,
                                     Rect clip, float alpha){
        if (alpha <= 0.0f || (clip != null && clip.isEmpty())) return;

        List<Layer> layers = new ArrayList<>();
        layers.add(new Layer(box, clip, alpha, 0, 0, 0));
        collect(box, inside(box, clip), alpha, 0, layers, new int[]{ 1 });

        sort(layers);

        // inside a faded group everything is drawn at the group's alpha, so whatever an opaque box covers
        // is left out rather than drawn under it and faded a second time
        Rect[] opaque = alpha < 1.0f ? occluders(layers) : null;

        for (int i = 0; i < layers.size(); i++) {
            Layer layer = layers.get(i);
            if (i > 0 && startsContext(layer.box())) {
                paintContext(layer.box(), graphics, font, layer.clip(), layer.alpha());
            } else {
                paintSelf(layer, graphics, font, covered(opaque, i));
            }
        }
    }

    /** The layers of one context in the order they are drawn, which is by z-index and then by tree order. */
    private static void sort(List<Layer> layers){
        if (layers.size() <= 2) return;
        layers.subList(1, layers.size()).sort(Comparator.comparingInt(Layer::z)
                .thenComparingInt(Layer::drag) // a box the user dragged comes over the ones it was dropped on
                .thenComparingInt(Layer::order));
    }

    /**
     * The order the stacking context a box starts paints in, as the boxes themselves: the box first and
     * then everything under it that does not start a context of its own, sorted the way paint sorts it.
     * A box that does start one stands for the whole of its own context, since nothing inside one is
     * sorted against anything outside it.
     */
    public static List<CTMLLayout.Box> order(CTMLLayout.Box box){
        List<Layer> layers = new ArrayList<>();
        layers.add(new Layer(box, null, 1.0f, 0, 0, 0));
        collect(box, inside(box, null), 1.0f, 0, layers, new int[]{ 1 });
        sort(layers);

        List<CTMLLayout.Box> boxes = new ArrayList<>(layers.size());
        for (Layer layer : layers) boxes.add(layer.box());
        return boxes;
    }

    /**
     * Gathers everything under a box into the layers of its stacking context. A child that starts a
     * context of its own goes in whole, since nothing inside one is sorted against anything outside it;
     * any other child goes in followed by its own descendants, so a z-index down there is sorted against
     * this context rather than against its siblings.
     */
    private static void collect(CTMLLayout.Box box, Rect clip, float alpha, int drag,
                                List<Layer> layers, int[] order){
        for (CTMLLayout.Box child : box.children()) {
            float childAlpha = alpha * child.style().get(ElementStyle.OPACITY, 1.0f);
            int childDrag = drag | (isDragged(child) ? 1 : 0); // a dragged box takes its subtree up with it
            boolean context = startsContext(child);
            int z = context ? child.style().get(ElementStyle.Z_INDEX, 0) : 0;

            layers.add(new Layer(child, clip, childAlpha, z, childDrag, order[0]++));
            if (!context) collect(child, inside(child, clip), childAlpha, childDrag, layers, order);
        }
    }

    /**
     * What starts a stacking context: an opacity that has to be drawn as one thing, a z-index of its own,
     * and a box that clips, since nothing inside one could be lifted clear of the clip anyway.
     */
    private static boolean startsContext(CTMLLayout.Box box){
        ElementStyle style = box.style();
        return style.get(ElementStyle.OPACITY, 1.0f) < 1.0f
                || style.isSet(ElementStyle.Z_INDEX)
                || box.clipsX() || box.clipsY();
    }

    private static boolean isDragged(CTMLLayout.Box box){
        return box.element().getDragX() != 0 || box.element().getDragY() != 0;
    }

    /**
     * The clip everything inside a box is under: whatever it was already under, cut down to the
     * scrollport on whichever axes the box clips.
     */
    private static Rect inside(CTMLLayout.Box box, Rect clip){
        if (!box.clipsX() && !box.clipsY()) return clip;
        CTMLLayout.Port port = box.port();
        Rect own = new Rect(
                box.clipsX() ? port.x() : -UNBOUNDED,
                box.clipsY() ? port.y() : -UNBOUNDED,
                box.clipsX() ? port.right() : UNBOUNDED,
                box.clipsY() ? port.bottom() : UNBOUNDED);
        return own.and(clip);
    }

    /** Cuts everything outside a rectangle away, or hands back false where there was nothing to cut to. */
    private static boolean push(GuiGraphicsExtractor graphics, Rect clip){
        if (clip == null) return false;
        graphics.enableScissor(Math.max(0, clip.x()), Math.max(0, clip.y()),
                Math.min(graphics.guiWidth(), clip.right()), Math.min(graphics.guiHeight(), clip.bottom()));
        return true;
    }

    /**
     * The boxes of a faded group whose own background colour covers outright whatever is behind it, so
     * nothing drawn earlier needs to show through. Only a square, wholly opaque background counts: a
     * rounded or translucent one does not cover what is under it.
     */
    private static Rect[] occluders(List<Layer> layers){
        Rect[] opaque = new Rect[layers.size()];
        for (int i = 0; i < layers.size(); i++) {
            CTMLLayout.Box box = layers.get(i).box();
            ElementStyle style = box.style();
            Integer color = style.get(ElementStyle.BACKGROUND_COLOR);
            if (color == null || (color >>> 24) != 0xFF) continue;
            if (style.get(ElementStyle.BORDER_RADIUS, 0) > 0) continue;
            if (style.get(ElementStyle.BACKGROUND_CLIP, ElementStyle.BackgroundClip.BORDER_BOX)
                    != ElementStyle.BackgroundClip.BORDER_BOX) continue;
            if (style.get(ElementStyle.VISIBILITY) == ElementStyle.Visibility.HIDDEN) continue;
            if (style.get(ElementStyle.OPACITY, 1.0f) < 1.0f) continue;

            Rect rect = new Rect(box.x(), box.y(), box.right(), box.bottom()).and(layers.get(i).clip());
            if (!rect.isEmpty()) opaque[i] = rect;
        }
        return opaque;
    }

    /** The covers that come after a layer, which are the ones it does not have to be drawn under. */
    private static List<Rect> covered(Rect[] opaque, int from){
        if (opaque == null) return List.of();
        List<Rect> rects = new ArrayList<>();
        for (int i = from + 1; i < opaque.length; i++) if (opaque[i] != null) rects.add(opaque[i]);
        return rects;
    }

    /** Whether something is wholly behind a cover, so there is no point drawing it at all. */
    private static boolean hidden(List<Rect> covered, Rect area){
        for (Rect cover : covered) if (cover.covers(area)) return true;
        return false;
    }

    /** One box's own painting: its background, its frame, its text and its scrollbars, in that order. */
    private static void paintSelf(Layer layer, GuiGraphicsExtractor graphics, Font font, List<Rect> covered){
        CTMLLayout.Box box = layer.box();
        if (box.style().get(ElementStyle.VISIBILITY) == ElementStyle.Visibility.HIDDEN) return;
        if (layer.clip() != null && layer.clip().isEmpty()) return;

        float alpha = layer.alpha();
        boolean clipped = push(graphics, layer.clip());

        background(box, graphics, alpha, covered);
        borders(box, graphics, alpha);

        // the text is content, so the box's own clip cuts it off as well as whatever is above it
        Rect inner = inside(box, layer.clip());
        if (inner == layer.clip()) {
            text(box, graphics, font, alpha, covered);
        } else if (!inner.isEmpty()) {
            push(graphics, inner);
            text(box, graphics, font, alpha, covered);
            graphics.disableScissor();
        }

        // the bars go on last so nothing inside the box can cover them, and outside its own clip so a
        // thumb sitting in its gutter is not cut off by the very box it belongs to
        scrollbars(box, graphics, alpha);
        if (clipped) graphics.disableScissor();
    }

    /**
     * The order a parent's children are drawn in, which is still what hit testing walks: z-index decides
     * what covers what, and a box the user has dragged comes over its siblings so it is not left under
     * one it was dropped on.
     */
    static List<CTMLLayout.Box> paintOrder(List<CTMLLayout.Box> children){
        if (children.size() < 2) return children;

        boolean layered = false;
        for (CTMLLayout.Box child : children) layered |= child.style().isSet(ElementStyle.Z_INDEX) || isDragged(child);
        if (!layered) return children;

        List<CTMLLayout.Box> sorted = new ArrayList<>(children);
        sorted.sort(Comparator.<CTMLLayout.Box>comparingInt(child -> child.style().get(ElementStyle.Z_INDEX, 0))
                .thenComparingInt(child -> isDragged(child) ? 1 : 0)); // stable, so ties stay put
        return sorted;
    }

    // ---- scrollbars ----

    /** The scrollbars of a box, track then thumb, each one a flat fill. */
    private static void scrollbars(CTMLLayout.Box box, GuiGraphicsExtractor graphics, float alpha){
        bar(CTMLScroll.verticalBar(box), box.style(), graphics, alpha);
        bar(CTMLScroll.horizontalBar(box), box.style(), graphics, alpha);
    }

    private static void bar(CTMLScroll.Bar bar, ElementStyle style, GuiGraphicsExtractor graphics, float alpha){
        if (bar == null) return;
        int track = fade(CTMLScroll.trackColor(style), alpha);
        if ((track >>> 24) != 0) graphics.fill(bar.x(), bar.y(), bar.right(), bar.bottom(), track);
        rounded(graphics, bar.thumbX(), bar.thumbY(), bar.thumbWidth(), bar.thumbHeight(),
                CTMLScroll.thumbRadius(style), fade(CTMLScroll.thumbColor(style), alpha));
    }

    // ---- background ----

    /**
     * The background of a box: its colour, with the gradient drawn over the top of it rather than
     * instead of it, both cut to wherever background-clip says the background stops.
     * A background clipped to the text is not drawn here at all; the text draws it.
     */
    private static void background(CTMLLayout.Box box, GuiGraphicsExtractor graphics, float alpha,
                                  List<Rect> covered){
        ElementStyle style = box.style();
        ElementStyle.BackgroundClip clip = style.get(ElementStyle.BACKGROUND_CLIP, ElementStyle.BackgroundClip.BORDER_BOX);
        if (clip == ElementStyle.BackgroundClip.TEXT) return;

        int x = box.x();
        int y = box.y();
        int width = box.width();
        int height = box.height();
        int radius = style.get(ElementStyle.BORDER_RADIUS, 0);

        if (clip == ElementStyle.BackgroundClip.PADDING_BOX) {
            int left = borderWidth(style, LEFT);
            int top = borderWidth(style, TOP);
            x += left;
            y += top;
            width = Math.max(0, width - left - borderWidth(style, RIGHT));
            height = Math.max(0, height - top - borderWidth(style, BOTTOM));
            radius = Math.max(0, radius - Math.max(left, top));
        } else if (clip == ElementStyle.BackgroundClip.CONTENT_BOX) {
            radius = Math.max(0, radius - (box.contentX() - box.x()));
            x = box.contentX();
            y = box.contentY();
            width = box.contentWidth();
            height = box.contentHeight();
        }

        Integer color = style.get(ElementStyle.BACKGROUND_COLOR);
        if (color != null) rounded(graphics, x, y, width, height, radius, fade(color, alpha), covered);

        Rect area = new Rect(x, y, x + width, y + height);

        // the image goes over the colour and under the gradient, so a gradient can be laid over it
        BackgroundImage image = style.get(ElementStyle.BACKGROUND_IMAGE);
        if (image != null && image.isDrawable() && !hidden(covered, area)) {
            image(graphics, image, x, y, width, height, radius, alpha);
        }

        Gradient gradient = style.get(ElementStyle.BACKGROUND_GRADIENT);
        if (gradient != null && gradient.isDrawable() && !hidden(covered, area)) {
            gradient(graphics, gradient, x, y, width, height, radius, alpha);
        }
    }

    // ---- background images ----

    /**
     * The image behind a box, as many copies of it as background-repeat asks for. Each copy is cut to
     * the box here rather than by a clip, so an image that is not tiled and does not reach a corner is
     * one draw; only a rounded box needs its rows clipped to, the same rows the background is drawn in.
     *
     * <p>An image no pack holds has no size to work from, so it is stretched over the whole box: that
     * draws minecraft's own missing texture across it, which says far more than drawing nothing.
     */
    private static void image(GuiGraphicsExtractor graphics, BackgroundImage image,
                              int x, int y, int width, int height, int radius, float alpha){
        CTMLTextures.Texture texture = CTMLTextures.of(image.source());
        if (texture == null) return;

        List<BackgroundImage.Tile> tiles = texture.isMeasured()
                ? image.tiles(x, y, width, height, texture.width(), texture.height(),
                        graphics.guiWidth(), graphics.guiHeight())
                : List.of(new BackgroundImage.Tile(x, y, width, height));
        if (tiles.isEmpty()) return;

        int tint = fade(0xFFFFFFFF, alpha);
        int corner = Math.clamp(radius, 0, Math.min(width, height) / 2);
        if (corner <= 0) {
            for (BackgroundImage.Tile tile : tiles) {
                tile(graphics, texture, tile, new Rect(x, y, x + width, y + height), tint);
            }
            return;
        }

        for (int[] band : bands(x, y, width, height, corner)) {
            Rect row = new Rect(band[0], band[1], band[2], band[3]);
            for (BackgroundImage.Tile tile : tiles) tile(graphics, texture, tile, row, tint);
        }
    }

    /**
     * One copy of an image, cut down to the part of it that shows. The cut is taken out of the image as
     * well as out of the draw, so the part that shows is the part of the picture that was there: the
     * blit is handed the slice of the texture that matches what is left of the tile.
     */
    private static void tile(GuiGraphicsExtractor graphics, CTMLTextures.Texture texture,
                             BackgroundImage.Tile tile, Rect clip, int tint){
        int left = Math.max(tile.x(), clip.x());
        int top = Math.max(tile.y(), clip.y());
        int right = Math.min(tile.right(), clip.right());
        int bottom = Math.min(tile.bottom(), clip.bottom());
        if (right <= left || bottom <= top) return;

        int imageWidth = texture.isMeasured() ? texture.width() : tile.width();
        int imageHeight = texture.isMeasured() ? texture.height() : tile.height();

        float u = (left - tile.x()) * (float) imageWidth / tile.width();
        float v = (top - tile.y()) * (float) imageHeight / tile.height();
        int across = Math.max(1, Math.round((right - left) * (float) imageWidth / tile.width()));
        int down = Math.max(1, Math.round((bottom - top) * (float) imageHeight / tile.height()));

        graphics.blit(RenderPipelines.GUI_TEXTURED, texture.id(), left, top, u, v,
                right - left, bottom - top, across, down, imageWidth, imageHeight, tint);
    }

    /**
     * The rows a rounded rectangle is made of: the straight middle, and the rows of each corner,
     * every one as wide as the circle is at that height. A radius of nothing is one rectangle.
     * Rows are grouped where the radius is big, so the number of fills stays in the teens.
     */
    private static List<int[]> bands(int x, int y, int width, int height, int radius){
        List<int[]> bands = new ArrayList<>();
        if (width <= 0 || height <= 0) return bands;

        radius = Math.clamp(radius, 0, Math.min(width, height) / 2);
        if (radius <= 0) {
            bands.add(new int[]{ x, y, x + width, y + height });
            return bands;
        }

        bands.add(new int[]{ x, y + radius, x + width, y + height - radius });

        int step = step(radius);
        for (int offset = 0; offset < radius; offset += step) {
            int rows = Math.min(step, radius - offset);
            // measured at the row's outer edge, so the shape never pokes outside the circle
            int far = radius - offset - rows;
            int inset = inset(radius, far);
            bands.add(new int[]{ x + inset, y + offset, x + width - inset, y + offset + rows });
            bands.add(new int[]{ x + inset, y + height - offset - rows, x + width - inset, y + height - offset });
        }
        return bands;
    }

    /** How many rows of a corner are drawn as one, which is what keeps a big radius from costing a draw a row. */
    private static int step(int radius){
        return Math.max(1, radius / 8);
    }

    /** How far in from the side of a shape its outline sits, given how far the row is from the circle's middle. */
    private static int inset(int radius, int far){
        if (far >= radius) return radius;
        return radius - (int) Math.round(Math.sqrt((double) radius * radius - (double) far * far));
    }

    /** A fill with its corners rounded off, one fill per row of the corners. */
    private static void rounded(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int color){
        rounded(graphics, x, y, width, height, radius, color, List.of());
    }

    private static void rounded(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius,
                                int color, List<Rect> covered){
        if ((color >>> 24) == 0) return;
        for (int[] band : bands(x, y, width, height, radius)) {
            if (covered.isEmpty()) graphics.fill(band[0], band[1], band[2], band[3], color);
            else fillUncovered(graphics, band, color, covered);
        }
    }

    /**
     * One row of a shape with the parts of it that something opaque will cover left out: the row is split
     * wherever a cover starts or ends, and then each slice is drawn either side of whatever covers it.
     */
    private static void fillUncovered(GuiGraphicsExtractor graphics, int[] band, int color, List<Rect> covered){
        List<Integer> rows = new ArrayList<>();
        rows.add(band[1]);
        rows.add(band[3]);
        for (Rect cover : covered) {
            if (cover.y() > band[1] && cover.y() < band[3]) rows.add(cover.y());
            if (cover.bottom() > band[1] && cover.bottom() < band[3]) rows.add(cover.bottom());
        }
        rows.sort(null);

        for (int i = 0; i < rows.size() - 1; i++) {
            int from = rows.get(i);
            int to = rows.get(i + 1);
            if (to <= from) continue;

            // the covers that span the whole slice, which is all of them: the rows were split on their edges
            List<int[]> spans = new ArrayList<>();
            for (Rect cover : covered) {
                if (cover.y() > from || cover.bottom() < to) continue;
                if (cover.right() <= band[0] || cover.x() >= band[2]) continue;
                spans.add(new int[]{ Math.max(band[0], cover.x()), Math.min(band[2], cover.right()) });
            }
            spans.sort(Comparator.comparingInt(span -> span[0]));

            int at = band[0];
            for (int[] span : spans) {
                if (span[0] > at) graphics.fill(at, from, span[0], to, color);
                at = Math.max(at, span[1]);
            }
            if (at < band[2]) graphics.fill(at, from, band[2], to, color);
        }
    }

    // ---- gradients ----

    /** How many wedges a gradient running out from the middle is drawn in. */
    private static final int RADIAL_WEDGES = 32;

    /** And one running round it, where the colour changes across a wedge rather than along it. */
    private static final int CONIC_WEDGES = 32;

    /**
     * A gradient over a box, cut to the box's rounded shape by clipping to each of its rows in turn.
     * Everything is drawn against the box itself, so the rows of one shape all agree on where the
     * gradient starts and ends.
     */
    private static void gradient(GuiGraphicsExtractor graphics, Gradient gradient,
                                 int x, int y, int width, int height, int radius, float alpha){
        if (width <= 0 || height <= 0) return;

        for (int[] band : bands(x, y, width, height, radius)) {
            graphics.enableScissor(band[0], band[1], band[2], band[3]);
            switch (gradient.kind()) {
                case LINEAR -> linear(graphics, gradient, x, y, width, height, alpha);
                case RADIAL -> radial(graphics, gradient, x, y, width, height, alpha);
                case CONIC -> conic(graphics, gradient, x, y, width, height, alpha);
            }
            graphics.disableScissor();
        }
    }

    /**
     * A straight ramp at any angle, drawn in the frame the gradient runs down: the pose is turned so
     * the gradient points straight down, and then each pair of stops is one of minecraft's own two
     * colour fills. One draw per pair however the ramp is angled, rather than one per pixel column.
     */
    private static void linear(GuiGraphicsExtractor graphics, Gradient gradient,
                               int x, int y, int width, int height, float alpha){
        List<Gradient.Stop> stops = gradient.stops();
        double radians = Math.toRadians(gradient.angle());

        // the box's own size, measured along the gradient and across it
        int length = Math.max(1, Math.round(gradient.axisLength(width, height)));
        int across = Math.round((float) (Math.abs(width * Math.cos(radians)) + Math.abs(height * Math.sin(radians))));
        int half = across / 2 + 2;
        int start = -length / 2;
        int end = start + length;

        graphics.pose().pushMatrix();
        graphics.pose().translate(x + width / 2.0f, y + height / 2.0f);
        // the local y axis is turned to point the way the angle names, which is where the ramp ends
        graphics.pose().rotate((float) (radians + Math.PI));

        int first = Math.round(start + stops.getFirst().at() * length);
        if (first > start) graphics.fill(-half, start - 1, half, first, fade(stops.getFirst().color(), alpha));

        for (int i = 0; i < stops.size() - 1; i++) {
            Gradient.Stop from = stops.get(i);
            Gradient.Stop to = stops.get(i + 1);
            int top = Math.round(start + from.at() * length);
            int bottom = Math.round(start + to.at() * length);
            if (bottom <= top) continue;
            graphics.fillGradient(-half, top, half, bottom, fade(from.color(), alpha), fade(to.color(), alpha));
        }

        int last = Math.round(start + stops.getLast().at() * length);
        if (last < end) graphics.fill(-half, last, half, end + 1, fade(stops.getLast().color(), alpha));

        graphics.pose().popMatrix();
    }

    /**
     * A gradient running out from the middle, drawn as wedges: each one a rectangle reaching out from the
     * centre, turned to the angle it covers, carrying the whole ramp along its own length. A real ramp
     * rather than a stack of flat discs, and a couple of dozen fills rather than a couple of hundred.
     * The wedges still overlap near the centre, where a rectangle is far wider than the slice it stands
     * for, so translucent stops blend in there.
     */
    private static void radial(GuiGraphicsExtractor graphics, Gradient gradient,
                               int x, int y, int width, int height, float alpha){
        List<Gradient.Stop> stops = gradient.stops();
        double slice = Math.PI / RADIAL_WEDGES;
        float reach = gradient.radius(width, height);
        // the outer end of a wedge is a chord of the circle, so it has to reach past it to cover the corners
        int length = Math.max(1, Math.round(reach / (float) Math.cos(slice)) + 1);
        int half = Math.max(1, Math.round(reach * (float) Math.tan(slice)) + 1);

        graphics.pose().pushMatrix();
        graphics.pose().translate(x + width / 2.0f, y + height / 2.0f);

        for (int wedge = 0; wedge < RADIAL_WEDGES; wedge++) {
            graphics.pose().pushMatrix();
            graphics.pose().rotate((float) (2 * Math.PI * wedge / RADIAL_WEDGES));

            int first = Math.round(stops.getFirst().at() * length);
            if (first > 0) graphics.fill(-half, -first, half, 1, fade(stops.getFirst().color(), alpha));

            for (int i = 0; i < stops.size() - 1; i++) {
                int from = Math.round(stops.get(i).at() * length);
                int to = Math.round(stops.get(i + 1).at() * length);
                if (to <= from) continue;
                graphics.fillGradient(-half, -to, half, -from,
                        fade(stops.get(i + 1).color(), alpha), fade(stops.get(i).color(), alpha));
            }

            int last = Math.round(stops.getLast().at() * length);
            if (last < length) graphics.fill(-half, -length, half, -last, fade(stops.getLast().color(), alpha));

            graphics.pose().popMatrix();
        }

        graphics.pose().popMatrix();
    }

    /**
     * A gradient running round the middle. A wedge's colour changes across it rather than along it, so
     * the circle is drawn as rings of wedges, each ring a ramp between the colours at the angles its two
     * corners sit at. Each ring in is half the radius and half the wedges of the one outside it, since a
     * wedge covers twice the turn for the same width down there, and a wedge thinner than a pixel is no
     * use to anyone. That halving is what keeps the whole thing to a couple of handfuls of fills.
     */
    private static void conic(GuiGraphicsExtractor graphics, Gradient gradient,
                              int x, int y, int width, int height, float alpha){
        float reach = gradient.radius(width, height);
        int length = Math.max(1, Math.round(reach / (float) Math.cos(Math.PI / CONIC_WEDGES)) + 1);

        graphics.pose().pushMatrix();
        graphics.pose().translate(x + width / 2.0f, y + height / 2.0f);

        int wedges = CONIC_WEDGES;
        for (float outer = length; outer >= 1; outer /= 2, wedges /= 2) {
            double slice = Math.PI / wedges;
            // the last ring reaches the middle, where every colour meets and a wedge is a pixel across
            float inner = wedges <= 4 ? 0 : outer / 2;
            int across = Math.max(1, Math.round(outer * (float) Math.tan(slice)) + 1);
            // how much of the turn the ring's width covers, which is what the ramp runs between
            float spread = (float) (Math.atan2(across, (inner + outer) / 2.0) / (2 * Math.PI));

            for (int wedge = 0; wedge < wedges; wedge++) {
                float turn = (wedge + 0.5f) / wedges;
                // the frame is turned a quarter past the wedge's own angle, so the ramp across the wedge
                // runs down the local y axis, which is the only way a two colour fill can be pointed
                graphics.pose().pushMatrix();
                graphics.pose().rotate((float) (Math.toRadians(gradient.angle()) + 2 * Math.PI * turn + Math.PI / 2));
                graphics.fillGradient(-Math.round(outer), -across, -Math.round(inner) + 1, across,
                        fade(gradient.colorAt(turn + spread), alpha), fade(gradient.colorAt(turn - spread), alpha));
                graphics.pose().popMatrix();
            }

            if (wedges <= 4) break;
        }

        graphics.pose().popMatrix();
    }

    // ---- borders ----

    /**
     * The frame around a box: the ring between its rounded outline and the rounded padding box inside it,
     * so a rounded box gets rounded corners rather than a square frame. Each side carries its own colour
     * and style, and the two sides meeting at a corner share it out along the line from the outer corner
     * to the inner one, which is how css miters them.
     */
    private static void borders(CTMLLayout.Box box, GuiGraphicsExtractor graphics, float alpha){
        ElementStyle style = box.style();
        int[] widths = new int[4];
        int[] colors = new int[4];
        ElementStyle.BorderStyle[] kinds = new ElementStyle.BorderStyle[4];
        boolean any = false;

        for (int side = 0; side < 4; side++) {
            kinds[side] = style.get(BORDER_STYLES[side], ElementStyle.BorderStyle.SOLID);
            widths[side] = borderWidth(style, side);
            // css starts a border off at the colour of the text, which is what currentColor means
            colors[side] = fade(style.get(BORDER_COLORS[side],
                    style.get(ElementStyle.COLOR, DEFAULT_BORDER_COLOR)), alpha);
            any |= widths[side] > 0 && (colors[side] >>> 24) != 0;
        }
        if (!any) return;

        int radius = style.get(ElementStyle.BORDER_RADIUS, 0);
        int[] outer = new int[4];
        int[] thick = new int[4];

        // a double border is two thinner rings with a gap between them, each a third of the width
        for (int side = 0; side < 4; side++) {
            thick[side] = kinds[side] == ElementStyle.BorderStyle.DOUBLE
                    ? Math.max(1, widths[side] / 3)
                    : widths[side];
        }
        ring(graphics, box, radius, outer, thick, colors, kinds);

        boolean doubled = false;
        for (int side = 0; side < 4; side++) {
            boolean twice = kinds[side] == ElementStyle.BorderStyle.DOUBLE && widths[side] >= 3;
            thick[side] = twice ? Math.max(1, widths[side] / 3) : 0;
            outer[side] = twice ? widths[side] - thick[side] : 0;
            doubled |= twice;
        }
        if (doubled) ring(graphics, box, radius, outer, thick, colors, kinds);
    }

    private static int borderWidth(ElementStyle style, int side){
        return CTMLLayout.borderWidth(style, BORDER_WIDTHS[side], BORDER_STYLES[side]);
    }

    /**
     * One ring between two rounded shapes, row by row. The rows above the inner shape belong to the top
     * side and the ones below it to the bottom; in between, the spans either side of it are the left and
     * the right one. The rows are the ones the background is drawn in, so the two shapes agree to the pixel.
     */
    private static void ring(GuiGraphicsExtractor graphics, CTMLLayout.Box box, int radius,
                             int[] outer, int[] thick, int[] colors, ElementStyle.BorderStyle[] kinds){
        int x = box.x() + outer[LEFT];
        int y = box.y() + outer[TOP];
        int width = box.width() - outer[LEFT] - outer[RIGHT];
        int height = box.height() - outer[TOP] - outer[BOTTOM];
        if (width <= 0 || height <= 0) return;

        int outside = Math.max(Math.max(outer[TOP], outer[RIGHT]), Math.max(outer[BOTTOM], outer[LEFT]));
        int shapeRadius = Math.clamp(radius - outside, 0, Math.min(width, height) / 2);

        int holeX = x + thick[LEFT];
        int holeTop = y + thick[TOP];
        int holeRight = x + width - thick[RIGHT];
        int holeBottom = y + height - thick[BOTTOM];
        boolean hole = holeRight > holeX && holeBottom > holeTop;
        int inside = Math.max(Math.max(thick[TOP], thick[RIGHT]), Math.max(thick[BOTTOM], thick[LEFT]));
        int holeRadius = hole
                ? Math.clamp(shapeRadius - inside, 0, Math.min(holeRight - holeX, holeBottom - holeTop) / 2)
                : 0;

        for (int[] band : bands(x, y, width, height, shapeRadius)) {
            for (int[] rows : split(band[1], band[3], holeTop, holeBottom)) {
                int from = rows[0];
                int to = rows[1];
                if (hole && from >= holeTop && to <= holeBottom) {
                    int gap = inset(holeTop, holeBottom, holeRadius, from, to);
                    piece(graphics, box, band[0], from, Math.min(holeX + gap, band[2]), to, LEFT, colors, kinds, thick);
                    piece(graphics, box, Math.max(holeRight - gap, band[0]), from, band[2], to, RIGHT, colors, kinds, thick);
                    continue;
                }
                corner(graphics, box, band, from, to, from < holeTop || !hole && from < y + height / 2,
                        y, y + height, colors, kinds, thick);
            }
        }
    }

    /** A row of the ring split at the edges of the inner shape, so no row straddles one of them. */
    private static List<int[]> split(int from, int to, int first, int second){
        List<int[]> rows = new ArrayList<>(3);
        int at = from;
        for (int edge : new int[]{ first, second }) {
            if (edge > at && edge < to) {
                rows.add(new int[]{ at, edge });
                at = edge;
            }
        }
        if (at < to) rows.add(new int[]{ at, to });
        return rows;
    }

    /**
     * A row above or below the inner shape, which is the top or the bottom side with a corner at each end.
     * The corner is given to the side it is nearer along the diagonal from the outer corner to the inner
     * one; where the three sides are drawn the same way there is nothing to split and it is one piece.
     */
    private static void corner(GuiGraphicsExtractor graphics, CTMLLayout.Box box, int[] band, int from, int to,
                               boolean top, int y, int bottom, int[] colors, ElementStyle.BorderStyle[] kinds,
                               int[] thick){
        int side = top ? TOP : BOTTOM;
        if (same(side, LEFT, colors, kinds) && same(side, RIGHT, colors, kinds)) {
            piece(graphics, box, band[0], from, band[2], to, side, colors, kinds, thick);
            return;
        }

        // how far down the corner this row sits, as a share of the side's own width
        int depth = thick[side];
        float along = depth <= 0 ? 1.0f
                : Math.clamp(((from + to) / 2.0f + 0.5f - (top ? y : bottom - depth)) / depth, 0.0f, 1.0f);
        float share = top ? along : 1.0f - along;

        int left = Math.clamp(band[0] + Math.round(thick[LEFT] * share), band[0], band[2]);
        int right = Math.clamp(band[2] - Math.round(thick[RIGHT] * share), left, band[2]);

        piece(graphics, box, band[0], from, left, to, LEFT, colors, kinds, thick);
        piece(graphics, box, left, from, right, to, side, colors, kinds, thick);
        piece(graphics, box, right, from, band[2], to, RIGHT, colors, kinds, thick);
    }

    private static boolean same(int side, int other, int[] colors, ElementStyle.BorderStyle[] kinds){
        return colors[side] == colors[other] && kinds[side] == kinds[other];
    }

    /**
     * One piece of one side, dashed or dotted along the way it runs if that is what it was asked for.
     * The dashes are measured from the box's own edge, so the pieces of a side all keep one pattern.
     */
    private static void piece(GuiGraphicsExtractor graphics, CTMLLayout.Box box, int x, int y, int right, int bottom,
                              int side, int[] colors, ElementStyle.BorderStyle[] kinds, int[] thick){
        if (right <= x || bottom <= y || (colors[side] >>> 24) == 0) return;

        ElementStyle.BorderStyle kind = kinds[side];
        if (kind != ElementStyle.BorderStyle.DASHED && kind != ElementStyle.BorderStyle.DOTTED) {
            graphics.fill(x, y, right, bottom, colors[side]);
            return;
        }

        int unit = Math.max(1, thick[side]);
        int on = kind == ElementStyle.BorderStyle.DOTTED ? unit : unit * 2;
        int period = on + unit;
        boolean across = side == TOP || side == BOTTOM;
        int start = across ? x : y;
        int stop = across ? right : bottom;
        int origin = across ? box.x() : box.y();

        for (int at = Math.floorDiv(start - origin, period) * period + origin; at < stop; at += period) {
            int dashStart = Math.max(start, at);
            int dashStop = Math.min(stop, at + on);
            if (dashStop <= dashStart) continue;
            if (across) graphics.fill(dashStart, y, dashStop, bottom, colors[side]);
            else graphics.fill(x, dashStart, right, dashStop, colors[side]);
        }
    }

    /** How far in from the side of a shape its outline sits over a row of it, measured like the bands are. */
    private static int inset(int top, int bottom, int radius, int from, int to){
        if (radius <= 0) return 0;
        int far = Math.max(radius - (to - top), radius - (bottom - from));
        return far <= 0 ? 0 : inset(radius, far);
    }

    // ---- text ----

    private static void text(CTMLLayout.Box box, GuiGraphicsExtractor graphics, Font font, float alpha,
                             List<Rect> covered){
        if (box.lines().isEmpty()) return;

        ElementStyle style = box.style();
        int color = fade(style.get(ElementStyle.COLOR, DEFAULT_TEXT_COLOR), alpha);
        boolean shadow = style.get(ElementStyle.TEXT_SHADOW, true); // what minecraft does with text by default

        // a text gradient paints the glyphs, and so does a background clipped to them: css's bg-clip-text,
        // which leaves the box's own background colour where it is and only takes the gradient off it
        Gradient gradient = style.get(ElementStyle.TEXT_GRADIENT);
        if (gradient == null
                && style.get(ElementStyle.BACKGROUND_CLIP) == ElementStyle.BackgroundClip.TEXT) {
            gradient = style.get(ElementStyle.BACKGROUND_GRADIENT);
        }
        Gradient ramp = gradient != null && gradient.isDrawable() ? gradient : null;

        for (CTMLLayout.TextLine line : box.lines()) {
            int x = box.contentX() + line.x() - box.port().scrollX();
            int y = box.contentY() + line.y() - box.port().scrollY();
            if (hidden(covered, new Rect(x, y, x + line.width(),
                    y + Math.round(font.lineHeight * line.scale())))) continue;

            // letter-spacing and a ramp both have to go glyph by glyph; a plain line is one draw
            if (ramp != null || line.spacing() != 0) {
                glyphs(graphics, font, line, box, ramp, x, y, color, alpha, shadow);
                continue;
            }

            if (line.scale() == 1.0f) {
                graphics.text(font, line.text(), x, y, color, shadow);
                continue;
            }

            // font-size is a scale against minecraft's own, so the whole draw gets scaled around the line's corner
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            graphics.pose().scale(line.scale(), line.scale());
            graphics.text(font, line.text(), 0, 0, color, shadow);
            graphics.pose().popMatrix();
        }
    }

    /**
     * A line drawn a glyph at a time, which is what letter-spacing needs to put room between them and what
     * a gradient needs to give each one its own colour off the ramp.
     * Every glyph keeps the style the line gave it, bold and italic included, so it is measured and drawn
     * the width it really is; a ramp only takes its colour away, since the ramp provides that.
     * The ramp is measured across the whole content box, not the line, so every line of a block agrees.
     */
    private static void glyphs(GuiGraphicsExtractor graphics, Font font, CTMLLayout.TextLine line,
                               CTMLLayout.Box box, Gradient ramp, int x, int y, int color, float alpha,
                               boolean shadow){
        List<FormattedCharSequence> glyphs = new ArrayList<>();
        line.text().accept((index, style, codePoint) -> {
            glyphs.add(FormattedCharSequence.codepoint(codePoint,
                    ramp == null ? style : style.withColor((TextColor) null)));
            return true;
        });
        if (glyphs.isEmpty()) return;

        int width = Math.max(1, box.contentWidth());
        int height = Math.max(1, box.contentHeight());
        float scale = line.scale();
        float advance = 0;

        for (FormattedCharSequence glyph : glyphs) {
            int glyphWidth = font.width(glyph);
            int paint = color;
            if (ramp != null) {
                float middleX = line.x() + advance + glyphWidth * scale / 2.0f;
                float middleY = line.y() + font.lineHeight * scale / 2.0f;
                paint = fade(ramp.colorAt(ramp.positionOf(middleX, middleY, width, height)), alpha);
            }

            graphics.pose().pushMatrix();
            graphics.pose().translate(x + advance, y);
            if (scale != 1.0f) graphics.pose().scale(scale, scale);
            graphics.text(font, glyph, 0, 0, paint, shadow);
            graphics.pose().popMatrix();

            advance += glyphWidth * scale + line.spacing();
        }
    }

    private static int fade(int color, float alpha){
        if (alpha >= 1.0f) return color;
        int faded = Math.round(((color >>> 24) & 0xFF) * alpha);
        return (color & 0x00FFFFFF) | (faded << 24);
    }

    // ---- the border keys, in side order ----

    @SuppressWarnings("unchecked")
    private static ElementStyle.Key<Integer>[] widths(){
        return new ElementStyle.Key[]{ ElementStyle.BORDER_TOP_WIDTH, ElementStyle.BORDER_RIGHT_WIDTH,
                ElementStyle.BORDER_BOTTOM_WIDTH, ElementStyle.BORDER_LEFT_WIDTH };
    }

    @SuppressWarnings("unchecked")
    private static ElementStyle.Key<Integer>[] colors(){
        return new ElementStyle.Key[]{ ElementStyle.BORDER_TOP_COLOR, ElementStyle.BORDER_RIGHT_COLOR,
                ElementStyle.BORDER_BOTTOM_COLOR, ElementStyle.BORDER_LEFT_COLOR };
    }

    @SuppressWarnings("unchecked")
    private static ElementStyle.Key<ElementStyle.BorderStyle>[] styles(){
        return new ElementStyle.Key[]{ ElementStyle.BORDER_TOP_STYLE, ElementStyle.BORDER_RIGHT_STYLE,
                ElementStyle.BORDER_BOTTOM_STYLE, ElementStyle.BORDER_LEFT_STYLE };
    }
}
