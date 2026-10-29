package net.tutla.craftytml.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.style.Gradient;
import net.tutla.craftytml.client.dom.DOMTreeElement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CTMLRenderer {
    private static final int DEFAULT_TEXT_COLOR = 0xFFFFFFFF;
    private static final int DEFAULT_BORDER_COLOR = 0xFFFFFFFF;

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
        if (root != null) paint(root, graphics, Minecraft.getInstance().font, 1.0f);
    }

    private static void paint(CTMLLayout.Box box, GuiGraphicsExtractor graphics, Font font, float opacity){
        ElementStyle style = box.style();
        float alpha = opacity * style.get(ElementStyle.OPACITY, 1.0f);
        if (alpha <= 0.0f) return;

        boolean hidden = style.get(ElementStyle.VISIBILITY) == ElementStyle.Visibility.HIDDEN;
        if (!hidden) {
            background(box, graphics, alpha);
            borders(box, graphics, alpha);
        }

        boolean clipped = box.clipsX() || box.clipsY();
        if (clipped) clip(box, graphics);

        if (!hidden) text(box, graphics, font, alpha);
        for (CTMLLayout.Box child : paintOrder(box.children())) paint(child, graphics, font, alpha);

        if (clipped) graphics.disableScissor();

        // the bars go on last so nothing inside the box can cover them, and outside the clip so a thumb
        // sitting in its gutter is not cut off by the very box it belongs to
        if (!hidden) scrollbars(box, graphics, alpha);
    }

    /**
     * Cuts everything outside the scrollport away, on whichever axes the box clips. The axis that does not
     * clip is given the whole screen, since the scissor stack is already cut down by whatever is above.
     */
    private static void clip(CTMLLayout.Box box, GuiGraphicsExtractor graphics){
        CTMLLayout.Port port = box.port();
        graphics.enableScissor(
                box.clipsX() ? port.x() : 0,
                box.clipsY() ? port.y() : 0,
                box.clipsX() ? port.right() : graphics.guiWidth(),
                box.clipsY() ? port.bottom() : graphics.guiHeight());
    }

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

    /**
     * z-index decides what covers what, and a box the user has dragged comes over its siblings so it is
     * not left under one it was dropped on. Everything else keeps the order layout handed over.
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

    private static boolean isDragged(CTMLLayout.Box box){
        return box.element().getDragX() != 0 || box.element().getDragY() != 0;
    }

    /**
     * The background of a box: its colour, with the gradient drawn over the top of it rather than
     * instead of it, both cut to wherever background-clip says the background stops.
     * A background clipped to the text is not drawn here at all; the text draws it.
     */
    private static void background(CTMLLayout.Box box, GuiGraphicsExtractor graphics, float alpha){
        ElementStyle style = box.style();
        ElementStyle.BackgroundClip clip = style.get(ElementStyle.BACKGROUND_CLIP, ElementStyle.BackgroundClip.BORDER_BOX);
        if (clip == ElementStyle.BackgroundClip.TEXT) return;

        int x = box.x();
        int y = box.y();
        int width = box.width();
        int height = box.height();
        int radius = style.get(ElementStyle.BORDER_RADIUS, 0);

        if (clip == ElementStyle.BackgroundClip.PADDING_BOX) {
            int left = style.get(ElementStyle.BORDER_LEFT_WIDTH, 0);
            int top = style.get(ElementStyle.BORDER_TOP_WIDTH, 0);
            x += left;
            y += top;
            width = Math.max(0, width - left - style.get(ElementStyle.BORDER_RIGHT_WIDTH, 0));
            height = Math.max(0, height - top - style.get(ElementStyle.BORDER_BOTTOM_WIDTH, 0));
            radius = Math.max(0, radius - Math.max(left, top));
        } else if (clip == ElementStyle.BackgroundClip.CONTENT_BOX) {
            radius = Math.max(0, radius - (box.contentX() - box.x()));
            x = box.contentX();
            y = box.contentY();
            width = box.contentWidth();
            height = box.contentHeight();
        }

        Integer color = style.get(ElementStyle.BACKGROUND_COLOR);
        if (color != null) rounded(graphics, x, y, width, height, radius, fade(color, alpha));

        Gradient gradient = style.get(ElementStyle.BACKGROUND_GRADIENT);
        if (gradient != null && gradient.isDrawable()) {
            gradient(graphics, gradient, x, y, width, height, radius, alpha);
        }
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

        int step = Math.max(1, radius / 8);
        for (int offset = 0; offset < radius; offset += step) {
            int rows = Math.min(step, radius - offset);
            // measured at the row's outer edge, so the shape never pokes outside the circle
            int far = radius - offset - rows;
            int inset = radius - (int) Math.round(Math.sqrt((double) radius * radius - (double) far * far));
            bands.add(new int[]{ x + inset, y + offset, x + width - inset, y + offset + rows });
            bands.add(new int[]{ x + inset, y + height - offset - rows, x + width - inset, y + height - offset });
        }
        return bands;
    }

    /** A fill with its corners rounded off, one fill per row of the corners. */
    private static void rounded(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int color){
        if ((color >>> 24) == 0) return;
        for (int[] band : bands(x, y, width, height, radius)) {
            graphics.fill(band[0], band[1], band[2], band[3], color);
        }
    }

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
     * A gradient running out from the middle, drawn as discs of one colour each, biggest first, so
     * every one covers the ring inside it. A few dozen fills, which is why the steps are counted out
     * from the size: translucent stops blend where the discs overlap, unlike a straight ramp.
     */
    private static void radial(GuiGraphicsExtractor graphics, Gradient gradient,
                               int x, int y, int width, int height, float alpha){
        int reach = Math.max(1, Math.round(gradient.radius(width, height)));
        int rings = Math.clamp(reach / 4, 2, 12);
        int centerX = x + width / 2;
        int centerY = y + height / 2;

        for (int ring = rings; ring >= 1; ring--) {
            int radius = Math.max(1, reach * ring / rings);
            int color = fade(gradient.colorAt((ring - 0.5f) / rings), alpha);
            rounded(graphics, centerX - radius, centerY - radius, radius * 2, radius * 2, radius, color);
        }
    }

    /** How many wedges a gradient running round the middle is drawn in. */
    private static final int CONIC_WEDGES = 64;

    /**
     * A gradient running round the middle, drawn as wedges of one colour each: a rectangle reaching
     * out from the centre, turned to the angle it covers. The wedges overlap close to the middle,
     * so the innermost pixels take their colour from whichever wedge was drawn last.
     */
    private static void conic(GuiGraphicsExtractor graphics, Gradient gradient,
                              int x, int y, int width, int height, float alpha){
        float reach = gradient.radius(width, height) + 2;
        int length = Math.max(1, Math.round(reach));
        int half = Math.max(1, Math.round(reach * (float) Math.tan(Math.PI / CONIC_WEDGES)) + 1);

        graphics.pose().pushMatrix();
        graphics.pose().translate(x + width / 2.0f, y + height / 2.0f);

        for (int wedge = 0; wedge < CONIC_WEDGES; wedge++) {
            float middle = (wedge + 0.5f) / CONIC_WEDGES;
            int color = fade(gradient.colorAt(middle), alpha);
            graphics.pose().pushMatrix();
            // zero is straight up and it goes round clockwise, the same way the angles do
            graphics.pose().rotate((float) Math.toRadians(gradient.angle() + middle * 360));
            graphics.fill(-half, -length, half, 1, color);
            graphics.pose().popMatrix();
        }

        graphics.pose().popMatrix();
    }

    private static void borders(CTMLLayout.Box box, GuiGraphicsExtractor graphics, float alpha){
        ElementStyle style = box.style();
        int color = fade(style.get(ElementStyle.BORDER_COLOR, DEFAULT_BORDER_COLOR), alpha);

        int top = style.get(ElementStyle.BORDER_TOP_WIDTH, 0);
        int right = style.get(ElementStyle.BORDER_RIGHT_WIDTH, 0);
        int bottom = style.get(ElementStyle.BORDER_BOTTOM_WIDTH, 0);
        int left = style.get(ElementStyle.BORDER_LEFT_WIDTH, 0);

        if (top > 0) graphics.fill(box.x(), box.y(), box.right(), box.y() + top, color);
        if (bottom > 0) graphics.fill(box.x(), box.bottom() - bottom, box.right(), box.bottom(), color);
        if (left > 0) graphics.fill(box.x(), box.y(), box.x() + left, box.bottom(), color);
        if (right > 0) graphics.fill(box.right() - right, box.y(), box.right(), box.bottom(), color);
    }

    private static void text(CTMLLayout.Box box, GuiGraphicsExtractor graphics, Font font, float alpha){
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
        boolean ramp = gradient != null && gradient.isDrawable();

        for (CTMLLayout.TextLine line : box.lines()) {
            int x = box.contentX() + line.x() - box.port().scrollX();
            int y = box.contentY() + line.y() - box.port().scrollY();

            if (line.scale() == 1.0f) {
                if (ramp) gradientText(graphics, font, line, gradient, box, x, y, 1.0f, alpha, shadow);
                else graphics.text(font, line.text(), x, y, color, shadow);
                continue;
            }

            // font-size is a scale against minecraft's own, so the whole draw gets scaled around the line's corner
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            graphics.pose().scale(line.scale(), line.scale());
            if (ramp) gradientText(graphics, font, line, gradient, box, 0, 0, line.scale(), alpha, shadow);
            else graphics.text(font, line.text(), 0, 0, color, shadow);
            graphics.pose().popMatrix();
        }
    }

    /**
     * Gradient text, drawn a glyph at a time so each one can take its own colour off the ramp.
     * Every glyph keeps the style the line gave it, bold and italic included, so it is measured and
     * drawn the width it really is; only its colour is taken away, since the ramp provides that.
     * The ramp is measured across the whole content box, not the line, so every line of a block agrees.
     * Drawing is already inside the scale matrix, so the advances here are unscaled.
     */
    private static void gradientText(GuiGraphicsExtractor graphics, Font font, CTMLLayout.TextLine line, Gradient gradient,
                                     CTMLLayout.Box box, int x, int y, float scale, float alpha, boolean shadow){
        List<FormattedCharSequence> glyphs = new ArrayList<>();
        line.text().accept((index, style, codePoint) -> {
            glyphs.add(FormattedCharSequence.codepoint(codePoint, style.withColor((TextColor) null)));
            return true;
        });
        if (glyphs.isEmpty()) return;

        int width = Math.max(1, box.contentWidth());
        int height = Math.max(1, box.contentHeight());
        int advance = 0;

        for (FormattedCharSequence glyph : glyphs) {
            int glyphWidth = font.width(glyph);
            float middleX = line.x() + (advance + glyphWidth / 2.0f) * scale;
            float middleY = line.y() + font.lineHeight * scale / 2.0f;
            int color = fade(gradient.colorAt(gradient.positionOf(middleX, middleY, width, height)), alpha);

            graphics.text(font, glyph, x + advance, y, color, shadow);
            advance += glyphWidth;
        }
    }

    private static int fade(int color, float alpha){
        if (alpha >= 1.0f) return color;
        int faded = Math.round(((color >>> 24) & 0xFF) * alpha);
        return (color & 0x00FFFFFF) | (faded << 24);
    }
}
