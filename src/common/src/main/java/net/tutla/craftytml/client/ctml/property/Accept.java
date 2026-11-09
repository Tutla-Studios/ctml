package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * Read and kept like any other property, so a script can set and read it back, but nothing in ctml acts on it: there is no file input to filter.
 *
 * @ctml.on input
 * @ctml.example <input type="text" accept=".png">
 */
public class Accept extends ElementProperty {
    public Accept(){
        super("accept", ElementPropertyType.STRING, CTMLTokenType.ACCEPT);
    }

    public String getStringValue(){
        return (String) getValue();
    }
}
