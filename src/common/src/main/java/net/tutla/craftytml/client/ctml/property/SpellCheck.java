package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * Read and kept like any other property, so a script can set and read it back, but nothing in ctml acts on it: there is no spell checker.
 *
 * @ctml.on input, textarea
 * @ctml.example <input type="text" spellcheck=false>
 */
public class SpellCheck extends ElementProperty {
    public SpellCheck(){
        super("spellcheck", ElementPropertyType.BOOLEAN, CTMLTokenType.SPELLCHECK);
    }

    public boolean isOn(){
        return Boolean.TRUE.equals(getValue());
    }
}
