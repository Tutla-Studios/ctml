package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * Read and kept like any other property, so a script can set and read it back, but nothing in ctml acts on it: there is no on screen keyboard to pick a layout for.
 *
 * @ctml.on input, textarea
 * @ctml.example <input type="text" inputmode="numeric">
 */
public class InputMode extends ElementProperty {
    public InputMode(){
        super("inputmode", ElementPropertyType.STRING, CTMLTokenType.INPUTMODE);
    }

    public String getStringValue(){
        return (String) getValue();
    }
}
