package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * Lets a text field be focused, selected and read but not typed into.
 *
 * @ctml.on input, textarea
 * @ctml.example <input type="text" value="fixed" readonly>
 */
public class ReadOnly extends ElementProperty {
    public ReadOnly(){
        super("readonly", ElementPropertyType.BOOLEAN, CTMLTokenType.READONLY);
    }

    public boolean isOn(){
        return Boolean.TRUE.equals(getValue());
    }
}
