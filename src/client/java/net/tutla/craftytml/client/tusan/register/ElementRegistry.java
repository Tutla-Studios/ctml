package net.tutla.craftytml.client.tusan.register;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.element.*;
import net.tutla.craftytml.client.ctml.element.input.Button;
import net.tutla.craftytml.client.ctml.element.markdown.*;
import net.tutla.craftytml.client.ctml.element.markdown.font.*;
import net.tutla.craftytml.client.ctml.element.markdown.heading.*;
import net.tutla.craftytml.client.ctml.element.markdown.list.ListItem;
import net.tutla.craftytml.client.ctml.element.markdown.list.OrderedList;
import net.tutla.craftytml.client.ctml.element.markdown.list.UnorderedList;
import net.tutla.craftytml.client.ctml.element.markdown.paragraph.*;
import net.tutla.craftytml.client.ctml.element.markdown.table.*;
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
        register(new Img());
        register(new UnorderedList());
        register(new OrderedList());
        register(new ListItem());
        register(new Paragraph());
        register(new Rule());
        register(new LineBreak());
        register(new Bold());
        register(new Strong());
        register(new Italic());
        register(new Emphasis());
        register(new Strike());
        register(new Deleted());
        register(new Underline());
        register(new Inserted());
        register(new Code());
        register(new Preformatted());
        register(new BlockQuote());
        register(new Heading1());
        register(new Heading2());
        register(new Heading3());
        register(new Heading4());
        register(new Heading5());
        register(new Heading6());
        register(new Button());
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
