package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * Marks the option a select starts on. The first option is the one chosen where none says so.
 *
 * @ctml.on option
 * @ctml.example <option value="b" selected text="Bee"></option>
 */
public class Selected extends ElementProperty {
    public Selected(){
        super("selected", ElementPropertyType.BOOLEAN, CTMLTokenType.SELECTED);
    }

    public boolean isOn(){
        return Boolean.TRUE.equals(getValue());
    }
}
