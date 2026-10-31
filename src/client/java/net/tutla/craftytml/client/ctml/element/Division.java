package net.tutla.craftytml.client.ctml.element;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * A plain box, and nothing on top of the common defaults, so it is the one every other element
 * varies from. Reach for it whenever nothing more particular is wanted.
 *
 * @ctml.group layout
 * @ctml.properties none
 * @ctml.example <div class="flex flex-col gap-2 p-2"></div>
 */
public class Division extends CTMLElement {
    public Division(){
        super("div", CTMLElementProperties.newElementProperties(), true, CTMLTokenType.DIV, CTMLTokenType.DIV_CLOSING);
    }
}
