package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

public class Text extends ElementProperty {
    public Text(){
        super("text", ElementPropertyType.STRING, CTMLTokenType.TEXT);
    }

    public String getStringValue(){
        return (String) getValue();
    }
}
