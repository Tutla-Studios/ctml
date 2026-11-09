package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * How far one nudge moves a number or a range: the arrow keys, and where a dragged range lands. It is one unless this says so, and always a whole number.
 *
 * @ctml.on input
 * @ctml.example <input type="range" min=0 max=100 step=10>
 */
public class Step extends ElementProperty {
    public Step(){
        super("step", ElementPropertyType.INTEGER, CTMLTokenType.STEP);
    }

    /** The step written, never less than one. */
    public int getAmount(){
        return getValue() instanceof Integer amount ? Math.max(1, amount) : 1;
    }
}
