package net.tutla.craftytml.client.render;

import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.dom.DOMTreeElement;

import java.util.List;

/**
 * Picking a draggable element out of a laid out tree and moving it with the cursor.
 * Kept away from the screen so it can be worked out without a running game.
 */
public final class CTMLDrag {
    private CTMLDrag(){}

    /** A box the user has hold of, remembering where it started so the cursor can be followed from there. */
    public static final class Grip {
        private final DOMTreeElement element;
        private final int startX;
        private final int startY;
        private final double grabX;
        private final double grabY;
        private final int minX;
        private final int maxX;
        private final int minY;
        private final int maxY;

        private Grip(DOMTreeElement element, int startX, int startY, double grabX, double grabY,
                     int minX, int maxX, int minY, int maxY){
            this.element = element;
            this.startX = startX;
            this.startY = startY;
            this.grabX = grabX;
            this.grabY = grabY;
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
        }

        public DOMTreeElement element(){
            return element;
        }

        /**
         * Puts the element where the cursor is, measured from where it was grabbed rather than
         * from one frame to the next, so a dropped or clamped movement cannot leave it behind.
         */
        public void follow(double mouseX, double mouseY){
            element.setDragOffset(
                    Math.clamp(startX + Math.round(mouseX - grabX), minX, maxX),
                    Math.clamp(startY + Math.round(mouseY - grabY), minY, maxY));
        }
    }

    /** The draggable box under the cursor, or null. Nothing is grabbed from the root itself: it has no parent to move in. */
    public static Grip grab(CTMLLayout.Box root, double mouseX, double mouseY){
        return root == null ? null : search(root, null, mouseX, mouseY);
    }

    /**
     * Topmost first, so whatever the user can actually see is what gets picked up, and deepest first,
     * so a draggable box inside another draggable one wins. Only a box that clips its overflow cuts the
     * search short; everything else is searched even where the cursor is outside it, since a child can
     * stick out of its parent and still be drawn.
     */
    private static Grip search(CTMLLayout.Box box, CTMLLayout.Box parent, double mouseX, double mouseY){
        ElementStyle style = box.style();
        if (style.get(ElementStyle.VISIBILITY) == ElementStyle.Visibility.HIDDEN) return null; // not drawn, so not grabbable
        if (style.get(ElementStyle.OPACITY, 1.0f) <= 0.0f) return null;

        boolean clips = style.get(ElementStyle.OVERFLOW, ElementStyle.Overflow.VISIBLE) != ElementStyle.Overflow.VISIBLE;
        if (clips && !within(mouseX, mouseY, box.contentX(), box.contentY(), box.contentRight(), box.contentBottom())) return null;

        List<CTMLLayout.Box> children = CTMLRenderer.paintOrder(box.children());
        for (int i = children.size() - 1; i >= 0; i--) {
            Grip grip = search(children.get(i), box, mouseX, mouseY);
            if (grip != null) return grip;
        }

        if (parent == null || !box.element().isDraggable()) return null;
        if (!within(mouseX, mouseY, box.x(), box.y(), box.right(), box.bottom())) return null;
        return grip(box, parent, mouseX, mouseY);
    }

    /** The element can go anywhere inside its parent's content box, so that is where the limits come from. */
    private static Grip grip(CTMLLayout.Box box, CTMLLayout.Box parent, double mouseX, double mouseY){
        DOMTreeElement element = box.element();
        int baseX = box.x() - element.getDragX(); // where layout would have put it, drag aside
        int baseY = box.y() - element.getDragY();

        int minX = parent.contentX() - baseX;
        int minY = parent.contentY() - baseY;
        return new Grip(element, element.getDragX(), element.getDragY(), mouseX, mouseY,
                minX, Math.max(minX, parent.contentRight() - box.width() - baseX),
                minY, Math.max(minY, parent.contentBottom() - box.height() - baseY));
    }

    private static boolean within(double x, double y, int left, int top, int right, int bottom){
        return x >= left && x < right && y >= top && y < bottom;
    }
}
