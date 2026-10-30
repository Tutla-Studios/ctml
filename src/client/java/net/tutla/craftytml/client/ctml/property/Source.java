package net.tutla.craftytml.client.ctml.property;

import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementPropertyType;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/** The image an element shows, as the identifier a texture is held under: "craftytml:textures/gui/a.png". */
public class Source extends ElementProperty {
    public Source(){
        super("src", ElementPropertyType.STRING, CTMLTokenType.SRC);
    }

    public String getSource(){
        return getValue() instanceof String source && !source.isBlank() ? source.strip() : null;
    }
}
