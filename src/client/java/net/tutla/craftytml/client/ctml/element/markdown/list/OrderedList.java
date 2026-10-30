package net.tutla.craftytml.client.ctml.element.markdown.list;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.style.Length;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/** A list whose items are numbered. start says what to count from and reversed counts down. */
public class OrderedList extends CTMLElement {
    private static final ElementStyle OWN_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.DISPLAY, ElementStyle.Display.BLOCK)
            .set(ElementStyle.LIST_STYLE_TYPE, ElementStyle.ListStyle.DECIMAL)
            .set(ElementStyle.PADDING_LEFT, Length.px(10))
            .set(ElementStyle.MARGIN_TOP, Length.px(4))
            .set(ElementStyle.MARGIN_BOTTOM, Length.px(4))
            .build();

    public OrderedList(){
        super("ol", CTMLElementProperties.newElementProperties(), true, CTMLTokenType.OL, CTMLTokenType.OL_CLOSING);
    }

    @Override
    public ElementStyle defaultStyle(){
        return OWN_DEFAULTS;
    }
}
