package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * Read and kept like any other property, so a script can set and read it back, but nothing in ctml acts on it: there is no autofill to switch off.
 *
 * @ctml.on input, textarea, select
 * @ctml.example <input type="text" autocomplete="off">
 */
public class Autocomplete extends ElementProperty {
    public Autocomplete(){
        super("autocomplete", ElementPropertyType.STRING, CTMLTokenType.AUTOCOMPLETE);
    }

    public String getStringValue(){
        return (String) getValue();
    }
}
