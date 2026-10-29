package net.tutla.craftytml.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
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

    /** z-index decides what covers what; everything else keeps the order layout handed over. */
    static List<CTMLLayout.Box> paintOrder(List<CTMLLayout.Box> children){
        if (children.size() < 2) return children;

        boolean layered = false;
        for (CTMLLayout.Box child : children) layered |= child.style().isSet(ElementStyle.Z_INDEX);
        if (!layered) return children;

        List<CTMLLayout.Box> sorted = new ArrayList<>(children);
        sorted.sort(Comparator.comparingInt(child -> child.style().get(ElementStyle.Z_INDEX, 0))); // stable, so ties stay put
        return sorted;
    }

    private static void background(CTMLLayout.Box box, GuiGraphicsExtractor graphics, float alpha){
        Gradient gradient = box.style().get(ElementStyle.BACKGROUND_GRADIENT);
        if (gradient != null && gradient.isDrawable()) {
            gradient(graphics, gradient, box.x(), box.y(), box.width(), box.height(), alpha);
            return;
        }

        Integer color = box.style().get(ElementStyle.BACKGROUND_COLOR);
        if (color == null) return;

        rounded(graphics, box.x(), box.y(), box.width(), box.height(),
                box.style().get(ElementStyle.BORDER_RADIUS, 0), fade(color, alpha));
    }

    /** A fill with its corners cut off, since there is no rounded rect to draw with. */
    private static void rounded(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int color){
        if (width <= 0 || height <= 0 || (color >>> 24) == 0) return;

        radius = Math.min(radius, Math.min(width, height) / 2);
        if (radius <= 0) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }

        graphics.fill(x, y + radius, x + width, y + height - radius, color);
        graphics.fill(x + radius, y, x + width - radius, y + radius, color);
        graphics.fill(x + radius, y + height - radius, x + width - radius, y + height, color);
    }

    /**
     * A linear gradient, drawn as one strip per pixel along the axis it runs down.
     * Straight vertical ones go through minecraft's own two colour fill, which is the same thing
     * in a single draw; the diagonals are drawn as horizontal, since a real one needs per pixel work.
     */
    private static void gradient(GuiGraphicsExtractor graphics, Gradient gradient, int x, int y, int width, int height, float alpha){
        if (width <= 0 || height <= 0) return;

        if (gradient.direction().isVertical()) {
            List<Integer> stops = gradient.stops();
            int segments = stops.size() - 1;
            for (int segment = 0; segment < segments; segment++) {
                int top = y + height * segment / segments;
                int bottom = y + height * (segment + 1) / segments;
                float from = segment / (float) segments;
                float to = (segment + 1) / (float) segments;
                graphics.fillGradient(x, top, x + width, bottom,
                        fade(gradient.colorAt(from), alpha), fade(gradient.colorAt(to), alpha));
            }
            return;
        }

        for (int offset = 0; offset < width; offset++) {
            int color = fade(gradient.colorAt((offset + 0.5f) / width), alpha);
            graphics.fill(x + offset, y, x + offset + 1, y + height, color);
        }
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
        Gradient gradient = style.get(ElementStyle.TEXT_GRADIENT);
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
     * Gradient text, drawn a character at a time so each one can take its own colour off the ramp.
     * The ramp is measured across the whole content box, not the line, so every line of a block agrees.
     * Drawing is already inside the scale matrix, so the advances here are unscaled.
     */
    private static void gradientText(GuiGraphicsExtractor graphics, Font font, CTMLLayout.TextLine line, Gradient gradient,
                                     CTMLLayout.Box box, int x, int y, float scale, float alpha, boolean shadow){
        String content = line.content();
        if (content.isEmpty()) return;

        boolean vertical = gradient.direction().isVertical();
        int span = Math.max(1, vertical ? box.contentHeight() : box.contentWidth());
        int advance = 0;

        for (int i = 0; i < content.length(); ) {
            int codePoint = content.codePointAt(i);
            String glyph = new String(Character.toChars(codePoint));
            i += Character.charCount(codePoint);

            int width = font.width(glyph);
            int center = Math.round((line.x() + (advance + width / 2.0f) * scale));
            float t = vertical ? (line.y() + font.lineHeight * scale / 2.0f) / span : center / (float) span;

            graphics.text(font, glyph, x + advance, y, fade(gradient.colorAt(t), alpha), shadow);
            advance += width;
        }
    }

    private static int fade(int color, float alpha){
        if (alpha >= 1.0f) return color;
        int faded = Math.round(((color >>> 24) & 0xFF) * alpha);
        return (color & 0x00FFFFFF) | (faded << 24);
    }
}
