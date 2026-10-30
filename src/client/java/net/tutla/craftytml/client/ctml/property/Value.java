package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/** The number of one list item, which the items after it carry on from. */
public class Value extends ElementProperty {
    public Value(){
        super("value", ElementPropertyType.INTEGER, CTMLTokenType.VALUE);
    }

    /** The number this item was given, or Integer.MIN_VALUE where it was given none. */
    public int getNumber(){
        return getValue() instanceof Integer number ? number : Integer.MIN_VALUE;
    }
}
