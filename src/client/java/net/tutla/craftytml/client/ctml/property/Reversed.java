package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/** Whether an ordered list counts down instead of up. */
public class Reversed extends ElementProperty {
    public Reversed(){
        super("reversed", ElementPropertyType.BOOLEAN, CTMLTokenType.REVERSED);
    }

    public boolean isOn(){
        return Boolean.TRUE.equals(getValue());
    }
}
