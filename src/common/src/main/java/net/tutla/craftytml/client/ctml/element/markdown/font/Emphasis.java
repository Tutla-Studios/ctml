package net.tutla.craftytml.client.ctml.element.markdown.font;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * Italic, for a word leant on in the text.
 *
 * @ctml.group text
 * @ctml.properties none
 * @ctml.example <em text="leant on"></em>
 */
public class Emphasis extends CTMLElement {
    private static final ElementStyle OWN_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.DISPLAY, ElementStyle.Display.INLINE)
            .set(ElementStyle.ITALIC, true)
            .build();

    public Emphasis(){
        super("em", CTMLElementProperties.newElementProperties(), true, CTMLTokenType.EM, CTMLTokenType.EM_CLOSING);
    }

    @Override
    public ElementStyle defaultStyle(){
        return OWN_DEFAULTS;
    }
}
