package net.tutla.craftytml.client.tusan.lexer;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.element.Division;

import java.util.ArrayList;

public class ElementRegistry {
    private static final ArrayList<CTMLElement> elements = new ArrayList<>();

    public static void init(){
        register(new Division());
    }

    public static void register(CTMLElement element){
        elements.add(element);
    }

    public static ArrayList<CTMLElement> getRegister(){
        return elements;
    }
}
