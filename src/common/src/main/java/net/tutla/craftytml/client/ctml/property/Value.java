package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;
import net.tutla.tums.tusan.TusanContext;

/**
 * What an element holds. On a list item it is the number of that item, which the items after it carry
 * on from; on an input, a textarea or an option it is the text, the number or the choice the element
 * stands for, and it is rewritten as the user changes it, so reading it back gives what is on screen.
 *
 * <p>It is kept as text whatever it was written as, so value=7 and value="7" are the same thing.
 *
 * @ctml.on li, input, textarea, select, option
 * @ctml.example <input type="text" value="hello">
 */
public class Value extends ElementProperty {
    public Value(){
        super("value", ElementPropertyType.STRING, CTMLTokenType.VALUE);
    }

    @Override
    public void setValue(Object val, TusanContext ctx){
        // every number a script reads is a decimal, so a whole one is written without its ".0"
        if (val instanceof Number number) {
            double each = number.doubleValue();
            val = each == Math.rint(each) && Math.abs(each) < 1e15 ? String.valueOf((long) each) : String.valueOf(each);
        }
        super.setValue(val, ctx);
    }

    /** The text the element holds, or an empty string where it was given none. */
    public String getText(){
        return getValue() instanceof String text ? text : "";
    }

    /** The number this item was given, or Integer.MIN_VALUE where it was given none or none that reads as one. */
    public int getNumber(){
        if (!(getValue() instanceof String text)) return Integer.MIN_VALUE;
        try {
            return (int) Math.round(Double.parseDouble(text.strip()));
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }
}
