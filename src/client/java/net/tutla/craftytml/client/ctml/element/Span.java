package net.tutla.craftytml.client.ctml.element;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

public class Span extends CTMLElement {
    public Span(){
        super("span", CTMLElementProperties.newElementProperties(), true, CTMLTokenType.SPAN, CTMLTokenType.SPAN_CLOSING);
    }
}
