package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * The dimmed words shown in a text field while it holds nothing.
 *
 * @ctml.on input, textarea
 * @ctml.example <input type="text" placeholder="your name">
 */
public class Placeholder extends ElementProperty {
    public Placeholder(){
        super("placeholder", ElementPropertyType.STRING, CTMLTokenType.PLACEHOLDER);
    }

    public String getStringValue(){
        return (String) getValue();
    }
}
