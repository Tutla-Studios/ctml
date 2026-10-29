package net.tutla.craftytml.client.tusan.register;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.property.ClassName;
import net.tutla.craftytml.client.ctml.property.Text;
import net.tutla.tums.tusan.lexer.TokenType;

import java.util.ArrayList;
import java.util.HashMap;

public class PropertyRegistry {
    private static final ArrayList<ElementProperty> properties = new ArrayList<>();
    private static final HashMap<TokenType, ElementProperty> propertyMapping = new HashMap<>();

    public static void init(){
        register(new ClassName());
        register(new Text());
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
