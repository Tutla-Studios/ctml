package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * Says the control has to be filled in. Nothing refuses to submit, there being no forms, but isValid on the element reports it.
 *
 * @ctml.on input, textarea, select
 * @ctml.example <input type="text" required>
 */
public class Required extends ElementProperty {
    public Required(){
        super("required", ElementPropertyType.BOOLEAN, CTMLTokenType.REQUIRED);
    }

    public boolean isOn(){
        return Boolean.TRUE.equals(getValue());
    }
}
