package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * Read and kept like any other property, so a script can set and read it back, but nothing in ctml acts on it: there is no datalist to suggest from.
 *
 * @ctml.on input
 * @ctml.example <input type="text" list="choices">
 */
public class ListId extends ElementProperty {
    public ListId(){
        super("list", ElementPropertyType.STRING, CTMLTokenType.LIST);
    }

    public String getStringValue(){
        return (String) getValue();
    }
}
