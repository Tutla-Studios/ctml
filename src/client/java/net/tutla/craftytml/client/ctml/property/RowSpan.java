package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/** How many rows of its table a cell takes up, the rows under it making room for it. */
public class RowSpan extends ElementProperty {
    public RowSpan(){
        super("rowspan", ElementPropertyType.INTEGER, CTMLTokenType.ROWSPAN);
    }

    public int getSpan(){
        return getValue() instanceof Integer span && span > 0 ? span : 1;
    }
}
