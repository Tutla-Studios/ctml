package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

public class ClassName extends ElementProperty {
    public ClassName(){
        super("class", ElementPropertyType.STRING, CTMLTokenType.CLASS);
    }
}
