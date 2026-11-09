package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * Read and kept like any other property, so a script can set and read it back, but nothing in ctml acts on it: a select only ever takes one choice, and there is no file input.
 *
 * @ctml.on input, select
 * @ctml.example <select multiple></select>
 */
public class Multiple extends ElementProperty {
    public Multiple(){
        super("multiple", ElementPropertyType.BOOLEAN, CTMLTokenType.MULTIPLE);
    }

    public boolean isOn(){
        return Boolean.TRUE.equals(getValue());
    }
}
