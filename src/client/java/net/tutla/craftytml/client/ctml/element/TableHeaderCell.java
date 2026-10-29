package net.tutla.craftytml.client.ctml.element;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/** A heading cell: a cell that comes bold and centred, the way html has it. */
public class TableHeaderCell extends CTMLElement {
    private static final ElementStyle HEADING_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.DISPLAY, ElementStyle.Display.TABLE_CELL)
            .set(ElementStyle.BOLD, true)
            .set(ElementStyle.TEXT_ALIGN, ElementStyle.TextAlign.CENTER)
            .build();

    public TableHeaderCell(){
        super("th", CTMLElementProperties.newElementProperties(), true, CTMLTokenType.TH, CTMLTokenType.TH_CLOSING);
    }

    @Override
    public ElementStyle defaultStyle(){
        return HEADING_DEFAULTS;
    }
}
