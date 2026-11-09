package net.tutla.craftytml.client.tusan.register;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.property.*;
import net.tutla.tums.tusan.lexer.TokenType;

import java.util.ArrayList;
import java.util.HashMap;

public class PropertyRegistry {
    private static final ArrayList<ElementProperty> properties = new ArrayList<>();
    private static final HashMap<TokenType, ElementProperty> propertyMapping = new HashMap<>();

    public static void init(){
        register(new ClassName());
        register(new Text());
        register(new Draggable());
        register(new ID());
        register(new Callback());
        register(new ColSpan());
        register(new RowSpan());
        register(new ScrollX());
        register(new ScrollY());
        register(new ScrollInto());
        register(new Source());
        register(new TabIndex());
        register(new Start());
        register(new Reversed());
        register(new Value());
        register(new Type());
        register(new Placeholder());
        register(new Checked());
        register(new Name());
        register(new Min());
        register(new Max());
        register(new Step());
        register(new MaxLength());
        register(new Disabled());
        register(new ReadOnly());
        register(new Selected());
        register(new Alpha());
        register(new Required());
        register(new Autofocus());
        register(new MinLength());
        register(new Pattern());
        register(new Size());
        register(new Rows());
        register(new Cols());
        register(new Label());
        register(new Autocomplete());
        register(new InputMode());
        register(new Accept());
        register(new Capture());
        register(new Alt());
        register(new ListId());
        register(new Form());
        register(new FormAction());
        register(new FormEncType());
        register(new FormMethod());
        register(new FormTarget());
        register(new DirName());
        register(new Wrap());
        register(new Title());
        register(new Multiple());
        register(new SpellCheck());
        register(new FormNoValidate());
    }

    public static void register(ElementProperty property){
        properties.add(property);
        propertyMapping.put(property.tokenType, property);
    }

    public static ArrayList<ElementProperty> getRegister(){
        return properties;
    }

    public static ElementProperty getPropertyFromToken(TokenType tokenType){
        return propertyMapping.get(tokenType);
    }
}
