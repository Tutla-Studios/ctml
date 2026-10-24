package net.tutla.craftytml.client.tusan.register;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.element.Division;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

import java.util.ArrayList;
import java.util.HashMap;

public class ElementRegistry {
    private static final ArrayList<CTMLElement> elements = new ArrayList<>();
    private static final HashMap<CTMLTokenType, CTMLElement> Token2ElementMap = new HashMap<>();

    public static void init(){
        register(new Division());
    }

    public static void register(CTMLElement element){
        elements.add(element);
        Token2ElementMap.put(element.tokenType, element);
    }

    public static ArrayList<CTMLElement> getRegister(){
        return elements;
    }

    public static CTMLElement getElementFromTokenType(CTMLTokenType tokenType){
        return Token2ElementMap.get(tokenType);
    }
}
