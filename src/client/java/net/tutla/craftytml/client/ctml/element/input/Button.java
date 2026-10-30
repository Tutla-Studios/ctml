package net.tutla.craftytml.client.ctml.element.input;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * Something to press. callback names a function the host put in the dom context, and a press on the
 * button, or enter or space while it has the focus, calls it with a {@link
 * net.tutla.craftytml.client.dom.CTMLCallback} of type INPUT.
 *
 * <p>The name is one function, which hears about everything that happens to the button and not only
 * the presses, so one that cares which is which asks the callback it is handed. Hovering a button is
 * not pressing it, and a function that acts on everything it is told would have it look as though it
 * were.
 *
 * <p>It is inline-block and centres its text, which is what a browser's button does, and it is in the
 * tab order without having to say so: pressing it is the only thing it is for, so the keyboard can
 * always reach it.
 */
public class Button extends CTMLElement {
    private static final ElementStyle BUTTON_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.DISPLAY, ElementStyle.Display.INLINE_BLOCK)
            .set(ElementStyle.TEXT_ALIGN, ElementStyle.TextAlign.CENTER)
            .build();

    public Button(){
        super("button", CTMLElementProperties.newElementProperties(), true,
                CTMLTokenType.BUTTON, CTMLTokenType.BUTTON_CLOSING);
    }

    @Override
    public ElementStyle defaultStyle(){
        return BUTTON_DEFAULTS;
    }

    /** In the tab order where it comes in the markup, the way a browser has a button. */
    @Override
    public int defaultTabIndex(){
        return 0;
    }

    @Override
    public boolean firesInput(){
        return true;
    }
}
