package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * The words an option shows, ahead of its text property and the words between its tags.
 *
 * @ctml.on option
 * @ctml.example <option value="a" label="Apple"></option>
 */
public class Label extends ElementProperty {
    public Label(){
        super("label", ElementPropertyType.STRING, CTMLTokenType.LABEL);
    }

    public String getStringValue(){
        return (String) getValue();
    }
}
