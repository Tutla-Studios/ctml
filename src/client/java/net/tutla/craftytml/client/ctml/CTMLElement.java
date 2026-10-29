package net.tutla.craftytml.client.ctml;

import net.tutla.craftytml.client.ctml.property.ClassName;
import net.tutla.craftytml.client.ctml.style.TailwindParser;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.tusan.CTMLTokenType;
import net.tutla.tums.tusan.TusanContext;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

public abstract class CTMLElement {
    public final String name;
    public final CTMLElementProperties propertyList; //wierd naming convention cuz properties would eb for an instance not like the shi itself iywim
    private ElementStyle style = ElementStyle.UNSET; //not final like the property list cuz every style set swaps the whole thing out
    public final Boolean hasChildren;
    //private Boolean isFirstNode = false;

    public final CTMLTokenType tokenType;
    public final CTMLTokenType closingTokenType;

    public CTMLElement(String name, CTMLElementProperties propertyList, Boolean hasChildren, CTMLTokenType tokenType, CTMLTokenType closingTokenType){
        this.name = name;
        this.propertyList = propertyList;
        this.hasChildren = hasChildren;
        this.tokenType = tokenType;
        this.closingTokenType = closingTokenType;
    }

    /* public void makeFirstNode(){
        isFirstNode = true;
    }

    public Boolean isFirstNode(){
        return isFirstNode;
    }
     */

    public void updateProperty(String name, Object value, TusanContext ctx){
        propertyList.updateProperty(name, value, ctx);

        if (propertyList.propertyFromName(name) instanceof ClassName classes && classes.hasValue()){
            applyClassStyles(classes.getStringValue());
        }
    }

    public void applyClassStyles(String classList){
        ElementStyle.Builder parsed = ElementStyle.builder();
        List<String> unknown = TailwindParser.parseInto(parsed, classList);
        if (!unknown.isEmpty()) System.out.println("[CTML] <" + name + "> has classes nothing knows how to style: " + unknown);
        style = style.inherit(parsed.build());
    }

    public ElementStyle getStyle(){
        return style;
    }

    public void setStyle(ElementStyle style){
        this.style = style == null ? ElementStyle.UNSET : style;
    }

    public <T> void updateStyle(ElementStyle.Key<T> key, T value){
        style = style.with(key, value);
    }

    public void updateStyle(String cssName, Object value){
        style = style.toBuilder().set(cssName, value).build();
    }

    public CTMLElement newInstance(){
        try {
            return getClass().getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public DOMTreeElement newNode(){
        return new DOMTreeElement(newInstance());
    }
}
