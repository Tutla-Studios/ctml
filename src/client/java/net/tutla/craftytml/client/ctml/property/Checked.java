package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * Whether a checkbox or a radio button is ticked. It is rewritten as the user ticks it, so reading it back gives what is on screen.
 *
 * @ctml.on input
 * @ctml.example <input type="checkbox" checked>
 */
public class Checked extends ElementProperty {
    public Checked(){
        super("checked", ElementPropertyType.BOOLEAN, CTMLTokenType.CHECKED);
    }

    public boolean isOn(){
        return Boolean.TRUE.equals(getValue());
    }
}
