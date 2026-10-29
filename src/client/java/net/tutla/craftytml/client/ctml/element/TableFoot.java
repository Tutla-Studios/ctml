package net.tutla.craftytml.client.ctml.element;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/** The rows at the bottom of a table, whatever order they were written in. */
public class TableFoot extends CTMLElement {
    private static final ElementStyle FOOT_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.DISPLAY, ElementStyle.Display.TABLE_FOOTER_GROUP)
            .build();

    public TableFoot(){
        super("tfoot", CTMLElementProperties.newElementProperties(), true, CTMLTokenType.TFOOT, CTMLTokenType.TFOOT_CLOSING);
    }

    @Override
    public ElementStyle defaultStyle(){
        return FOOT_DEFAULTS;
    }
}
