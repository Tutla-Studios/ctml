package net.tutla.craftytml.client.ctml.element;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

public class Division extends CTMLElement {
    public Division(){
        super("div", CTMLElementProperties.newElementProperties(), true, CTMLTokenType.DIV, CTMLTokenType.DIV_CLOSING);
    }
}
