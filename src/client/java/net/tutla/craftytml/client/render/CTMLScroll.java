package net.tutla.craftytml.client.render;

import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.ElementStyle.Overflow;
import net.tutla.craftytml.client.dom.DOMTreeElement;

import java.util.List;

/**
 * Everything overflow: what a box cuts off, what the user is allowed to scroll, where its bars sit
 * and how the wheel and those bars move the content.
 * Kept away from the screen and the renderer so the geometry can be worked out without a running game.
 */
public final class CTMLScroll {
    /** How thick a bar is when nothing says otherwise. */
    public static final int DEFAULT_THICKNESS = 4;
    /** However little there is to scroll, the thumb stays big enough to grab. */
    public static final int MIN_THUMB = 8;
    /** Gui pixels the content moves for one notch of the wheel. */
    public static final int WHEEL_STEP = 12;

    public static final int DEFAULT_TRACK_COLOR = 0x33000000;
    public static final int DEFAULT_THUMB_COLOR = 0x99FFFFFF;

    private CTMLScroll(){}

    // ---- what the overflow keys add up to ----

    public static Overflow overflowX(ElementStyle style){
        return beside(style.get(ElementStyle.OVERFLOW_X, Overflow.VISIBLE), style.get(ElementStyle.OVERFLOW_Y, Overflow.VISIBLE));
    }

    public static Overflow overflowY(ElementStyle style){
        return beside(style.get(ElementStyle.OVERFLOW_Y, Overflow.VISIBLE), style.get(ElementStyle.OVERFLOW_X, Overflow.VISIBLE));
    }

    /**
     * What an axis ends up meaning next to the other one, the way css computes it: nothing can spill out
     * of a box that scrolls the other way, so visible turns into auto and clip into hidden.
     */
    private static Overflow beside(Overflow own, Overflow other){
        if (other != Overflow.SCROLL && other != Overflow.AUTO) return own;
        return switch (own) {
            case VISIBLE -> Overflow.AUTO;
            case CLIP -> Overflow.HIDDEN;
            default -> own;
        };
    }

    /** Whether the axis cuts off what reaches past it. */
    public static boolean clips(Overflow axis){
        return axis != Overflow.VISIBLE;
    }

    /** Whether the axis keeps a scroll offset at all. Clip is the one that cuts off and stays put. */
    public static boolean scrolls(Overflow axis){
        return axis == Overflow.HIDDEN || axis == Overflow.SCROLL || axis == Overflow.AUTO;
    }

    /** Whether the user may move it, by a bar or by the wheel. Hidden scrolls, but only from code. */
    public static boolean userScrolls(Overflow axis){
        return axis == Overflow.SCROLL || axis == Overflow.AUTO;
    }

    // ---- how the bars look ----

    public static boolean barsHidden(ElementStyle style){
        return style.get(ElementStyle.SCROLLBAR_HIDDEN, false);
    }

    public static int thickness(ElementStyle style){
        return Math.max(1, style.get(ElementStyle.SCROLLBAR_WIDTH, DEFAULT_THICKNESS));
    }

    /** True keeps room for the bars inside the box; false, the default, lets them float over the content. */
    public static boolean gutter(ElementStyle style){
        return style.get(ElementStyle.SCROLLBAR_GUTTER, false) && !barsHidden(style);
    }

    public static int trackColor(ElementStyle style){
        return style.get(ElementStyle.SCROLLBAR_TRACK_COLOR, DEFAULT_TRACK_COLOR);
    }

    public static int thumbColor(ElementStyle style){
        return style.get(ElementStyle.SCROLLBAR_THUMB_COLOR, DEFAULT_THUMB_COLOR);
    }

    public static int thumbRadius(ElementStyle style){
        return Math.max(0, style.get(ElementStyle.SCROLLBAR_RADIUS, 0));
    }

    /** scroll always offers a bar, auto only once there is something to scroll, and hidden bars never do. */
    public static boolean showsBar(ElementStyle style, Overflow axis, boolean overflowing){
        if (barsHidden(style)) return false;
        return axis == Overflow.SCROLL || (axis == Overflow.AUTO && overflowing);
    }

    public static boolean showsVerticalBar(CTMLLayout.Box box){
        return showsBar(box.style(), overflowY(box.style()), box.port().maxScrollY() > 0);
    }

    public static boolean showsHorizontalBar(CTMLLayout.Box box){
        return showsBar(box.style(), overflowX(box.style()), box.port().maxScrollX() > 0);
    }

    // ---- where the bars sit ----

