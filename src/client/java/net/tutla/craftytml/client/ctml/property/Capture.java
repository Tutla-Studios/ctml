package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * Read and kept like any other property, so a script can set and read it back, but nothing in ctml acts on it: there is no file input to capture into.
 *
 * @ctml.on input
 * @ctml.example <input type="text" capture="user">
 */
public class Capture extends ElementProperty {
    public Capture(){
        super("capture", ElementPropertyType.STRING, CTMLTokenType.CAPTURE);
    }

    public String getStringValue(){
        return (String) getValue();
    }
}
