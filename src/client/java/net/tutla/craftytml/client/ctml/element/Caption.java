package net.tutla.craftytml.client.ctml.element;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/** A table's caption: a line of its own above the table, or below it with caption-bottom. */
public class Caption extends CTMLElement {
    private static final ElementStyle CAPTION_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.DISPLAY, ElementStyle.Display.TABLE_CAPTION)
            .set(ElementStyle.TEXT_ALIGN, ElementStyle.TextAlign.CENTER)
            .build();

    public Caption(){
        super("caption", CTMLElementProperties.newElementProperties(), true, CTMLTokenType.CAPTION, CTMLTokenType.CAPTION_CLOSING);
    }

    @Override
    public ElementStyle defaultStyle(){
        return CAPTION_DEFAULTS;
    }
}
