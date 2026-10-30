package net.tutla.craftytml.client.render;

import net.tutla.craftytml.client.ctml.property.TabIndex;
import net.tutla.craftytml.client.dom.DOMTreeElement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What the keyboard is talking to, and where the ring is drawn.
 *
 * <p>An element is focusable when it says so with tabindex, and a scroll container is focusable without
 * saying anything, since scrolling is the one thing the keys can do to it. tabindex works the way html's
 * does: zero puts the element in the tab order at its place in the markup, a positive number puts it
 * ahead of those in that number's order, and below zero leaves it out of the order while keeping it
 * something a press can focus.
 *
 * <p>A press focuses whatever it lands on, or the nearest thing above it that can be focused. The wheel
 * leaves the focus where it is, the way a browser does: what the keys talk to is not meant to wander
 * off under the cursor.
 */
public final class CTMLFocus {
    private CTMLFocus(){}

    /** Whether the keyboard can be put on this box at all. */
    public static boolean focusable(CTMLLayout.Box box){
        if (!CTMLScroll.shown(box)) return false;
        int index = box.element().getTabIndex();
        return index != TabIndex.UNSET || box.userScrollsX() || box.userScrollsY();
    }

    /** Whether tab stops on it, which the ones taken out of the order with a negative tabindex do not. */
    public static boolean tabbable(CTMLLayout.Box box){
        if (!focusable(box)) return false;
        int index = box.element().getTabIndex();
        // unset and focusable means it scrolls, and a box that scrolls is somewhere tab should stop
        return index >= 0 || index == TabIndex.UNSET;
    }

    /**
     * Everything tab stops on, in the order it stops: the ones with a tabindex of their own first, in
     * that number's order, and then everything else where it comes in the markup. Equal numbers keep
     * the order they were written in, which is what html does with them.
     */
    public static List<CTMLLayout.Box> order(CTMLLayout.Box root){
        List<CTMLLayout.Box> found = new ArrayList<>();
        gather(root, found);

        List<CTMLLayout.Box> sorted = new ArrayList<>(found);
        sorted.sort(Comparator.comparingInt(box -> {
            int index = box.element().getTabIndex();
            return index > 0 ? index : Integer.MAX_VALUE; // the numbered ones first, the rest as written
        }));
        return sorted;
    }

    private static void gather(CTMLLayout.Box box, List<CTMLLayout.Box> found){
        if (!CTMLScroll.shown(box)) return;
        if (tabbable(box)) found.add(box);
        for (CTMLLayout.Box child : box.children()) gather(child, found);
    }

    /**
     * Where tab goes next, wrapping round at either end, or null where there is nothing to focus.
     * With nothing focused yet it starts at the first in the order, or the last going backwards.
     */
    public static DOMTreeElement next(CTMLLayout.Box root, DOMTreeElement from, boolean backwards){
        if (root == null) return null;
        List<CTMLLayout.Box> order = order(root);
        if (order.isEmpty()) return null;

        int at = -1;
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).element() == from) {
                at = i;
                break;
            }
        }

        if (at < 0) return (backwards ? order.getLast() : order.getFirst()).element();
        int step = backwards ? -1 : 1;
        return order.get(Math.floorMod(at + step, order.size())).element();
    }

    /**
     * What a press at this spot focuses: the innermost box under the cursor that can be focused, or the
     * nearest one above it that can, so a press on a line of text focuses the box holding the text.
     * Null where nothing under the cursor can be focused at all, which leaves the focus where it was.
     */
    public static DOMTreeElement at(CTMLLayout.Box root, double x, double y){
        List<CTMLLayout.Box> under = new ArrayList<>();
        if (root == null || !hit(root, x, y, under)) return null;

        for (int i = under.size() - 1; i >= 0; i--) {
            if (focusable(under.get(i))) return under.get(i).element();
        }
        return null;
    }

    /**
     * The boxes from the root down to the innermost one under the cursor, outermost first. The topmost
     * child wins, so what the cursor meets is what is drawn over everything else there.
     */
    private static boolean hit(CTMLLayout.Box box, double x, double y, List<CTMLLayout.Box> path){
        if (!CTMLScroll.shown(box) || CTMLScroll.clipsAway(box, x, y)) return false;

        List<CTMLLayout.Box> children = CTMLRenderer.paintOrder(box.children());
        for (int i = children.size() - 1; i >= 0; i--) {
            if (hit(children.get(i), x, y, path)) {
                path.addFirst(box);
                return true;
            }
        }

        if (!CTMLScroll.within(x, y, box.x(), box.y(), box.right(), box.bottom())) return false;
        path.add(box);
        return true;
    }
}
