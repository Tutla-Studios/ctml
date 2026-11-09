package net.tutla.craftytml.client.tusan.node;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.tums.tusan.TusanContext;
import net.tutla.tums.tusan.node.Node;

public abstract class CTMLNode extends Node {
    private CTMLElement createdElement;

    public CTMLNode(TusanContext ctx) {
        super(ctx);
    }

    public abstract CTMLNode create();
}
