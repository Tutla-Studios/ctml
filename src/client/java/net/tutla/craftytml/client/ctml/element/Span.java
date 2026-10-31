package net.tutla.craftytml.client.ctml.element;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * A box that runs along the line with its neighbours instead of starting one of its own.
 *
 * @ctml.group text
 * @ctml.properties none
 * @ctml.example <span class="text-amber-400" text="gold"></span>
 */
public class Span extends CTMLElement {
    /** A span runs along the line with its neighbours instead of starting a new one. */
    private static final ElementStyle SPAN_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.DISPLAY, ElementStyle.Display.INLINE)
            .build();

    public Span(){
        super("span", CTMLElementProperties.newElementProperties(), true, CTMLTokenType.SPAN, CTMLTokenType.SPAN_CLOSING);
    }

    @Override
    public ElementStyle defaultStyle(){
        return SPAN_DEFAULTS;
    }
}
