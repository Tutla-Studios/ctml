package net.tutla.craftytml.client.ctml.element.markdown.font;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/** Underlined, with nothing said about why. */
public class Underline extends CTMLElement {
    private static final ElementStyle OWN_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.DISPLAY, ElementStyle.Display.INLINE)
            .set(ElementStyle.UNDERLINE, true)
            .build();

    public Underline(){
        super("u", CTMLElementProperties.newElementProperties(), true, CTMLTokenType.U, CTMLTokenType.U_CLOSING);
    }

    @Override
    public ElementStyle defaultStyle(){
        return OWN_DEFAULTS;
    }
}
