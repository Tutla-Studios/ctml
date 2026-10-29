package net.tutla.craftytml.client.render;

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
        if (!CTMLScroll.shown(box)) return null; // not drawn, so not grabbable
        if (CTMLScroll.clipsAway(box, mouseX, mouseY)) return null;

        List<CTMLLayout.Box> children = CTMLRenderer.paintOrder(box.children());
        for (int i = children.size() - 1; i >= 0; i--) {
            Grip grip = search(children.get(i), box, mouseX, mouseY);
            if (grip != null) return grip;
        }

        if (parent == null || !box.element().isDraggable()) return null;
        if (!CTMLScroll.within(mouseX, mouseY, box.x(), box.y(), box.right(), box.bottom())) return null;
        return grip(box, parent, mouseX, mouseY);
    }

    /**
     * The element can go anywhere in the room its parent gives it, which is the parent's content box or,
     * where the parent scrolls, as much of the scrollable area as the rest of the content reaches. Layout
     * works the same room out and clamps to it every frame, so a grab and a resize cannot disagree.
     * A scrolled parent has already moved its children, so the limits are read in that same moved space.
     */
    private static Grip grip(CTMLLayout.Box box, CTMLLayout.Box parent, double mouseX, double mouseY){
        DOMTreeElement element = box.element();
        int baseX = box.flowX(); // where layout would have put it, drag aside
        int baseY = box.flowY();

        int left = parent.contentX() - parent.port().scrollX();
        int top = parent.contentY() - parent.port().scrollY();
        int roomWidth = CTMLLayout.dragRoomWidth(parent, box);
        int roomHeight = CTMLLayout.dragRoomHeight(parent, box);

        int minX = left - baseX;
        int minY = top - baseY;
        return new Grip(element, element.getDragX(), element.getDragY(), mouseX, mouseY,
                minX, Math.max(minX, left + roomWidth - box.width() - baseX),
                minY, Math.max(minY, top + roomHeight - box.height() - baseY));
    }
}
