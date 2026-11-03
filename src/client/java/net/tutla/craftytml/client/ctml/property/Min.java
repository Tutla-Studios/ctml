package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * The lowest a number or a range can go. A range starts at zero, and a number has no lowest unless this says so.
 *
 * @ctml.on input
 * @ctml.example <input type="range" min=0 max=10>
 */
public class Min extends ElementProperty {
    public Min(){
        super("min", ElementPropertyType.INTEGER, CTMLTokenType.MIN);
    }

    /** The lowest value written, or the fallback where none was. */
    public int getLimit(int fallback){
        return getValue() instanceof Integer limit ? limit : fallback;
    }
}
