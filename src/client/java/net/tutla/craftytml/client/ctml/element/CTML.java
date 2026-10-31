package net.tutla.craftytml.client.ctml.element;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.style.Length;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * The root of a tree: html and body in one. It fills the screen it is drawn in, and the text
 * values everything below inherits bottom out here, so a tree always has something to inherit from.
 *
 * @ctml.group root
 * @ctml.properties none
 * @ctml.example <ctml class="w-full h-full bg-slate-900 text-slate-100"></ctml>
 */
public class CTML extends CTMLElement {
    /**
     * The document root, so this is html and body in one: it fills the screen, and the text values
     * everything below inherits bottom out in it. Those are {@link CTMLElement#INHERITED_INITIAL},
     * which layout seeds at whatever it is rooted at, so a tree with no ctml over it has them too.
     */
    private static final ElementStyle ROOT_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.WIDTH, Length.FULL)
            .set(ElementStyle.HEIGHT, Length.FULL)
            .set(ElementStyle.MARGIN, Length.ZERO)
            .set(ElementStyle.PADDING, Length.ZERO)
            .setAll(INHERITED_INITIAL)
            .build();

    public CTML(){
        super("ctml", CTMLElementProperties.newElementProperties(), true, CTMLTokenType.CTML, CTMLTokenType.CTML_CLOSING);
    }

    @Override
    public ElementStyle defaultStyle(){
        return ROOT_DEFAULTS;
    }
}
