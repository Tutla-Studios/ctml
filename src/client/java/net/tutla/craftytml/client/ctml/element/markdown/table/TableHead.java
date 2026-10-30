package net.tutla.craftytml.client.ctml.element.markdown.table;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/** The rows at the top of a table, whatever order they were written in. */
public class TableHead extends CTMLElement {
    private static final ElementStyle HEAD_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.DISPLAY, ElementStyle.Display.TABLE_HEADER_GROUP)
            .build();

    public TableHead(){
        super("thead", CTMLElementProperties.newElementProperties(), true, CTMLTokenType.THEAD, CTMLTokenType.THEAD_CLOSING);
    }

    @Override
    public ElementStyle defaultStyle(){
        return HEAD_DEFAULTS;
    }
}
