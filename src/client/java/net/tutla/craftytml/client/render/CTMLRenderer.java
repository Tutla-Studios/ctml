package net.tutla.craftytml.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.dom.DOMTreeElement;

public class CTMLRenderer {
    private static final int DEFAULT_TEXT_COLOR = 0xFFFFFFFF;
    private static final int DEFAULT_BORDER_COLOR = 0xFFFFFFFF;

    public static void renderScreen(DOMTreeElement domTreeHeadElement, Screen screen, GuiGraphicsExtractor graphics){
        render(domTreeHeadElement, graphics, screen.width, screen.height);
    }

    public static void render(DOMTreeElement domHeadElement, GuiGraphicsExtractor graphics, int viewWidth, int viewHeight){
        Font font = Minecraft.getInstance().font;
        CTMLLayout.Box root = new CTMLLayout(font, viewWidth, viewHeight).layout(domHeadElement, 0, 0, viewWidth, viewHeight);
        if (root != null) paint(root, graphics, font, 1.0f);
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

        boolean clipped = style.get(ElementStyle.OVERFLOW, ElementStyle.Overflow.VISIBLE) != ElementStyle.Overflow.VISIBLE;
        if (clipped) graphics.enableScissor(box.contentX(), box.contentY(), box.contentRight(), box.contentBottom());

        if (!hidden) text(box, graphics, font, alpha);
        for (CTMLLayout.Box child : box.children()) paint(child, graphics, font, alpha);

        if (clipped) graphics.disableScissor();
    }

    private static void background(CTMLLayout.Box box, GuiGraphicsExtractor graphics, float alpha){
        Integer color = box.style().get(ElementStyle.BACKGROUND_COLOR);
        if (color == null) return;

        int radius = Math.min(box.style().get(ElementStyle.BORDER_RADIUS, 0), Math.min(box.width(), box.height()) / 2);
        int filled = fade(color, alpha);

        if (radius <= 0) {
            graphics.fill(box.x(), box.y(), box.right(), box.bottom(), filled);
            return;
        }

        // no rounded fill to draw with, so the corners just get cut off instead
        graphics.fill(box.x(), box.y() + radius, box.right(), box.bottom() - radius, filled);
        graphics.fill(box.x() + radius, box.y(), box.right() - radius, box.y() + radius, filled);
        graphics.fill(box.x() + radius, box.bottom() - radius, box.right() - radius, box.bottom(), filled);
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

        for (CTMLLayout.TextLine line : box.lines()) {
            int x = box.contentX() + line.x();
            int y = box.contentY() + line.y();

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

    private static int fade(int color, float alpha){
        if (alpha >= 1.0f) return color;
        int faded = Math.round(((color >>> 24) & 0xFF) * alpha);
        return (color & 0x00FFFFFF) | (faded << 24);
    }
}
