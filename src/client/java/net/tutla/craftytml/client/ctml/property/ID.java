package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

public class ID extends ElementProperty {
    public ID(){
        super("id", ElementPropertyType.STRING, CTMLTokenType.ID);
    }

    public String getStringValue(){
        return (String) getValue();
    }
}
