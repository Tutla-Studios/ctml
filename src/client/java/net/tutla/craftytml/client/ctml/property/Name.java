package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * Which group a radio button belongs to: ticking one unticks every other radio button with the same name in the same screen.
 *
 * @ctml.on input
 * @ctml.example <input type="radio" name="colour" value="red">
 */
public class Name extends ElementProperty {
    public Name(){
        super("name", ElementPropertyType.STRING, CTMLTokenType.NAME);
    }

    public String getStringValue(){
        return (String) getValue();
    }
}
