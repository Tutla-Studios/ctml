package net.tutla.craftytml.client.ctml.style;

import net.tutla.craftytml.client.dom.DOMTreeNode;

/**
 * What the screen as a whole is like, which is what the variants that are not about one element ask
 * about: how big the viewport is, behind the breakpoint variants, and whether the ui is being drawn
 * dark, behind dark:.
 *
 * <p>The size is written down by whatever lays a tree out, since that is the one place that knows the
 * viewport, and is read back by a variant on an element that has no viewport of its own to look at.
 * Nothing has to be told about a resize: the breakpoints are worked out afresh every time an element
 * is asked for its style, and layout already keeps the viewport in the key it caches a box under.
 *
 * <p>The breakpoints are in gui pixels rather than css ones, since that is what ctml measures in: a
 * minecraft window is around 427 by 240 gui pixels at the biggest gui scale and 640 by 360 at the one
 * most windows land on, so the usual screen is somewhere around md and lg rather than off the end of
 * the scale the way css's own 640 to 1536 would put it.
 */
public final class Media {
    private Media(){}

    private static int width;
    private static int height;

    /** Whether dark: holds where nothing on the tree itself says, which the host sets. */
    private static boolean dark;

    /**
     * Bumped by anything a style depends on that is not in the tree, so the boxes worked out before
     * it moved can be thrown away. The viewport is not in here: layout keys its own caches on that.
     */
    private static long generation;

    /** Told to us by whatever is laying a tree out, which is the one place the viewport is known. */
    public static void measured(int width, int height){
        Media.width = width;
        Media.height = height;
    }

    public static int width(){ return width; }
    public static int height(){ return height; }

    public static boolean isDark(){ return dark; }

    /**
     * Whether dark: holds for a tree that says nothing about its own colour scheme. An element or
     * anything above it can say so for itself with scheme-dark, which beats this either way.
     */
    public static void dark(boolean dark){
        if (Media.dark == dark) return;
        Media.dark = dark;
        generation++;
        DOMTreeNode.touch(); // every tree on screen may look different now
    }

    public static long generation(){ return generation; }
}
