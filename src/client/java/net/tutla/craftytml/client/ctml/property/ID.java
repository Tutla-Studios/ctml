package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * The name a script can find the element again by, which is what getElementById looks up. An element
 * given none gets one handed out in order the first time the context needs it.
 *
 * @ctml.on any element
 * @ctml.example id="cookie-counter"
 */
public class ID extends ElementProperty {
    public ID(){
        super("id", ElementPropertyType.STRING, CTMLTokenType.ID);
    }

    public String getStringValue(){
        return (String) getValue();
    }
}
