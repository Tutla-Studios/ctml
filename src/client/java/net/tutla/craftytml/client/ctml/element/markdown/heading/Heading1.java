package net.tutla.craftytml.client.ctml.element.markdown.heading;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.style.Length;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/** A heading, the largest. Bold and bigger than the text around it, with room above and below. */
public class Heading1 extends CTMLElement {
    private static final ElementStyle OWN_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.DISPLAY, ElementStyle.Display.BLOCK)
            .set(ElementStyle.BOLD, true)
            .set(ElementStyle.FONT_SIZE, 32)
            .set(ElementStyle.MARGIN_TOP, Length.px(6))
            .set(ElementStyle.MARGIN_BOTTOM, Length.px(6))
            .build();

    public Heading1(){
        super("h1", CTMLElementProperties.newElementProperties(), true, CTMLTokenType.H1, CTMLTokenType.H1_CLOSING);
    }

    @Override
    public ElementStyle defaultStyle(){
        return OWN_DEFAULTS;
    }
}
