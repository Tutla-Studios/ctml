package net.tutla.craftytml.client.ctml.element.markdown.paragraph;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/** A break in the middle of a line, which carries on underneath. */
public class LineBreak extends CTMLElement {
    private static final ElementStyle OWN_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.DISPLAY, ElementStyle.Display.INLINE)
            .build();

    public LineBreak(){
        super("br", CTMLElementProperties.newElementProperties(), false, CTMLTokenType.BR, CTMLTokenType.BR_CLOSING);
    }

    @Override
    public ElementStyle defaultStyle(){
        return OWN_DEFAULTS;
    }
}
