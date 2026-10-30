package net.tutla.craftytml.client.ctml.element.markdown.paragraph;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/** A span of code. There is no font-family to switch to, so it keeps the spacing it was written with and leaves the look to the classes on it. */
public class Code extends CTMLElement {
    private static final ElementStyle OWN_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.DISPLAY, ElementStyle.Display.INLINE)
            .set(ElementStyle.WHITE_SPACE, ElementStyle.WhiteSpace.PRE)
            .build();

    public Code(){
        super("code", CTMLElementProperties.newElementProperties(), true, CTMLTokenType.CODE, CTMLTokenType.CODE_CLOSING);
    }

    @Override
    public ElementStyle defaultStyle(){
        return OWN_DEFAULTS;
    }
}
