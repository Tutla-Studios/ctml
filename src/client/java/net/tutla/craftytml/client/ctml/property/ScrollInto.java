package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * The id of a descendant to bring into view, which is css's scrollIntoView. Set rather than read:
 * whatever lays the element out takes the value off it, scrolls there and leaves it empty again.
 */
public class ScrollInto extends ElementProperty {
    public ScrollInto(){
        super("scrollinto", ElementPropertyType.STRING, CTMLTokenType.SCROLLINTO);
    }

    public String getStringValue(){
        return (String) getValue();
    }
}
