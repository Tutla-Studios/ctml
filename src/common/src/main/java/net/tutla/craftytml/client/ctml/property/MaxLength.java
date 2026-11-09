package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * How many characters a text field takes. Typing and pasting stop at it; nothing is cut off a value that was written longer.
 *
 * @ctml.on input, textarea
 * @ctml.example <input type="text" maxlength=16>
 */
public class MaxLength extends ElementProperty {
    public MaxLength(){
        super("maxlength", ElementPropertyType.INTEGER, CTMLTokenType.MAXLENGTH);
    }

    /** The most characters allowed, or Integer.MAX_VALUE where no limit was written. */
    public int getLimit(){
        return getValue() instanceof Integer limit ? Math.max(0, limit) : Integer.MAX_VALUE;
    }
}
