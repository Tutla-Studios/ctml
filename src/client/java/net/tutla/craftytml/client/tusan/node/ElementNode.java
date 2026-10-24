package net.tutla.craftytml.client.tusan.node;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.ctml.element.Division;
import net.tutla.craftytml.client.tusan.CTMLTokenGroup;
import net.tutla.craftytml.client.tusan.register.ElementRegistry;
import net.tutla.craftytml.client.tusan.register.NodeRegistry;
import net.tutla.craftytml.client.tusan.register.PropertyRegistry;
import net.tutla.tums.tusan.TusanContext;
import net.tutla.tums.tusan.lexer.util.prebuilt.PrebuiltTusanTokenType;
import net.tutla.tums.tusan.node.Node;
import net.tutla.tums.tusan.node.NodeConfig;
import net.tutla.tums.tusan.nodes.expression.Expression;

import java.lang.reflect.InvocationTargetException;

public class ElementNode extends Node {
    public ElementNode(TusanContext ctx) {
        super(ctx);
    }

    @Override
    public Object create() {
        //Object value = false;
        if (interpreter.end){
            return false;
        }

        if (ctx.getCurrentToken().group != CTMLTokenGroup.ELEMENT && ctx.getCurrentToken().type != PrebuiltTusanTokenType.STRING){ // avoid receiving non-elements or raw text
            ctx.nextToken();
            return false;
        }
        ElementRegistry.getRegister().forEach(_element -> {
            if (_element.tokenType == ctx.getCurrentToken().type){
                try {
                    CTMLElement element = _element.getClass().getDeclaredConstructor().newInstance();
                    while (ctx.getNextToken().group == CTMLTokenGroup.PROPERTY){
                        ctx.nextToken();
                        ElementProperty property = PropertyRegistry.getPropertyFromToken(ctx.getCurrentToken().type);
                        Object value;
                        if (property.type != ElementPropertyType.BOOLEAN){
                            ctx.tokenContext.expectTokenType(PrebuiltTusanTokenType.EQUAL);
                            ctx.nextToken();
                            value = new Expression(ctx).create().value; // TODO: Make property validate for types
                        } else {
                            value = true;
                        }
                        element.propertyList.updateProperty(property.name, value, ctx);
                    }

                    //since it has children
                    if (ctx.getNextToken().group != CTMLTokenGroup.ELEMENT_CLOSE){
                        ctx.nextToken();
                        new ElementNode(ctx).create();
                    }
                    ctx.tokenContext.expectTokenType(element.closingTokenType);
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                         NoSuchMethodException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        return this;
    }
}