    /** A track and the thumb inside it, both already in screen pixels. */
    public record Bar(boolean vertical, int x, int y, int width, int height,
                      int thumbX, int thumbY, int thumbWidth, int thumbHeight) {
        public int right(){ return x + width; }
        public int bottom(){ return y + height; }

        /** How far the thumb can travel, which is what a drag gets measured against. */
        public int travel(){ return vertical ? height - thumbHeight : width - thumbWidth; }

        public boolean holds(double mouseX, double mouseY){
            return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
        }

        public boolean holdsThumb(double mouseX, double mouseY){
            return mouseX >= thumbX && mouseX < thumbX + thumbWidth
                    && mouseY >= thumbY && mouseY < thumbY + thumbHeight;
        }
    }

    /** The vertical bar of a box, or null when it has none. It runs down the right of the scrollport. */
    public static Bar verticalBar(CTMLLayout.Box box){
        if (!showsVerticalBar(box)) return null;
        CTMLLayout.Port port = box.port();
        int thick = thickness(box.style());

        // with a gutter the bar gets the room that was kept for it, otherwise it floats over the content
        int x = port.gutterRight() > 0 ? port.right() : port.right() - thick;
        // when both bars float, the horizontal one owns the corner
        int height = port.height() - (showsHorizontalBar(box) && port.gutterBottom() == 0 ? thick : 0);
        if (height <= 0) return null;

        int thumb = thumbSize(height, port.height(), port.scrollHeight());
        int thumbY = port.y() + slide(height - thumb, port.scrollY(), port.maxScrollY());
        return new Bar(true, x, port.y(), thick, height, x, thumbY, thick, thumb);
    }

    /** The horizontal bar of a box, or null when it has none. It runs along the bottom of the scrollport. */
    public static Bar horizontalBar(CTMLLayout.Box box){
        if (!showsHorizontalBar(box)) return null;
        CTMLLayout.Port port = box.port();
        int thick = thickness(box.style());

        int y = port.gutterBottom() > 0 ? port.bottom() : port.bottom() - thick;
        int width = port.width() - (showsVerticalBar(box) && port.gutterRight() == 0 ? thick : 0);
        if (width <= 0) return null;

        int thumb = thumbSize(width, port.width(), port.scrollWidth());
        int thumbX = port.x() + slide(width - thumb, port.scrollX(), port.maxScrollX());
        return new Bar(false, port.x(), y, width, thick, thumbX, y, thumb, thick);
    }

    /** The thumb takes up as much of the track as the port takes up of the content. */
    private static int thumbSize(int track, int port, int content){
        if (content <= 0 || port >= content) return track;
        int size = Math.round(track * (port / (float) content));
        return Math.clamp(size, Math.min(MIN_THUMB, track), track);
    }

    private static int slide(int travel, int scroll, int maxScroll){
        if (travel <= 0 || maxScroll <= 0) return 0;
        return Math.clamp(Math.round(travel * (scroll / (float) maxScroll)), 0, travel);
    }

    // ---- moving the content ----

    /** A bar the user has hold of, which maps however far the cursor went onto however far the content can go. */
    public static final class Grip {
        private final DOMTreeElement element;
        private final boolean vertical;
        private final int startScroll;
        private final double grabAt;
        private final int travel;
        private final int maxScroll;

        private Grip(DOMTreeElement element, boolean vertical, int startScroll, double grabAt, int travel, int maxScroll){
            this.element = element;
            this.vertical = vertical;
            this.startScroll = startScroll;
            this.grabAt = grabAt;
            this.travel = travel;
            this.maxScroll = maxScroll;
        }

        public DOMTreeElement element(){
            return element;
        }

        /**
         * Scrolls to wherever the cursor has got to, measured from where the thumb was grabbed rather than
         * one frame to the next, so a movement that arrives late or lands off the track cannot drift.
         */
        public void follow(double mouseX, double mouseY){
            double moved = (vertical ? mouseY : mouseX) - grabAt;
            int scroll = travel <= 0
                    ? 0
                    : Math.clamp(startScroll + Math.round(moved * maxScroll / (float) travel), 0, maxScroll);
            apply(element, vertical, scroll);
        }
    }

    /**
     * Takes hold of whatever scrollbar the press landed on, or null. A press on the track rather than the
     * thumb pulls the thumb under the cursor first, so the drag that follows carries on from there.
     */
    public static Grip grab(CTMLLayout.Box root, double mouseX, double mouseY){
        return root == null ? null : searchBars(root, mouseX, mouseY);
    }

    private static Grip searchBars(CTMLLayout.Box box, double mouseX, double mouseY){
        if (!shown(box) || clipsAway(box, mouseX, mouseY)) return null;

        // a box draws its bars over its own children, so they are what the cursor meets first
        if (box.userScrollsY()) {
            Bar bar = verticalBar(box);
            if (bar != null && bar.holds(mouseX, mouseY)) return grip(box, bar, mouseX, mouseY);
        }
        if (box.userScrollsX()) {
            Bar bar = horizontalBar(box);
            if (bar != null && bar.holds(mouseX, mouseY)) return grip(box, bar, mouseX, mouseY);
        }

        List<CTMLLayout.Box> children = CTMLRenderer.paintOrder(box.children());
        for (int i = children.size() - 1; i >= 0; i--) {
            Grip grip = searchBars(children.get(i), mouseX, mouseY);
            if (grip != null) return grip;
        }
        return null;
    }

