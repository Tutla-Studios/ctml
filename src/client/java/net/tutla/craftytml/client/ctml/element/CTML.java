package net.tutla.craftytml.client.ctml.element;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.style.Length;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

public class CTML extends CTMLElement {
    /**
     * The document root, so this is html and body in one: it fills the screen and holds the text
     * values everything below inherits, since there is nothing further up to get them from.
     */
    private static final ElementStyle ROOT_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.WIDTH, Length.FULL)
            .set(ElementStyle.HEIGHT, Length.FULL)
            .set(ElementStyle.MARGIN, Length.ZERO)
            .set(ElementStyle.PADDING, Length.ZERO)
            .set(ElementStyle.COLOR, 0xFFFFFFFF)
            .set(ElementStyle.FONT_SIZE, 16)
            .set(ElementStyle.TEXT_ALIGN, ElementStyle.TextAlign.LEFT)
            .set(ElementStyle.TEXT_SHADOW, true) // minecraft draws text with a shadow unless told not to
            .set(ElementStyle.WHITE_SPACE, ElementStyle.WhiteSpace.NORMAL)
            .set(ElementStyle.VISIBILITY, ElementStyle.Visibility.VISIBLE)
            .build();

    public CTML(){
        super("ctml", CTMLElementProperties.newElementProperties(), true, CTMLTokenType.CTML, CTMLTokenType.CTML_CLOSING);
    }

    @Override
    public ElementStyle defaultStyle(){
        return ROOT_DEFAULTS;
    }
}
