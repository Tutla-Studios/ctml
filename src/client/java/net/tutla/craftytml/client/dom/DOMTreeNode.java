package net.tutla.craftytml.client.dom;

public abstract class DOMTreeNode {
    DOMTreeElement parent;
    abstract String render(String indent);
}
