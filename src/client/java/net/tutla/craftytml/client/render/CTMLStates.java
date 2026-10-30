package net.tutla.craftytml.client.render;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.dom.DOMTreeElement;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * What the style variants about one element ask it: whether the cursor is over it, whether it has the
 * keyboard, and whether a button is being held on it. A screen keeps one of these and tells it what
 * it knows; the elements themselves keep the answers, since that is where a style is worked out.
 *
 * <p>Every state reaches the whole chain of boxes it happened in rather than only the innermost one,
 * the way css's :hover and :active land on an element and on everything it is inside, so hovering the
 * text in a button hovers the button. The focus is the one that does not: focus: is the element with
 * the keyboard and focus-within: is everything above it, which are two different questions.
 *
 * <p>Only what changed is said to anything, and an element says back whether it was worth saying: a
 * state no class on it asks about moves nothing, so a cursor crossing a plain page costs a compare per
 * box it passes and no layout at all.
 */
public final class CTMLStates {
    /** The chain the cursor is in, the chain a press landed in, and the focus with everything above it. */
    private List<DOMTreeElement> hovered = List.of();
    private List<DOMTreeElement> pressed = List.of();
    private List<DOMTreeElement> focused = List.of();

    /**
     * Where the cursor is, as the boxes it is inside, outermost first, which is what {@link
     * CTMLFocus#path} hands back. Says whether any style changed because of it.
     */
    public boolean cursor(List<CTMLLayout.Box> under){
        List<DOMTreeElement> now = elements(under);
        boolean moved = swap(hovered, now, CTMLElement::setHovered);
        hovered = now;
        return moved;
    }

    /** The same for a press, which holds the chain it landed in until it is let go of. */
    public boolean press(List<CTMLLayout.Box> under){
        List<DOMTreeElement> now = elements(under);
        boolean moved = swap(pressed, now, CTMLElement::setActive);
        pressed = now;
        return moved;
    }

    public boolean release(){
        return press(List.of());
    }

    /**
     * What the keyboard is talking to. The element itself is focused and everything above it has the
     * focus within it, which are the two things a class can ask about separately.
     */
    public boolean keyboard(DOMTreeElement focus){
        List<DOMTreeElement> now = new ArrayList<>();
        for (DOMTreeElement each = focus; each != null; each = each.getParent()) now.add(each);

        boolean moved = false;
        for (DOMTreeElement each : focused) {
            if (now.contains(each)) continue;
            moved |= each.getElement().setFocused(false);
            moved |= each.getElement().setFocusWithin(false);
        }
        for (DOMTreeElement each : now) {
            moved |= each.getElement().setFocused(each == focus);
            moved |= each.getElement().setFocusWithin(each != focus);
        }

        focused = now;
        return moved;
    }

    /** Everything back to how it started, for a screen that is being rebuilt or put away. */
    public void clear(){
        cursor(List.of());
        release();
        keyboard(null);
    }

    private static List<DOMTreeElement> elements(List<CTMLLayout.Box> boxes){
        List<DOMTreeElement> elements = new ArrayList<>(boxes.size());
        for (CTMLLayout.Box box : boxes) elements.add(box.element());
        return elements;
    }

    /**
     * Says the state to everything that has just come into the chain and takes it off everything that
     * has just left it, and says whether any of them cared. The chains are a handful of boxes deep,
     * so looking a box up in one is cheaper than keeping a set beside it.
     */
    private static boolean swap(List<DOMTreeElement> was, List<DOMTreeElement> now,
                                BiPredicate<CTMLElement, Boolean> set){
        boolean moved = false;
        for (DOMTreeElement each : was) if (!now.contains(each)) moved |= set.test(each.getElement(), false);
        for (DOMTreeElement each : now) if (!was.contains(each)) moved |= set.test(each.getElement(), true);
        return moved;
    }
}
