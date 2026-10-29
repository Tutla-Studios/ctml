package net.tutla.craftytml.client.ctml.element;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

public class CTML extends CTMLElement {
    public CTML(){
        super("ctml", CTMLElementProperties.newElementProperties(), true, CTMLTokenType.CTML, CTMLTokenType.CTML_CLOSING);
    }
}
