package net.tutla.craftytml.client.tusan.node;

import net.tutla.craftytml.client.tusan.register.NodeRegistry;
import net.tutla.tums.tusan.TusanContext;
import net.tutla.tums.tusan.node.Node;
import net.tutla.tums.tusan.node.NodeConfig;

public class ElementNode extends CTMLNode {
    public ElementNode(TusanContext ctx) {
        super(ctx);
        setNodeConfig(
                new NodeConfig()
                        .setDontSendNextToken(true)
        );
    }

    @Override
    public ElementNode create() {
        NodeRegistry.getRegister().forEach(node -> {
            if (NodeRegistry.getElementFromNode(node).tokenType == ctx.getCurrentToken().type){
                Node.instantiateNode(node, ctx).create();
            }
        });
        return this;
    }
}
