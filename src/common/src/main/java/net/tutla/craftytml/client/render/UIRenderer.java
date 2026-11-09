package net.tutla.craftytml.client.render;

import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;

/**
 * Where a tree is shown, and the one part of CraftyTML that is different on every platform. Everything up
 * to here is the same anywhere: a tree from {@code CTMLAPI} and {@link UIRegistry}, a {@link DOMContext}
 * around it with its variables and callbacks. Handing the two to a renderer is the last step:
 *
 * <ul>
 *   <li>on Fabric, {@code ScreenRenderer} opens them as a {@code CTMLScreen};
 *   <li>on Paper and Spigot, {@code WindowRenderer} opens them as a window in front of one player.
 * </ul>
 */
public interface UIRenderer {
    /** Shows a tree with its context, in place of whatever this renderer is showing now. */
    void show(String name, DOMTreeElement tree, DOMContext ctx);

    /** Offers the viewer a link to follow, however this platform can. */
    void openLink(String url);
}
