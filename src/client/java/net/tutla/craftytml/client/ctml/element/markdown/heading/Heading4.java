package net.tutla.craftytml.client.ctml.element.markdown.heading;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.style.Length;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/** A heading, level 4. Bold and bigger than the text around it, with room above and below. */
public class Heading4 extends CTMLElement {
    private static final ElementStyle OWN_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.DISPLAY, ElementStyle.Display.BLOCK)
            .set(ElementStyle.BOLD, true)
            .set(ElementStyle.FONT_SIZE, 18)
            .set(ElementStyle.MARGIN_TOP, Length.px(4))
            .set(ElementStyle.MARGIN_BOTTOM, Length.px(4))
            .build();

    public Heading4(){
        super("h4", CTMLElementProperties.newElementProperties(), true, CTMLTokenType.H4, CTMLTokenType.H4_CLOSING);
    }

    @Override
    public ElementStyle defaultStyle(){
        return OWN_DEFAULTS;
    }
}
