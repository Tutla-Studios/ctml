package net.tutla.craftytml.client.tusan.register;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.element.*;
import net.tutla.craftytml.client.tusan.CTMLTokenType;
import net.tutla.tums.tusan.lexer.TokenType;

import java.util.ArrayList;
import java.util.HashMap;

public class ElementRegistry {
    private static final ArrayList<CTMLElement> elements = new ArrayList<>();
    private static final HashMap<TokenType, CTMLElement> Token2ElementMap = new HashMap<>();

    public static void init(){
        register(new Division());
        register(new CTML());
        register(new Span());
        register(new Table());
        register(new TableHead());
        register(new TableBody());
        register(new TableFoot());
        register(new Caption());
        register(new TableRow());
        register(new TableHeaderCell());
        register(new TableCell());
    }

    public static void register(CTMLElement element){
        elements.add(element);
        Token2ElementMap.put(element.tokenType, element);
    }

    public static ArrayList<CTMLElement> getRegister(){
        return elements;
    }

    public static CTMLElement getElementFromTokenType(TokenType tokenType){
        return Token2ElementMap.get(tokenType);
    }
}
