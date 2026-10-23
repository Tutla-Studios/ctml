package net.tutla.craftytml.client.ctml.element;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.property.ClassName;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

import java.util.List;

public class Division extends CTMLElement {
    public Division(){
        super("div", List.of(new ClassName()), true, CTMLTokenType.DIV, CTMLTokenType.DIV_CLOSING);
    }

    public void draw(){

    }
}
