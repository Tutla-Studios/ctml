package net.tutla.craftytml.client.ctml;

import net.tutla.craftytml.client.tusan.CTMLTokenType;

import java.util.List;

public class CTMLElement {
    public static final String name = "unidentified";
    public static final List<ElementProperty> propertyList = List.of(); //wierd naming convention cuz properties would eb for an instance not like the shi itself iywim
    public static final Boolean hasChildren = true;
    public static final CTMLTokenType tokenType = null;

    private List<ElementProperty> propertyValues;

    public void draw(){ // Draw Function

    };
}
