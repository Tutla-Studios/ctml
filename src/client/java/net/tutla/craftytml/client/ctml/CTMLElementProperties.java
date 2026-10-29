package net.tutla.craftytml.client.ctml;

import net.tutla.craftytml.client.tusan.register.PropertyRegistry;
import net.tutla.tums.tusan.TusanContext;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class CTMLElementProperties {
    private final Map<String, ElementProperty> mapping = new LinkedHashMap<>();

    public void updateProperty(String name, Object value, TusanContext ctx){
        ElementProperty e = propertyFromName(name);
        e.setValue(value, ctx);
        mapping.put(name, e);
    }

    public void putProperty(String name, ElementProperty property){
        mapping.put(name, property);
    }

    public ElementProperty propertyFromName(String name){
        return mapping.get(name);
    }

    public Map<String, ElementProperty> getProperties(){
        return Collections.unmodifiableMap(mapping);
    }

    public Map<String, ElementProperty> getAssignedProperties(){
        Map<String, ElementProperty> assigned = new LinkedHashMap<>();
        mapping.forEach((name, property) -> {
            if (property.hasValue()) assigned.put(name, property);
        });
        return assigned;
    }

    public static CTMLElementProperties newElementProperties(){
        CTMLElementProperties properties = new CTMLElementProperties();
        PropertyRegistry.getRegister().forEach(property -> {
            try {
                properties.putProperty(property.name, property.getClass().getDeclaredConstructor().newInstance());
            } catch (InstantiationException | InvocationTargetException | NoSuchMethodException |
                     IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        });

        return properties;
    }
}
