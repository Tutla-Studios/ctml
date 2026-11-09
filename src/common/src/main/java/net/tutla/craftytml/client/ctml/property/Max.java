package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * The highest a number or a range can go. A range stops at a hundred, and a number has no highest unless this says so.
 *
 * @ctml.on input
 * @ctml.example <input type="range" min=0 max=10>
 */
public class Max extends ElementProperty {
    public Max(){
        super("max", ElementPropertyType.INTEGER, CTMLTokenType.MAX);
    }

    /** The highest value written, or the fallback where none was. */
    public int getLimit(int fallback){
        return getValue() instanceof Integer limit ? limit : fallback;
    }
}
