package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * How many characters wide a textarea starts out. A width class on the element wins over it.
 *
 * @ctml.on textarea
 * @ctml.example <textarea cols=30></textarea>
 */
public class Cols extends ElementProperty {
    public Cols(){
        super("cols", ElementPropertyType.INTEGER, CTMLTokenType.COLS);
    }

    public int getChars(){
        return getValue() instanceof Integer chars ? Math.max(1, chars) : 0;
    }
}
