package net.tutla.craftytml.client.ctml.element;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/** A table: rows down, cells across, and columns as wide as what is in them. */
public class Table extends CTMLElement {
    private static final ElementStyle TABLE_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.DISPLAY, ElementStyle.Display.TABLE)
            .build();

    public Table(){
        super("table", CTMLElementProperties.newElementProperties(), true, CTMLTokenType.TABLE, CTMLTokenType.TABLE_CLOSING);
    }

    @Override
    public ElementStyle defaultStyle(){
        return TABLE_DEFAULTS;
    }
}
