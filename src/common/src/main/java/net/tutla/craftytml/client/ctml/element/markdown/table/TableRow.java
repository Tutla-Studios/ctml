package net.tutla.craftytml.client.ctml.element.markdown.table;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * One row of a table, which is as tall as the tallest cell on it.
 *
 * @ctml.group table
 * @ctml.properties none
 * @ctml.example <tr class="odd:bg-slate-800"></tr>
 */
public class TableRow extends CTMLElement {
    private static final ElementStyle ROW_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.DISPLAY, ElementStyle.Display.TABLE_ROW)
            .build();

    public TableRow(){
        super("tr", CTMLElementProperties.newElementProperties(), true, CTMLTokenType.TR, CTMLTokenType.TR_CLOSING);
    }

    @Override
    public ElementStyle defaultStyle(){
        return ROW_DEFAULTS;
    }
}
