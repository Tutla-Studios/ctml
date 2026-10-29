package net.tutla.craftytml.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.property.Text;
import net.tutla.craftytml.client.dom.DOMTreeElement;

public class CTMLRenderer {
    private static final int TEXT_COLOR = 0xFFFFFFFF; // no styles yet, so everything comes out plain white
    private static final int MARGIN = 4;

    public static void renderScreen(DOMTreeElement domTreeHeadElement, Screen screen, GuiGraphicsExtractor graphics){
        render(domTreeHeadElement, graphics, new Cursor(MARGIN, MARGIN));
    }

    public static void render(DOMTreeElement domHeadElement, GuiGraphicsExtractor graphics, Cursor cursor){
        ElementProperty text = domHeadElement.getProperty("text");

        if (text instanceof Text t && t.hasValue()){
            graphics.text(font(), t.getStringValue(), cursor.x, cursor.y, TEXT_COLOR);
            cursor.y += font().lineHeight; // every bit of text gets its own line for now
        }

        for (DOMTreeElement child : domHeadElement.getChildElements()) render(child, graphics, cursor);
    }

    private static Font font(){
        return Minecraft.getInstance().font;
    }

    public static class Cursor { // where the next bit of text lands, carried through the whole tree
        public int x;
        public int y;

        public Cursor(int x, int y){
            this.x = x;
            this.y = y;
        }
    }
}
