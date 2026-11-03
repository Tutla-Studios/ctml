package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * Read and kept like any other property, so a script can set and read it back, but nothing in ctml acts on it: a textarea never wraps, and scrolls sideways instead.
 *
 * @ctml.on textarea
 * @ctml.example <textarea wrap="soft"></textarea>
 */
public class Wrap extends ElementProperty {
    public Wrap(){
        super("wrap", ElementPropertyType.STRING, CTMLTokenType.WRAP);
    }

    public String getStringValue(){
        return (String) getValue();
    }
}
