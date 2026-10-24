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

        NodeRegistry.getRegister().forEach(node -> {
            if (NodeRegistry.getElementFromNode(node).tokenType == ctx.getCurrentToken().type){
                //Node.instantiateNode(node, ctx).create();

                try {
                    CTMLElement element = ElementRegistry.getElementFromTokenType(ctx.getCurrentToken().type).getClass().getDeclaredConstructor().newInstance();
                    while (ctx.getCurrentToken().group == CTMLTokenGroup.PROPERTY){
                        ElementProperty property = PropertyRegistry.getPropertyFromToken(ctx.getCurrentToken().type);
                        Object value;
                        if (property.type != ElementPropertyType.BOOLEAN){
                            ctx.tokenContext.expectTokenType(PrebuiltTusanTokenType.EQUAL);
                            value = new Expression(ctx).create(); // TODO: Make property validate for types
                        } else {
                            value = true;
                        }
                        element.propertyList.updateProperty(property.name, value, ctx);
                        ctx.nextToken();
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
