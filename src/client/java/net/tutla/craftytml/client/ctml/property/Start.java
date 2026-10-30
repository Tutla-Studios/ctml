package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/** What an ordered list counts from, which is one unless it says otherwise. */
public class Start extends ElementProperty {
    public Start(){
        super("start", ElementPropertyType.INTEGER, CTMLTokenType.START);
    }

    /** What the list counts from, or one where nothing says. */
    public int getFirst(){
        return getValue() instanceof Integer first ? first : 1;
    }
}
