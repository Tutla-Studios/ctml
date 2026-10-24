package net.tutla.craftytml.client.tusan.node;

import net.tutla.tums.tusan.TusanContext;

public class DivisionNode extends CTMLNode {

    public DivisionNode(TusanContext ctx) {
        super(ctx);
    }

    @Override
    public DivisionNode create() {
        return this;
    }
}
