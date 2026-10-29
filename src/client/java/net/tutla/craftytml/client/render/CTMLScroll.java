package net.tutla.craftytml.client.render;

import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.ElementStyle.Overflow;
import net.tutla.craftytml.client.dom.DOMTreeElement;

import java.util.ArrayList;
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
    /** Gui pixels the content moves for one notch of the wheel, and for one press of an arrow key. */
    public static final int WHEEL_STEP = 12;
    /** How much of the scrollport a page up or down keeps, so the eye has something to carry over. */
    public static final int PAGE_OVERLAP = 12;
    /** Roughly how long a smooth scroll takes to cover most of its distance, in seconds. */
    public static final float SLIDE_TAU = 0.07f;

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

    /** Whether a wheel this box cannot use is handed on to the box around it. */
    public static ElementStyle.Overscroll overscrollX(ElementStyle style){
        return style.get(ElementStyle.OVERSCROLL_BEHAVIOR_X, ElementStyle.Overscroll.AUTO);
    }

    public static ElementStyle.Overscroll overscrollY(ElementStyle style){
        return style.get(ElementStyle.OVERSCROLL_BEHAVIOR_Y, ElementStyle.Overscroll.AUTO);
    }

    /**
     * Whether a scroll slides rather than landing at once. Css only promises this for the scrolls a
     * script or the page itself asks for; ctml gives the wheel and the keys the same, since a game ui
     * is looked at rather than read and a jump of a dozen pixels is hard to follow.
     */
    public static boolean slides(ElementStyle style){
        return style.get(ElementStyle.SCROLL_BEHAVIOR, ElementStyle.ScrollBehavior.AUTO)
                == ElementStyle.ScrollBehavior.SMOOTH;
    }

    /** Whether what is on show is held still when something above it changes size. */
    public static boolean anchors(ElementStyle style){
        return style.get(ElementStyle.OVERFLOW_ANCHOR, ElementStyle.OverflowAnchor.AUTO)
                == ElementStyle.OverflowAnchor.AUTO;
    }

    // ---- how the bars look ----

    public static boolean barsHidden(ElementStyle style){
        return style.get(ElementStyle.SCROLLBAR_HIDDEN, false);
    }

    public static int thickness(ElementStyle style){
        return Math.max(1, style.get(ElementStyle.SCROLLBAR_WIDTH, DEFAULT_THICKNESS));
    }

    /**
     * How much room is kept for the bars inside the box. Auto, the default, keeps none and lets them
     * float over the content; stable keeps the far edge clear whenever the axis could scroll at all,
     * whether a bar is there yet or not, and both-edges keeps the same room on the near edge as well.
     */
    public static ElementStyle.ScrollbarGutter gutter(ElementStyle style){
        if (barsHidden(style)) return ElementStyle.ScrollbarGutter.AUTO;
        return style.get(ElementStyle.SCROLLBAR_GUTTER, ElementStyle.ScrollbarGutter.AUTO);
    }

    /**
     * How wide the room kept on an axis is: nothing, or a bar's worth however things turn out.
     * Only an axis that could show a bar keeps any, so an axis that is merely hidden keeps none.
     */
    public static int gutterFor(ElementStyle style, Overflow axis){
        return gutter(style) != ElementStyle.ScrollbarGutter.AUTO && userScrolls(axis) ? thickness(style) : 0;
    }

    /** Whether the same room is kept on the near edge too, so the content stays where it was. */
    public static boolean bothEdges(ElementStyle style){
        return gutter(style) == ElementStyle.ScrollbarGutter.BOTH_EDGES;
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
            // a thumb under the cursor goes where the cursor is, never behind it, however smooth the box asks to be
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
        // nothing left to give, so the turn goes outwards unless the box was told to keep it
        if (!movesX && !movesY) return contains(box, dx, dy);

        nudge(box, movesX ? moveX : 0, movesY ? moveY : 0);
        return true;
    }

    /**
     * Whether a box keeps a wheel it cannot use rather than handing it outwards, which is css's
     * overscroll-behavior. Only an axis the user may scroll at all can hold onto one.
     */
    private static boolean contains(CTMLLayout.Box box, int dx, int dy){
        ElementStyle style = box.style();
        boolean heldX = dx != 0 && box.userScrollsX() && overscrollX(style) != ElementStyle.Overscroll.AUTO;
        boolean heldY = dy != 0 && box.userScrollsY() && overscrollY(style) != ElementStyle.Overscroll.AUTO;
        return heldX || heldY;
    }

    /** Moves a box's content by however much, clamped to what there is, smoothly where it asked for that. */
    private static void nudge(CTMLLayout.Box box, int dx, int dy){
        CTMLLayout.Port port = box.port();
        scrollTo(box,
                Math.clamp(target(box, false) + dx, 0, port.maxScrollX()),
                Math.clamp(target(box, true) + dy, 0, port.maxScrollY()));
    }

    /**
     * Where the content is heading, which for a box part way through a smooth scroll is further on
     * than where it is now: a second notch has to add to the first rather than start again from it.
     */
    private static int target(CTMLLayout.Box box, boolean vertical){
        return vertical ? box.element().getScrollTargetY() : box.element().getScrollTargetX();
    }

    /** Puts a box's content at an offset, sliding there if the box asked to slide. */
    public static void scrollTo(CTMLLayout.Box box, int scrollX, int scrollY){
        if (slides(box.style())) box.element().slideScrollTo(scrollX, scrollY);
        else box.element().setScrollOffset(scrollX, scrollY);
    }

    // ---- the keyboard ----

    /** What a key asks a scroll container to do. The screen turns its key codes into these. */
    public enum Nudge {
        LINE_UP, LINE_DOWN, LINE_LEFT, LINE_RIGHT, PAGE_UP, PAGE_DOWN, TO_START, TO_END;

        public boolean isVertical(){
            return this != LINE_LEFT && this != LINE_RIGHT;
        }
    }

    /**
     * Hands a key to a scroll container, falling out to the one around it once that one has nothing
     * left to give, the same way the wheel does. focus is whatever the user last scrolled or pressed
     * on; with nothing focused the outermost scroll container takes it, which is the closest thing
     * to a browser scrolling the page when nothing else has the keyboard.
     */
    public static boolean key(CTMLLayout.Box root, DOMTreeElement focus, Nudge nudge){
        if (root == null || nudge == null) return false;

        List<CTMLLayout.Box> path = focus == null ? null : path(root, focus);
        if (path == null || path.isEmpty()) {
            CTMLLayout.Box outer = outermost(root);
            path = outer == null ? List.of() : path(root, outer.element());
        }

        for (int i = path.size() - 1; i >= 0; i--) {
            CTMLLayout.Box box = path.get(i);
            if (press(box, nudge)) return true;
            if (contains(box, nudge.isVertical() ? 0 : 1, nudge.isVertical() ? 1 : 0)) return true;
        }
        return false;
    }

    /** One key against one box, or false when that box has nothing left to give along the way asked. */
    private static boolean press(CTMLLayout.Box box, Nudge nudge){
        CTMLLayout.Port port = box.port();
        boolean vertical = nudge.isVertical();
        if (!(vertical ? box.userScrollsY() : box.userScrollsX())) return false;

        int max = vertical ? port.maxScrollY() : port.maxScrollX();
        if (max <= 0) return false;

        int page = Math.max(WHEEL_STEP, (vertical ? port.height() : port.width()) - PAGE_OVERLAP);
        int at = target(box, vertical);
        int want = switch (nudge) {
            case LINE_UP, LINE_LEFT -> at - WHEEL_STEP;
            case LINE_DOWN, LINE_RIGHT -> at + WHEEL_STEP;
            case PAGE_UP -> at - page;
            case PAGE_DOWN -> at + page;
            case TO_START -> 0;
            case TO_END -> max;
        };

        int scroll = Math.clamp(want, 0, max);
        if (scroll == at) return false;
        scrollTo(box, vertical ? port.scrollX() : scroll, vertical ? scroll : port.scrollY());
        return true;
    }

    /** The innermost scroll container the cursor is over, which is what a press hands the keyboard to. */
    public static DOMTreeElement focusAt(CTMLLayout.Box root, double mouseX, double mouseY){
        if (root == null || !shown(root) || clipsAway(root, mouseX, mouseY)) return null;

        List<CTMLLayout.Box> children = CTMLRenderer.paintOrder(root.children());
        for (int i = children.size() - 1; i >= 0; i--) {
            DOMTreeElement found = focusAt(children.get(i), mouseX, mouseY);
            if (found != null) return found;
        }

        boolean scrollable = root.userScrollsX() || root.userScrollsY();
        return scrollable && within(mouseX, mouseY, root.x(), root.y(), root.right(), root.bottom())
                ? root.element()
                : null;
    }

    /** The outermost box that can be scrolled at all, which stands in for the page itself. */
    private static CTMLLayout.Box outermost(CTMLLayout.Box box){
        if (!shown(box)) return null;
        if (box.userScrollsX() || box.userScrollsY()) return box;
        for (CTMLLayout.Box child : box.children()) {
            CTMLLayout.Box found = outermost(child);
            if (found != null) return found;
        }
        return null;
    }

    /** The boxes from the root down to the one holding an element, outermost first, or empty. */
    public static List<CTMLLayout.Box> path(CTMLLayout.Box root, DOMTreeElement element){
        List<CTMLLayout.Box> path = new ArrayList<>();
        return walkTo(root, element, path) ? path : List.of();
    }

    private static boolean walkTo(CTMLLayout.Box box, DOMTreeElement element, List<CTMLLayout.Box> path){
        path.add(box);
        if (box.element() == element) return true;
        for (CTMLLayout.Box child : box.children()) if (walkTo(child, element, path)) return true;
        path.removeLast();
        return false;
    }

    // ---- sliding, holding still and bringing into view ----

    /**
     * Moves every box that is part way through a smooth scroll on by however long the last frame took.
     * True when something moved, which is the screen's cue to lay out again.
     */
    public static boolean animate(CTMLLayout.Box box, float seconds){
        if (box == null || seconds <= 0) return false;
        boolean moved = box.element().advanceScroll(seconds, SLIDE_TAU);
        for (CTMLLayout.Box child : box.children()) moved |= animate(child, seconds);
        return moved;
    }

    /**
     * Holds what is on show still when something above it changes size, which is css's scroll anchoring.
     * Each scroll container picks the first child showing through its scrollport and remembers where in
     * the content that child sat; next time round, anything that moved it is taken off the scroll offset
     * instead of moving under the user's eye. True when an offset was corrected, so whatever called can
     * lay out again and draw the held position rather than next frame's.
     */
    public static boolean anchor(CTMLLayout.Box box){
        boolean moved = false;
        if (box != null) {
            moved = hold(box);
            for (CTMLLayout.Box child : box.children()) moved |= anchor(child);
        }
        return moved;
    }

    private static boolean hold(CTMLLayout.Box box){
        DOMTreeElement element = box.element();
        CTMLLayout.Port port = box.port();
        if (port.maxScrollY() <= 0 || !anchors(box.style())) {
            element.setScrollAnchor(null, 0);
            return false;
        }

        // the child picked last time, as long as it is still one of this box's children. A scroll of its
        // own picks a new one, since what the user then looks at is somewhere else entirely
        DOMTreeElement held = element.getScrollAnchor();
        CTMLLayout.Box anchor = held == null ? null : childFor(box, held);
        if (anchor == null) {
            CTMLLayout.Box picked = anchorChild(box, port);
            element.setScrollAnchor(picked == null ? null : picked.element(),
                    picked == null ? 0 : showsAt(picked, port));
            return false;
        }

        // where the child shows through the scrollport, which is what must not move
        int was = element.getScrollAnchorOffset();
        int drift = showsAt(anchor, port) - was;
        if (drift == 0 || port.scrollY() == 0) return false;

        int scroll = Math.clamp(port.scrollY() + drift, 0, port.maxScrollY());
        if (scroll == port.scrollY()) return false;

        element.setScrollOffset(port.scrollX(), scroll); // at once: this is a correction, not a scroll
        element.setScrollAnchor(anchor.element(), was); // and the child is still meant to show there
        return true;
    }

    /** How far below the top of the scrollport a child is drawn. */
    private static int showsAt(CTMLLayout.Box child, CTMLLayout.Port port){
        return child.flowY() - port.y();
    }

    private static CTMLLayout.Box childFor(CTMLLayout.Box box, DOMTreeElement element){
        for (CTMLLayout.Box child : box.children()) if (child.element() == element) return child;
        return null;
    }

    /** The first child showing through the scrollport, which is the one worth holding on to. */
    private static CTMLLayout.Box anchorChild(CTMLLayout.Box box, CTMLLayout.Port port){
        for (CTMLLayout.Box child : box.children()) {
            if (child.isFixed() || child.offsetY() != 0) continue; // dragged, stuck or taken out of the flow
            if (child.bottom() > port.y()) return child;
        }
        return null;
    }

    /**
     * Scrolls every container above an element just far enough to bring it into view, which is css's
     * scrollIntoView. Each one is worked out from where things are now, so a container that moves
     * because one outside it scrolled is brought the rest of the way on the next frame.
     */
    public static boolean intoView(CTMLLayout.Box root, DOMTreeElement element){
        List<CTMLLayout.Box> path = path(root, element);
        if (path.size() < 2) return false;

        CTMLLayout.Box target = path.getLast();
        boolean moved = false;
        for (int i = 0; i < path.size() - 1; i++) {
            CTMLLayout.Box box = path.get(i);
            CTMLLayout.Port port = box.port();
            if (port.maxScrollX() <= 0 && port.maxScrollY() <= 0) continue;

            int dx = reveal(target.x(), target.right(), port.x(), port.right());
            int dy = reveal(target.y(), target.bottom(), port.y(), port.bottom());
            if (dx == 0 && dy == 0) continue;

            scrollTo(box,
                    Math.clamp(port.scrollX() + dx, 0, port.maxScrollX()),
                    Math.clamp(port.scrollY() + dy, 0, port.maxScrollY()));
            moved = true;
        }
        return moved;
    }

    /**
     * Brings into view whatever any script asked for, anywhere in the tree. A script sets the id it
     * wants on the container and the value is taken off it here, so it happens once rather than
     * every frame for as long as the property is set.
     */
    public static boolean intoViewPending(CTMLLayout.Box root){
        return root != null && pending(root, root);
    }

    private static boolean pending(CTMLLayout.Box root, CTMLLayout.Box box){
        boolean moved = false;

        String id = box.element().takeScrollInto();
        if (id != null && !id.isEmpty()) {
            List<DOMTreeElement> found = box.element().findByProperty("id", id);
            if (!found.isEmpty()) moved = intoView(root, found.getFirst());
        }

        for (CTMLLayout.Box child : box.children()) moved |= pending(root, child);
        return moved;
    }

    /**
     * How far the content has to move along one axis to bring a span inside the port: nothing when it
     * already is, and only as far as the nearer edge otherwise, so nothing scrolls further than it must.
     */
    private static int reveal(int start, int end, int portStart, int portEnd){
        if (start >= portStart && end <= portEnd) return 0;
        if (end - start > portEnd - portStart) return start - portStart; // too big to fit: line up the near edge
        return start < portStart ? start - portStart : end - portEnd;
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
