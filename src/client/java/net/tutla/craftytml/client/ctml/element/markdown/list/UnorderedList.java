package net.tutla.craftytml.client.ctml.element.markdown.list;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.style.Length;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/** A list whose items are marked with a shape. list-disc by default, like html. */
public class UnorderedList extends CTMLElement {
    private static final ElementStyle OWN_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.DISPLAY, ElementStyle.Display.BLOCK)
            .set(ElementStyle.LIST_STYLE_TYPE, ElementStyle.ListStyle.DISC)
            .set(ElementStyle.PADDING_LEFT, Length.px(10))
            .set(ElementStyle.MARGIN_TOP, Length.px(4))
            .set(ElementStyle.MARGIN_BOTTOM, Length.px(4))
            .build();

    public UnorderedList(){
        super("ul", CTMLElementProperties.newElementProperties(), true, CTMLTokenType.UL, CTMLTokenType.UL_CLOSING);
    }

    @Override
    public ElementStyle defaultStyle(){
        return OWN_DEFAULTS;
    }
}
