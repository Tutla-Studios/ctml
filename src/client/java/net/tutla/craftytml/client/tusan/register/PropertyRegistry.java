package net.tutla.craftytml.client.tusan.register;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.property.*;
import net.tutla.tums.tusan.lexer.TokenType;

import java.util.ArrayList;
import java.util.HashMap;

public class PropertyRegistry {
    private static final ArrayList<ElementProperty> properties = new ArrayList<>();
    private static final HashMap<TokenType, ElementProperty> propertyMapping = new HashMap<>();

    public static void init(){
        register(new ClassName());
        register(new Text());
        register(new Draggable());
        register(new ID());
        register(new Callback());
        register(new ColSpan());
        register(new RowSpan());
        register(new ScrollX());
        register(new ScrollY());
        register(new ScrollInto());
    }

    public static void register(ElementProperty property){
        properties.add(property);
        propertyMapping.put(property.tokenType, property);
    }

    public static ArrayList<ElementProperty> getRegister(){
        return properties;
    }

    public static ElementProperty getPropertyFromToken(TokenType tokenType){
        return propertyMapping.get(tokenType);
    }
}
