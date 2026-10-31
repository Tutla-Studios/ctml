package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * How far the content inside the element is scrolled down, which is how a script scrolls it.
 *
 * @ctml.on any element that scrolls
 * @ctml.example scrolly="120"
 */
public class ScrollY extends ElementProperty {
    public ScrollY(){
        super("scrolly", ElementPropertyType.INTEGER, CTMLTokenType.SCROLLY);
    }

    public int getPixels(){
        return getValue() instanceof Integer pixels ? Math.max(0, pixels) : 0;
    }
}
