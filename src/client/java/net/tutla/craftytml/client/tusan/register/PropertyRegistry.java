package net.tutla.craftytml.client.tusan.register;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.property.ClassName;

import java.util.ArrayList;

public class PropertyRegistry {
    private static final ArrayList<ElementProperty> properties = new ArrayList<>();

    public static void init(){
        register(new ClassName());
    }

    public static void register(ElementProperty property){
        properties.add(property);
    }

    public static ArrayList<ElementProperty> getRegister(){
        return properties;
    }
}
