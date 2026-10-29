package net.tutla.craftytml.client.dom;

public abstract class DOMTreeNode {
    DOMTreeElement parent;

    /**
     * Counts every change layout would have to be worked out again for: a style, a property, a text
     * run, the shape of the tree, a drag or a scroll. A screen keeps the number it last laid out at
     * and only lays out again once it moves, which is what keeps layout off most frames.
     */
    private static long revision;

    public static long revision(){
        return revision;
    }

    /** Called by anything that changes what layout would come out as. */
    public static void touch(){
        revision++;
    }

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
        if (parent != null) {
            parent.children.remove(this);
            touch();
        }
        parent = null;
    }

    public abstract String render(String indent);
}
