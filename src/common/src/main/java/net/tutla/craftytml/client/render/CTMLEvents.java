package net.tutla.craftytml.client.render;

import net.tutla.craftytml.client.dom.CTMLCallback;
import net.tutla.craftytml.client.dom.CTMLCallback.CallbackType;
import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;

import java.util.List;

/**
 * What a press, a hover or a drag reaches: the innermost element under the cursor that names a
 * callback, or the nearest one above it that does. That is how a click on the text inside a button
 * still presses the button, the way an event bubbling up the tree does in a browser.
 *
 * <p>The name on the element is looked up in the screen's {@link DOMContext}, which is where the host
 * put its callbacks, and whatever it finds is handed a {@link CTMLCallback} saying what happened and
 * to what. An element that names a callback nobody registered is left alone: nothing happens, and
 * nothing is swallowed either, so the press carries on to whatever else wants it.
 *
 * <p>One name is one function, and the element has the one name, so that function hears about
 * everything that happens to the element: both clicks, the input, the cursor arriving over it and
 * every movement of a drag. Which of them it acts on is its own business, and {@link
 * CTMLCallback#isPress()} is the usual answer.
 */
public final class CTMLEvents {
    private CTMLEvents(){}

    /** The element a press at this spot would reach, or null where nothing there wants one. */
    public static DOMTreeElement at(CTMLLayout.Box root, DOMContext ctx, double x, double y){
        return at(CTMLFocus.path(root, x, y), ctx);
    }

    /**
     * The same, for a caller that has the chain of boxes under the cursor already: a screen works that
     * out once a frame, both for this and for the states the style variants ask about.
     */
    public static DOMTreeElement at(List<CTMLLayout.Box> under, DOMContext ctx){
        if (ctx == null) return null;
        for (int i = under.size() - 1; i >= 0; i--) {
            DOMTreeElement element = under.get(i).element();
            if (element.getCallback() != null) return element;
        }
        return null;
    }

    /**
     * Fires the mouse button's callback at whatever is under the cursor, and says whether anything
     * took it. A button takes a left press as an input rather than as a click, since pressing it is
     * the whole of what it is for.
     */
    public static boolean click(CTMLLayout.Box root, DOMContext ctx, int button, double x, double y){
        DOMTreeElement target = at(root, ctx, x, y);
        if (target == null) return false;

        CallbackType type = switch (button) {
            case MouseButton.LEFT -> target.firesInput() ? CallbackType.INPUT : CallbackType.LEFT_CLICK;
            case MouseButton.RIGHT -> CallbackType.RIGHT_CLICK;
            case MouseButton.MIDDLE -> CallbackType.MIDDLE_CLICK;
            default -> null;
        };
        return type != null && ctx.fire(type, target, CTMLCallback.NO_KEY);
    }

    /** Fires the focused element's input callback, for the keys that count as operating it. */
    public static boolean input(DOMContext ctx, DOMTreeElement focus, int keycode){
        return ctx != null && focus != null && focus.firesInput()
                && ctx.fire(CallbackType.INPUT, focus, keycode);
    }

    public static boolean hover(DOMContext ctx, DOMTreeElement element){
        return ctx != null && ctx.fire(CallbackType.HOVER, element, CTMLCallback.NO_KEY);
    }

    public static boolean drag(DOMContext ctx, DOMTreeElement element){
        return ctx != null && ctx.fire(CallbackType.DRAG, element, CTMLCallback.NO_KEY);
    }

    /** Sdl numbers the mouse buttons from one, so left is 1 and not 0 the way glfw had it. */
    public static final class MouseButton {
        public static final int LEFT = 1;
        public static final int MIDDLE = 2;
        public static final int RIGHT = 3;

        private MouseButton(){}
    }
}
