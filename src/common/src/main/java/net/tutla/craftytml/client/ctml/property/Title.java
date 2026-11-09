package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * Read and kept like any other property, so a script can set and read it back, but nothing in ctml acts on it: there are no tooltips.
 *
 * @ctml.on input, textarea, select
 * @ctml.example <input type="text" title="your name">
 */
public class Title extends ElementProperty {
    public Title(){
        super("title", ElementPropertyType.STRING, CTMLTokenType.TITLE);
    }

    public String getStringValue(){
        return (String) getValue();
    }
}
