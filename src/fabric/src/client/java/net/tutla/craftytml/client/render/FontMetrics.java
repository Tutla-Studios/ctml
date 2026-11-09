package net.tutla.craftytml.client.render;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.tutla.craftytml.client.ctml.ElementStyle;

/**
 * What layout asks about text and pictures, answered by minecraft's own font and the resource packs. It is
 * also where a laid out line turns back into the styled text the gui draws, so a line is drawn in exactly
 * the style it was measured in.
 */
public record FontMetrics(Font font) implements CTMLLayout.TextMetrics {
    @Override public int width(String text){ return font.width(text); }
    @Override public int width(String text, ElementStyle style){ return font.width(sequence(text, style)); }
    @Override public int lineHeight(){ return font.lineHeight; }
    @Override public int ascent(){ return CTMLLayout.FONT_ASCENT; }

    @Override
    public int[] pictureSize(String source){
        CTMLTextures.Texture texture = CTMLTextures.of(source);
        return texture != null && texture.isMeasured() ? new int[]{ texture.width(), texture.height() } : null;
    }

    public static FormattedCharSequence sequence(CTMLLayout.TextLine line){
        return sequence(line.content(), line.style());
    }

    public static FormattedCharSequence sequence(String content, ElementStyle style){
        return Component.literal(content).setStyle(textStyle(style)).getVisualOrderText();
    }


    public static Style textStyle(ElementStyle style){
        Style text = Style.EMPTY;
        if (style.isSet(ElementStyle.BOLD)) text = text.withBold(style.get(ElementStyle.BOLD));
        if (style.isSet(ElementStyle.ITALIC)) text = text.withItalic(style.get(ElementStyle.ITALIC));
        if (style.isSet(ElementStyle.UNDERLINE)) text = text.withUnderlined(style.get(ElementStyle.UNDERLINE));
        if (style.isSet(ElementStyle.STRIKETHROUGH)) text = text.withStrikethrough(style.get(ElementStyle.STRIKETHROUGH));
        return text;
    }
}
