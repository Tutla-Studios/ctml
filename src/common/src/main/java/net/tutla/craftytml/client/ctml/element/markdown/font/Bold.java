package net.tutla.craftytml.client.ctml.element.markdown.font;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * Bold, with nothing said about why.
 *
 * @ctml.group text
 * @ctml.properties none
 * @ctml.example <b class="text-sky-300" text="flex"></b>
 */
public class Bold extends CTMLElement {
    private static final ElementStyle OWN_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.DISPLAY, ElementStyle.Display.INLINE)
            .set(ElementStyle.BOLD, true)
            .build();

    public Bold(){
        super("b", CTMLElementProperties.newElementProperties(), true, CTMLTokenType.B, CTMLTokenType.B_CLOSING);
    }

    @Override
    public ElementStyle defaultStyle(){
        return OWN_DEFAULTS;
    }
}
