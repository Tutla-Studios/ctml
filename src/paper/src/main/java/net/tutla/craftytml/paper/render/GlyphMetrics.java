package net.tutla.craftytml.paper.render;

import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.render.CTMLLayout;

/**
 * Text measured the way the vanilla font draws it, for a server, which has no font to ask. A text display
 * is drawn by the client in its own font, so laying a tree out against these widths puts the text where
 * the client will draw it.
 *
 * <p>The widths are the advances of minecraft's default ascii glyphs, the gap after each included. Bold
 * draws every glyph a pixel wider. Anything past ascii is taken as six pixels, which is what most of the
 * latin glyphs are; a resource pack that changes the font changes what the client draws but not this.
 */
final class GlyphMetrics implements CTMLLayout.TextMetrics {
    static final GlyphMetrics VANILLA = new GlyphMetrics();

    private static final int LINE_HEIGHT = 9;
    private static final int DEFAULT_ADVANCE = 6;
    private static final int[] ASCII = new int[128];

    static {
        java.util.Arrays.fill(ASCII, 32, 127, DEFAULT_ADVANCE);
        set(4, " I[]t(){}*\"");
        set(2, "!',.:;i|");
        set(3, "`l");
        set(5, "<>fk");
        set(7, "@~");
    }

    private static void set(int advance, String glyphs){
        for (char glyph : glyphs.toCharArray()) ASCII[glyph] = advance;
    }

    private GlyphMetrics(){}

    @Override
    public int width(String text){
        return measure(text, false);
    }

    @Override
    public int width(String text, ElementStyle style){
        return measure(text, style.get(ElementStyle.BOLD, false));
    }

    @Override
    public int lineHeight(){
        return LINE_HEIGHT;
    }

    @Override
    public int ascent(){
        return CTMLLayout.FONT_ASCENT;
    }

    private static int measure(String text, boolean bold){
        int width = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            width += advance(codePoint, bold);
        }
        return width;
    }

    /** How far one glyph moves the pen, which is nothing for a control character. */
    static int advance(int codePoint, boolean bold){
        int advance = codePoint < ASCII.length ? ASCII[codePoint] : DEFAULT_ADVANCE;
        return advance == 0 ? 0 : advance + (bold ? 1 : 0);
    }
}
