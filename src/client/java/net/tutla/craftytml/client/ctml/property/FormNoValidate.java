package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * Read and kept like any other property, so a script can set and read it back, but nothing in ctml acts on it: there are no forms to submit.
 *
 * @ctml.on input
 * @ctml.example <input type="text" formnovalidate>
 */
public class FormNoValidate extends ElementProperty {
    public FormNoValidate(){
        super("formnovalidate", ElementPropertyType.BOOLEAN, CTMLTokenType.FORMNOVALIDATE);
    }

    public boolean isOn(){
        return Boolean.TRUE.equals(getValue());
    }
}
