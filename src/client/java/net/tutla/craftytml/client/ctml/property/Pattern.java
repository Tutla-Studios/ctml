package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * A regular expression the whole of a text field has to match to count as valid. One that does not compile is ignored. isValid on the element reports it.
 *
 * @ctml.on input
 * @ctml.example <input type="text" pattern="[a-z]+">
 */
public class Pattern extends ElementProperty {
    public Pattern(){
        super("pattern", ElementPropertyType.STRING, CTMLTokenType.PATTERN);
    }

    public String getStringValue(){
        return (String) getValue();
    }
}
