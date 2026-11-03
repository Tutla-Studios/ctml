package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * Read and kept like any other property, so a script can set and read it back, but nothing in ctml acts on it: there are no forms to submit.
 *
 * @ctml.on input, textarea
 * @ctml.example <input type="text" dirname="side">
 */
public class DirName extends ElementProperty {
    public DirName(){
        super("dirname", ElementPropertyType.STRING, CTMLTokenType.DIRNAME);
    }

    public String getStringValue(){
        return (String) getValue();
    }
}
