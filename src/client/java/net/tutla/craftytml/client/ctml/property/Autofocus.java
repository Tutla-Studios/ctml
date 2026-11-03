package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * Gives the control the keyboard when the screen opens. Where several say so, the first in the markup gets it.
 *
 * @ctml.on input, textarea, select
 * @ctml.example <input type="text" autofocus>
 */
public class Autofocus extends ElementProperty {
    public Autofocus(){
        super("autofocus", ElementPropertyType.BOOLEAN, CTMLTokenType.AUTOFOCUS);
    }

    public boolean isOn(){
        return Boolean.TRUE.equals(getValue());
    }
}
