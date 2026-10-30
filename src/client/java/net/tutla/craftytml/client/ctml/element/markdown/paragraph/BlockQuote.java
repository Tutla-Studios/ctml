package net.tutla.craftytml.client.ctml.element.markdown.paragraph;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.style.Length;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/** A quoted block, set in from the text around it behind a line down its left. */
public class BlockQuote extends CTMLElement {
    private static final ElementStyle OWN_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.DISPLAY, ElementStyle.Display.BLOCK)
            .set(ElementStyle.BORDER_LEFT_WIDTH, 2)
            .set(ElementStyle.PADDING_LEFT, Length.px(4))
            .set(ElementStyle.MARGIN_TOP, Length.px(4))
            .set(ElementStyle.MARGIN_BOTTOM, Length.px(4))
            .build();

    public BlockQuote(){
        super("blockquote", CTMLElementProperties.newElementProperties(), true, CTMLTokenType.BLOCKQUOTE, CTMLTokenType.BLOCKQUOTE_CLOSING);
    }

    @Override
    public ElementStyle defaultStyle(){
        return OWN_DEFAULTS;
    }
}
