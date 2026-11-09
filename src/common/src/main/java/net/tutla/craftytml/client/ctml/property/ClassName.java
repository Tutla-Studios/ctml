package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * The tailwind-like class list the element is styled from. Reading it again replaces everything
 * the classes said before, which is what lets a callback show a box it had hidden; the property holds
 * the list as it stands, so adding or taking away one class is an edit to that string.
 *
 * @ctml.on any element
 * @ctml.example class="flex flex-col gap-2 bg-slate-800 hover:bg-slate-700"
 */
public class ClassName extends ElementProperty {
    public ClassName(){
        super("class", ElementPropertyType.STRING, CTMLTokenType.CLASS);
    }

    public String getStringValue(){
        return (String) getValue();
    }
}
