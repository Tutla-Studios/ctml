package net.tutla.craftytml.client.tusan.node;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.ctml.element.CTML;
import net.tutla.craftytml.client.ctml.element.Span;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.tusan.CTMLTokenGroup;
import net.tutla.craftytml.client.tusan.register.ElementRegistry;
import net.tutla.craftytml.client.tusan.register.PropertyRegistry;
import net.tutla.tums.tusan.TusanContext;
import net.tutla.tums.tusan.lexer.util.prebuilt.PrebuiltTusanTokenType;
import net.tutla.tums.tusan.node.Node;
import net.tutla.tums.tusan.nodes.expression.Expression;

import java.lang.reflect.InvocationTargetException;

public class ElementNode extends Node {
    private DOMTreeElement DOMTreeParentElementNode;
    public ElementNode(TusanContext ctx) {
        super(ctx);

    }

    public void createWrapper(DOMTreeElement DOMTreeElementNode){
        this.DOMTreeParentElementNode = DOMTreeElementNode;
        create();
    }

    @Override
    public DOMTreeElement create() {
        //Object value = false;
        if (interpreter.end){
            return DOMTreeParentElementNode;
        }

        if (DOMTreeParentElementNode == null){
            // TODO: Make this return a head node
            DOMTreeParentElementNode = new DOMTreeElement(new CTML());
        }

        if (ctx.getCurrentToken().group != CTMLTokenGroup.ELEMENT && ctx.getCurrentToken().type != PrebuiltTusanTokenType.STRING){ // avoid receiving non-elements or raw text
            ctx.nextToken();
            return DOMTreeParentElementNode;
        }

        if (ctx.getCurrentToken().type == PrebuiltTusanTokenType.STRING){
            DOMTreeParentElementNode.getElement().updateProperty("text", ctx.getCurrentToken().value, ctx);
        } else {
            CTMLElement registered = ElementRegistry.getElementFromTokenType(ctx.getCurrentToken().type);
            if (registered != null){
                CTMLElement element = createElementFromClass(registered);
                registerAllProperties(element);
                DOMTreeElement domTreeElementNodeCreated = createElementAndAppendToTree(element);

                while (ctx.getNextToken() != null && ctx.getNextToken().group != CTMLTokenGroup.ELEMENT_CLOSE){
                    ctx.nextToken();
                    new ElementNode(ctx).createWrapper(domTreeElementNodeCreated);
                }
                ctx.tokenContext.expectTokenType(element.closingTokenType);
            }
        }



        ctx.interpreter.returned = DOMTreeParentElementNode.getRoot();
        return DOMTreeParentElementNode;
    }

    private DOMTreeElement createElementAndAppendToTree(CTMLElement element){
        // adding to the tree //
        DOMTreeElement domTreeElementNodeCreated = new DOMTreeElement(element);

        // checking if its the head node
        if (element instanceof CTML && DOMTreeParentElementNode.getElement() instanceof CTML){
            DOMTreeParentElementNode = domTreeElementNodeCreated; // replace the template head with ts.
            return domTreeElementNodeCreated; // appending it to itself would make the tree its own parent
        }
        DOMTreeParentElementNode.append(domTreeElementNodeCreated);

        return domTreeElementNodeCreated;
    }

    private CTMLElement createElementFromClass(CTMLElement element){
        try {
            return element.getClass().getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private void registerAllProperties(CTMLElement element){
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
            element.updateProperty(property.name, value, ctx);
        }
    }
}
