package net.tutla.craftytml.client.dom;

public abstract class DOMTreeNode {
    DOMTreeElement parent;

    /**
     * Counts every change layout would have to be worked out again for: a style, a property, a text
     * run, the shape of the tree, a drag or a scroll. A screen keeps the number it last laid out at
     * and only lays out again once it moves, which is what keeps layout off most frames.
     */
    private static long revision;

    /** The revision at which anything in this node's own subtree last changed. */
    private long subtree;

    public static long revision(){
        return revision;
    }

    /** Called by anything that changes what layout would come out as. */
    public static void touch(){
        revision++;
    }

    /**
     * Marks this node and everything above it as changed, so a box worked out for an element that was
     * left alone can be used again while the ones around it are laid out afresh.
     */
    public void touched(){
        revision++;
        for (DOMTreeNode node = this; node != null; node = node.parent) node.subtree = revision;
    }

    public long subtreeRevision(){
        return subtree;
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
            parent.touched();
        }
        parent = null;
    }

    public abstract String render(String indent);
}
