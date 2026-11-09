package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * The name of a function the host put in the dom context. It hears about everything that happens to
 * the element, not only the presses, so one that cares which is which asks the callback it is handed.
 *
 * @ctml.on any element
 * @ctml.example callback="clickCookie"
 */
public class Callback extends ElementProperty {
    public Callback(){
        super("callback", ElementPropertyType.STRING, CTMLTokenType.CALLBACK);
    }

    public String getStringValue(){
        return (String) getValue();
    }
}
