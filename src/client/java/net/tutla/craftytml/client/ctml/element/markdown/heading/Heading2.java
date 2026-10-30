package net.tutla.craftytml.client.ctml.element.markdown.heading;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.style.Length;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/** A heading, level 2. Bold and bigger than the text around it, with room above and below. */
public class Heading2 extends CTMLElement {
    private static final ElementStyle OWN_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.DISPLAY, ElementStyle.Display.BLOCK)
            .set(ElementStyle.BOLD, true)
            .set(ElementStyle.FONT_SIZE, 24)
            .set(ElementStyle.MARGIN_TOP, Length.px(5))
            .set(ElementStyle.MARGIN_BOTTOM, Length.px(5))
            .build();

    public Heading2(){
        super("h2", CTMLElementProperties.newElementProperties(), true, CTMLTokenType.H2, CTMLTokenType.H2_CLOSING);
    }

    @Override
    public ElementStyle defaultStyle(){
        return OWN_DEFAULTS;
    }
}
