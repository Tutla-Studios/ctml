package net.tutla.craftytml.client.ctml.element.markdown;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.property.Source;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * A picture. src names the texture, and the element comes out the size of that texture unless a width
 * or a height says otherwise; naming one of the two keeps the picture's own shape on the other.
 * object-fit says what happens when the box it ends up in is not the picture's shape.
 *
 * <p>It holds no children, and like every other element here it is still written with a closing tag.
 *
 * @ctml.group media
 * @ctml.properties src
 * @ctml.example <img src="minecraft:textures/block/stone.png" class="w-5 h-5 rounded-sm"></img>
 */
public class Img extends CTMLElement {
    /** Inline, the way html has it, so a picture sits in a line of text rather than breaking it. */
    private static final ElementStyle IMG_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.DISPLAY, ElementStyle.Display.INLINE_BLOCK)
            .set(ElementStyle.OBJECT_FIT, ElementStyle.ObjectFit.FILL) // css's own initial value
            .build();

    public Img(){
        super("img", CTMLElementProperties.newElementProperties(), false, CTMLTokenType.IMG, CTMLTokenType.IMG_CLOSING);
    }

    @Override
    public ElementStyle defaultStyle(){
        return IMG_DEFAULTS;
    }

    /** The texture named on the element, or null where none is. */
    public String source(){
        return resolve(propertyList.propertyFromName("src") instanceof Source src ? src.getSource() : null);
    }
}
