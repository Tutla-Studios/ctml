package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * Read and kept like any other property, so a script can set and read it back, but nothing in ctml acts on it: there are no forms to submit.
 *
 * @ctml.on input
 * @ctml.example <input type="text" formtarget="_blank">
 */
public class FormTarget extends ElementProperty {
    public FormTarget(){
        super("formtarget", ElementPropertyType.STRING, CTMLTokenType.FORMTARGET);
    }

    public String getStringValue(){
        return (String) getValue();
    }
}
