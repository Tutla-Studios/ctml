package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * The words the element shows. It is read through the dom context, so ${name} anywhere in it is the
 * variable of that name as it stands this frame.
 *
 * @ctml.on any element
 * @ctml.example text="fps ${fps}"
 */
public class Text extends ElementProperty {
    public Text(){
        super("text", ElementPropertyType.STRING, CTMLTokenType.TEXT);
    }

    public String getStringValue(){
        return (String) getValue();
    }
}