    private static Grip grip(CTMLLayout.Box box, Bar bar, double mouseX, double mouseY){
        CTMLLayout.Port port = box.port();
        double pos = bar.vertical() ? mouseY : mouseX;
        int travel = bar.travel();
        int maxScroll = bar.vertical() ? port.maxScrollY() : port.maxScrollX();
        int scroll = bar.vertical() ? port.scrollY() : port.scrollX();

        if (!bar.holdsThumb(mouseX, mouseY)) { // bring the thumb to the cursor instead of jumping once it moves
            int trackStart = bar.vertical() ? bar.y() : bar.x();
            int thumb = bar.vertical() ? bar.thumbHeight() : bar.thumbWidth();
            scroll = travel <= 0
                    ? 0
                    : Math.clamp(Math.round((pos - trackStart - thumb / 2.0f) * maxScroll / travel), 0, maxScroll);
            apply(box.element(), bar.vertical(), scroll);
        }

        return new Grip(box.element(), bar.vertical(), scroll, pos, travel, maxScroll);
    }

    /**
     * Hands a turn of the wheel to the innermost box under the cursor that can take it, falling out to the
     * box around it once that one has nothing left to give, which is what a browser does.
     */
    public static boolean wheel(CTMLLayout.Box root, double mouseX, double mouseY, double deltaX, double deltaY){
        if (root == null) return false;
        int dx = Math.round((float) deltaX * WHEEL_STEP);
        int dy = Math.round((float) -deltaY * WHEEL_STEP); // a notch away from the user brings the content down
        return (dx != 0 || dy != 0) && roll(root, mouseX, mouseY, dx, dy);
    }

    private static boolean roll(CTMLLayout.Box box, double mouseX, double mouseY, int dx, int dy){
        if (!shown(box) || clipsAway(box, mouseX, mouseY)) return false;

        List<CTMLLayout.Box> children = CTMLRenderer.paintOrder(box.children());
        for (int i = children.size() - 1; i >= 0; i--) {
            if (roll(children.get(i), mouseX, mouseY, dx, dy)) return true;
        }

        if (!within(mouseX, mouseY, box.x(), box.y(), box.right(), box.bottom())) return false;

        CTMLLayout.Port port = box.port();
        int moveX = box.userScrollsX() ? dx : 0;
        int moveY = box.userScrollsY() ? dy : 0;
        boolean movesX = canMove(port.scrollX(), port.maxScrollX(), moveX);
        boolean movesY = canMove(port.scrollY(), port.maxScrollY(), moveY);

        // a plain up or down notch still scrolls a box that only goes sideways
        if (!movesX && !movesY && dy != 0 && box.userScrollsX() && canMove(port.scrollX(), port.maxScrollX(), dy)) {
            moveX = dy;
            movesX = true;
        }
        if (!movesX && !movesY) return false;

        box.element().setScrollOffset(
                movesX ? Math.clamp(port.scrollX() + moveX, 0, port.maxScrollX()) : port.scrollX(),
                movesY ? Math.clamp(port.scrollY() + moveY, 0, port.maxScrollY()) : port.scrollY());
        return true;
    }

    private static boolean canMove(int scroll, int maxScroll, int delta){
        if (delta == 0) return false;
        return delta < 0 ? scroll > 0 : scroll < maxScroll;
    }

    private static void apply(DOMTreeElement element, boolean vertical, int scroll){
        element.setScrollOffset(
                vertical ? element.getScrollX() : scroll,
                vertical ? scroll : element.getScrollY());
    }

    // ---- shared with hit testing ----

    /** Whether the box is drawn at all, since nothing invisible answers the cursor. */
    static boolean shown(CTMLLayout.Box box){
        ElementStyle style = box.style();
        return style.get(ElementStyle.VISIBILITY) != ElementStyle.Visibility.HIDDEN
                && style.get(ElementStyle.OPACITY, 1.0f) > 0.0f;
    }

    /**
     * Whether the cursor sits where this box has cut everything off, on either axis it clips.
     * The room kept for the bars counts as inside, so a bar in its gutter stays grabbable.
     */
    static boolean clipsAway(CTMLLayout.Box box, double mouseX, double mouseY){
        CTMLLayout.Port port = box.port();
        if (box.clipsX() && (mouseX < port.x() || mouseX >= port.right() + port.gutterRight())) return true;
        return box.clipsY() && (mouseY < port.y() || mouseY >= port.bottom() + port.gutterBottom());
    }

    static boolean within(double x, double y, int left, int top, int right, int bottom){
        return x >= left && x < right && y >= top && y < bottom;
    }
}
