package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

public class Callback extends ElementProperty {
    public Callback(){
        super("callback", ElementPropertyType.STRING, CTMLTokenType.CALLBACK);
    }

    public String getStringValue(){
        return (String) getValue();
    }
}
