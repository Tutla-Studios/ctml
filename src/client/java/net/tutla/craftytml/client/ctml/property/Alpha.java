package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * Lets a colour input carry an opacity. Its value is then written #rrggbbaa, its picker gets an opacity
 * bar under the other ways of choosing, and its swatch is drawn over a chequerboard so it can be seen
 * through. Without it a colour is #rrggbb and fully opaque, the way html has it.
 *
 * @ctml.on input
 * @ctml.example <input type="color" alpha value="#38bdf880">
 */
public class Alpha extends ElementProperty {
    public Alpha(){
        super("alpha", ElementPropertyType.BOOLEAN, CTMLTokenType.ALPHA);
    }

    public boolean isOn(){
        return Boolean.TRUE.equals(getValue());
    }
}
