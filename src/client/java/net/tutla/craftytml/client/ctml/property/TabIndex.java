package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * Where the element comes in the tab order, the way html's tabindex does it: zero puts it in at its
 * place in the markup, a positive number puts it ahead of those in that number's order, and below
 * zero takes it out of the order while leaving it something a press can still focus.
 */
public class TabIndex extends ElementProperty {
    /** What an element with nothing said about it gets, so "has one" and "is zero" are different things. */
    public static final int UNSET = Integer.MIN_VALUE;

    public TabIndex(){
        super("tabindex", ElementPropertyType.INTEGER, CTMLTokenType.TABINDEX);
    }

    public int getIndex(){
        return getValue() instanceof Integer index ? index : UNSET;
    }
}
