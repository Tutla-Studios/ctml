package net.tutla.craftytml.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.dom.DOMTreeNode;

/**
 * The same tree a {@link CTMLScreen} would open, drawn over the game instead of in front of it.
 * Nothing about the tree says which it is: the same ctml root, the same elements, the same styles
 * and the same interpreter behind all of it. Whichever of the two the host builds is the answer.
 *
 * <p>So this is that screen with everything the user could do to it taken out. A hud gets no press,
 * no wheel, no key and no cursor, which means:
 * <ul>
 *   <li>callbacks never fire, so a callback property on a hud element says nothing;
 *   <li>hover:, focus:, focus-visible:, focus-within: and active: never hold, there being nothing
 *       to tell the tree about the mouse or the keyboard;
 *   <li>a draggable box is never dragged, and a scrollbar is drawn but cannot be taken hold of.
 * </ul>
 * Everything a script drives still works: the variables a ${name} reads, a class list written by
 * hand, and scroll-x, scroll-y and scroll-into, which a hud animates the way a screen does.
 *
 * <p>A {@link DOMContext} is still what the tree is shown through, since that is where the variables
 * live. It just never has {@link DOMContext#fire} called on it from here.
 */
public class CTMLHud implements HudElement {
    private final DOMTreeElement domTreeHeadElement;
    private final DOMContext domCtx;

    /** The last frame's boxes, which is all there is to draw: nothing is ever tested against them. */
    private CTMLLayout.Box layout;

    /** What the tree and the window looked like when that was worked out, so it is only done again when it has to be. */
    private long revision = -1;
    private int laidOutWidth = -1;
    private int laidOutHeight = -1;

    /** When the last frame was drawn, so a smooth scroll moves by time rather than by frames. */
    private long drawnAt;

    public CTMLHud(DOMTreeElement domTreeHeadElement, DOMContext domCtx){
        this.domTreeHeadElement = domTreeHeadElement;
        this.domCtx = domCtx;
    }

    public DOMTreeElement getDomTreeHeadElement(){
        return domTreeHeadElement;
    }

    public DOMContext getDomCtx(){
        return domCtx;
    }

    /**
     * Layout only runs again once something has actually moved: a style, a property, the tree or a
     * scroll bumps the dom's revision, and a resize changes the window. Every other frame just draws
     * the boxes that are already worked out, which is the screen's own arrangement.
     *
     * <p>The window is read off the graphics rather than held, a hud being handed no size of its own:
     * it is the whole of the gui, in the same gui pixels the layout measures in.
     */
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker){
        if (domTreeHeadElement == null) return;

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        // anything part way through a smooth scroll moves on by however long the last frame took,
        // which marks the tree as changed and so brings it back through the layout below
        long now = System.nanoTime();
        float elapsed = drawnAt == 0 ? 0 : Math.min(0.25f, (now - drawnAt) / 1_000_000_000.0f);
        drawnAt = now;
        CTMLScroll.animate(layout, elapsed);

        long current = DOMTreeNode.revision();
        if (layout == null || current != revision || width != laidOutWidth || height != laidOutHeight) {
            layout = CTMLRenderer.layout(domTreeHeadElement, width, height);

            // what a script asked to be brought into view, and holding still whatever a resize above
            // it would otherwise have pushed out from under the user's eye. Both move a scroll offset,
            // so the tree is laid out once more and this frame draws where things end up
            if (CTMLScroll.intoViewPending(layout) | CTMLScroll.anchor(layout)) {
                layout = CTMLRenderer.layout(domTreeHeadElement, width, height);
            }

            laidOutWidth = width;
            laidOutHeight = height;
            // laying out clamps the scroll offsets it finds, which bumps the revision itself
            revision = DOMTreeNode.revision();
        }

        // no focus: there is nothing a hud can hand the keyboard to, so no ring is ever drawn
        CTMLRenderer.paint(layout, graphics);
    }
}
