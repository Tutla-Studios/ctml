package net.tutla.craftytml.client.ctml.element;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.ctml.property.ClassName;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

import java.util.List;

public class Division extends CTMLElement {
    public Division(){
        super("div", CTMLElementProperties.newElementProperties(), true, CTMLTokenType.DIV, CTMLTokenType.DIV_CLOSING);
    }

    public void draw(){

    }
}
