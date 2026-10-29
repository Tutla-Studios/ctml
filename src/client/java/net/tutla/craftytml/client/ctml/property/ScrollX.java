package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/** How far the content inside the element is scrolled sideways, which is how a script scrolls it. */
public class ScrollX extends ElementProperty {
    public ScrollX(){
        super("scrollx", ElementPropertyType.INTEGER, CTMLTokenType.SCROLLX);
    }

    public int getPixels(){
        return getValue() instanceof Integer pixels ? Math.max(0, pixels) : 0;
    }
}
