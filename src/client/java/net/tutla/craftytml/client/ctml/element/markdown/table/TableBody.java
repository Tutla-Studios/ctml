package net.tutla.craftytml.client.ctml.element.markdown.table;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/** The rows in the middle of a table, between the head and the foot. */
public class TableBody extends CTMLElement {
    private static final ElementStyle BODY_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.DISPLAY, ElementStyle.Display.TABLE_ROW_GROUP)
            .build();

    public TableBody(){
        super("tbody", CTMLElementProperties.newElementProperties(), true, CTMLTokenType.TBODY, CTMLTokenType.TBODY_CLOSING);
    }

    @Override
    public ElementStyle defaultStyle(){
        return BODY_DEFAULTS;
    }
}
