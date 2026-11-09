package net.tutla.craftytml.client.demo;

import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;

/**
 * What the demos need from wherever they are shown: a client shows them as screens, a server as a
 * window in front of one player. Everything else about them, their callbacks and their variables, is
 * the same on both.
 */
public interface DemoHost {
    /** The tree a ui script builds, or null where there is no such script. */
    DOMTreeElement tree(String name);

    /** Shows a tree in place of whatever this host is showing now. */
    void show(String name, DOMTreeElement tree, DOMContext ctx);

    /** Offers the viewer a link to follow. */
    void openLink(String url);
}
