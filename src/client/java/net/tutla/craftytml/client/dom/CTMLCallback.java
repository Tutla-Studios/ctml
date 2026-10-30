package net.tutla.craftytml.client.dom;

import org.lwjgl.sdl.SDLKeycode;

public class CTMLCallback {
    public enum CallbackType {
        LEFT_CLICK,
        RIGHT_CLICK,
        MIDDLE_CLICK,

        HOVER,

        INPUT, // inputs/buttons, buttons also fire for left click
        DRAG
    }


    private final CallbackType callbackType;
    private final DOMTreeElement element;
    private SDLKeycode keycode; // for inputs & buttons
    public final DOMContext ctx;

    public CTMLCallback(CallbackType callbackType, DOMTreeElement element, DOMContext ctx){
        this.callbackType = callbackType;
        this.element = element;
        this.ctx = ctx;
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
}
