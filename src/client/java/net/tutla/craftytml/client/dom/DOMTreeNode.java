package net.tutla.craftytml.client.dom;

public abstract class DOMTreeNode {
    DOMTreeElement parent;

    public DOMTreeElement getParent(){
        return parent;
    }

    public DOMTreeElement getRoot(){
        DOMTreeNode node = this;
        while (node.parent != null) node = node.parent;
        return node instanceof DOMTreeElement e ? e : null;
    }

    public int getDepth(){
        int depth = 0;
        for (DOMTreeNode node = parent; node != null; node = node.parent) depth++;
        return depth;
    }

    public void detach(){
        if (parent != null) parent.children.remove(this);
        parent = null;
    }

    public abstract void draw();

    public abstract String render(String indent);
}
