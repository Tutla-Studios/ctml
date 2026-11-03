package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * Read and kept like any other property, so a script can set and read it back, but nothing in ctml acts on it: there is no image input to describe.
 *
 * @ctml.on input
 * @ctml.example <input type="text" alt="a picture">
 */
public class Alt extends ElementProperty {
    public Alt(){
        super("alt", ElementPropertyType.STRING, CTMLTokenType.ALT);
    }

    public String getStringValue(){
        return (String) getValue();
    }
}
