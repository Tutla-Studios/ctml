package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * What kind of input an input is: text, password, email, search, url, tel, number, checkbox, radio, range, hidden, button, submit or reset. Submit and reset are buttons, there being no forms for them to send or clear. One it does not know is read as text.
 *
 * @ctml.on input
 * @ctml.example <input type="checkbox">
 */
public class Type extends ElementProperty {
    public Type(){
        super("type", ElementPropertyType.STRING, CTMLTokenType.TYPE);
    }

    /** The kind written on the element, lower case, or "text" where it says none. */
    public String getKind(){
        return getValue() instanceof String kind ? kind.strip().toLowerCase(java.util.Locale.ROOT) : "text";
    }
}
