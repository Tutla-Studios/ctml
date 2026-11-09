package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * Takes the element out of use: it cannot be focused, typed into or pressed, and is drawn dimmed. On an option it cannot be chosen.
 *
 * @ctml.on input, textarea, select, option
 * @ctml.example <input type="text" disabled>
 */
public class Disabled extends ElementProperty {
    public Disabled(){
        super("disabled", ElementPropertyType.BOOLEAN, CTMLTokenType.DISABLED);
    }

    public boolean isOn(){
        return Boolean.TRUE.equals(getValue());
    }
}
