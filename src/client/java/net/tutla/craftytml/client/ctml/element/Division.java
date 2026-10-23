package net.tutla.craftytml.client.ctml.element;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.property.ClassName;

import java.util.List;

public class Division extends CTMLElement {
    public static final String name = "div";
    public static final List<ElementProperty> propertyList = List.of(new ClassName());

    public void draw(){

    }
}
