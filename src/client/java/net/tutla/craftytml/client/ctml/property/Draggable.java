package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/** Lets the element be dragged around inside its parent's content box. Just the name is enough: <div draggable>. */
public class Draggable extends ElementProperty {
    public Draggable(){
        super("draggable", ElementPropertyType.BOOLEAN, CTMLTokenType.DRAGGABLE);
    }

    public boolean isOn(){
        return Boolean.TRUE.equals(getValue());
    }
}
