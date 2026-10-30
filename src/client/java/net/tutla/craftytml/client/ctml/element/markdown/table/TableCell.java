package net.tutla.craftytml.client.ctml.element.markdown.table;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/** One cell of a table. colspan and rowspan say how many of them it takes up. */
public class TableCell extends CTMLElement {
    private static final ElementStyle CELL_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.DISPLAY, ElementStyle.Display.TABLE_CELL)
            .build();

    public TableCell(){
        super("td", CTMLElementProperties.newElementProperties(), true, CTMLTokenType.TD, CTMLTokenType.TD_CLOSING);
    }

    @Override
    public ElementStyle defaultStyle(){
        return CELL_DEFAULTS;
    }
}
