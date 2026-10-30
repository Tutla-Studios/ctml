package net.tutla.craftytml.client.ctml.element.markdown.list;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/** One item of a list, which draws the marker its list asks for. value sets its number. */
public class ListItem extends CTMLElement {
    private static final ElementStyle OWN_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.DISPLAY, ElementStyle.Display.LIST_ITEM)
            .build();

    public ListItem(){
        super("li", CTMLElementProperties.newElementProperties(), true, CTMLTokenType.LI, CTMLTokenType.LI_CLOSING);
    }

    @Override
    public ElementStyle defaultStyle(){
        return OWN_DEFAULTS;
    }
}
