package net.tutla.craftytml.client.ctml.element.markdown.font;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/** Struck through, for what no longer holds. */
public class Strike extends CTMLElement {
    private static final ElementStyle OWN_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.DISPLAY, ElementStyle.Display.INLINE)
            .set(ElementStyle.STRIKETHROUGH, true)
            .build();

    public Strike(){
        super("s", CTMLElementProperties.newElementProperties(), true, CTMLTokenType.S, CTMLTokenType.S_CLOSING);
    }

    @Override
    public ElementStyle defaultStyle(){
        return OWN_DEFAULTS;
    }
}
