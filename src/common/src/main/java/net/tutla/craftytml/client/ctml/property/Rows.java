package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * How many lines tall a textarea starts out. A height class on the element wins over it.
 *
 * @ctml.on textarea
 * @ctml.example <textarea rows=4></textarea>
 */
public class Rows extends ElementProperty {
    public Rows(){
        super("rows", ElementPropertyType.INTEGER, CTMLTokenType.ROWS);
    }

    public int getLines(){
        return getValue() instanceof Integer lines ? Math.max(1, lines) : 0;
    }
}
