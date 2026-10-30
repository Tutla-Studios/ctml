package net.tutla.craftytml.client.dom;

public class CTMLCallback {
    public enum CallbackType {
        LEFT_CLICK,
        RIGHT_CLICK,
        MIDDLE_CLICK,

        HOVER,

        INPUT, // inputs/buttons, buttons also fire for left click
        DRAG
    }

    /** What the keycode is when nothing on the keyboard set this off, which is every mouse event. */
    public static final int NO_KEY = 0;

    private final CallbackType callbackType;
    private final DOMTreeElement element;
    private final int keycode; // for inputs & buttons: what was pressed, or NO_KEY for the mouse
    public final DOMContext ctx;

    public CTMLCallback(CallbackType callbackType, DOMTreeElement element, DOMContext ctx){
        this(callbackType, element, ctx, NO_KEY);
    }

    public CTMLCallback(CallbackType callbackType, DOMTreeElement element, DOMContext ctx, int keycode){
        this.callbackType = callbackType;
        this.element = element;
        this.ctx = ctx;
        this.keycode = keycode;
    }

    public CallbackType getCallbackType(){
        return callbackType;
    }

    public DOMTreeElement getElement() {
        return element;
    }

    public DOMContext getCtx(){
        return ctx;
    }

    /** The key that set this off, or NO_KEY where the mouse did. */
    public int getKeycode(){
        return keycode;
    }

    /**
     * Whether this is the element being operated: a press on a button, or a left click on anything
     * else. One name is one function, and that function hears about everything that happens to the
     * element, the cursor merely arriving over it among them, so anything that only wants to be told
     * about a press has to say so. This is how it says it.
     */
    public boolean isPress(){
        return callbackType == CallbackType.INPUT || callbackType == CallbackType.LEFT_CLICK;
    }
}
