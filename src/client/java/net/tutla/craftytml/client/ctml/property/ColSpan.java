package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/** How many columns of its table a cell takes up. It belongs to the cell, not to how it looks. */
public class ColSpan extends ElementProperty {
    public ColSpan(){
        super("colspan", ElementPropertyType.INTEGER, CTMLTokenType.COLSPAN);
    }

    public int getSpan(){
        return getValue() instanceof Integer span && span > 0 ? span : 1;
    }
}
