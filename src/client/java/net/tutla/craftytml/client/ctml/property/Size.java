package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * How wide a text field starts out, in characters. A width class on the element wins over it.
 *
 * @ctml.on input
 * @ctml.example <input type="text" size=20>
 */
public class Size extends ElementProperty {
    public Size(){
        super("size", ElementPropertyType.INTEGER, CTMLTokenType.SIZE);
    }

    public int getChars(){
        return getValue() instanceof Integer chars ? Math.max(1, chars) : 0;
    }
}
