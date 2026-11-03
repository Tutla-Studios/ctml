package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * How many characters a text field has to hold to count as valid. Typing is never stopped by it; isValid on the element reports it.
 *
 * @ctml.on input, textarea
 * @ctml.example <input type="text" minlength=3>
 */
public class MinLength extends ElementProperty {
    public MinLength(){
        super("minlength", ElementPropertyType.INTEGER, CTMLTokenType.MINLENGTH);
    }

    public int getLimit(){
        return getValue() instanceof Integer limit ? Math.max(0, limit) : 0;
    }
}
