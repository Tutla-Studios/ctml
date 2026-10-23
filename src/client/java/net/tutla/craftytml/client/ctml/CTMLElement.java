package net.tutla.craftytml.client.ctml;

import net.tutla.craftytml.client.tusan.CTMLTokenType;

import java.util.List;

public class CTMLElement {
    public final String name;
    public final List<ElementProperty> propertyList; //wierd naming convention cuz properties would eb for an instance not like the shi itself iywim
    public final Boolean hasChildren;
    public final CTMLTokenType tokenType;
    public final CTMLTokenType closingTokenType;

    private List<ElementProperty> propertyValues;

    public CTMLElement(String name, List<ElementProperty> propertyList, Boolean hasChildren, CTMLTokenType tokenType, CTMLTokenType closingTokenType){
        this.name = name;
        this.propertyList = propertyList;
        this.hasChildren = hasChildren;
        this.tokenType = tokenType;
        this.closingTokenType = closingTokenType;
    }

    public void draw(){ // Draw Function

    };
}
