package net.tutla.craftytml.client.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.dom.DOMTreeNode;

public class CTMLScreen extends Screen {
    /** Sdl numbers the mouse buttons from one, so left is 1 and not 0 the way glfw had it. */
    private static final int LEFT_BUTTON = 1;

    private final DOMTreeElement domTreeHeadElement;
    private final DOMContext domCtx;
    private final Screen parent;

    /** The last frame's boxes, which is what gets drawn and what the cursor gets tested against. */
    private CTMLLayout.Box layout;

    /** What the tree and the screen looked like when that was worked out, so it is only done again when it has to be. */
    private long revision = -1;
    private int laidOutWidth = -1;
    private int laidOutHeight = -1;

    private CTMLDrag.Grip grip;

    /** A scrollbar thumb the user has hold of, which takes the press before any draggable box does. */
    private CTMLScroll.Grip bar;

    public CTMLScreen(String name, DOMTreeElement domTreeHeadElement, Screen parent, DOMContext domCtx){
        super(Component.literal(name));
        this.domTreeHeadElement = domTreeHeadElement;
        this.parent = parent;
        this.domCtx = domCtx;
    }

    public DOMTreeElement getDomTreeHeadElement(){
        return domTreeHeadElement;
    }

    public Screen getParent(){
        return parent;
    }

    /**
     * Layout only runs again once something has actually moved: a style, a property, the tree, a drag or a
     * scroll bumps the dom's revision, and a resize changes the screen. Every other frame just draws the
     * boxes that are already worked out.
     */
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        long current = DOMTreeNode.revision();
        if (layout == null || current != revision || width != laidOutWidth || height != laidOutHeight) {
            layout = CTMLRenderer.layout(domTreeHeadElement, width, height);
            laidOutWidth = width;
            laidOutHeight = height;
            // laying out clamps the scroll and drag offsets it finds, which bumps the revision itself
            revision = DOMTreeNode.revision();
        }

        CTMLRenderer.paint(layout, graphics);
    }

    /** Takes hold of whatever draggable box the press landed on, and swallows the click if it found one. */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == LEFT_BUTTON) {
            bar = CTMLScroll.grab(layout, event.x(), event.y()); // the bars are drawn over everything in their box
            if (bar != null) return true;
            grip = CTMLDrag.grab(layout, event.x(), event.y());
            if (grip != null) return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /**
     * The element is put where the cursor is rather than nudged by the deltas this also gets handed,
     * so a movement that arrives late or lands outside what the element is allowed cannot leave it
     * trailing behind the pointer. The event's position is in the same gui pixels as the layout.
     */
    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (bar != null) {
            bar.follow(event.x(), event.y());
            return true;
        }
        if (grip == null) return super.mouseDragged(event, deltaX, deltaY);
        grip.follow(event.x(), event.y());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if ((grip != null || bar != null) && event.button() == LEFT_BUTTON) {
            grip = null;
            bar = null;
            return true;
        }
        return super.mouseReleased(event);
    }

    /** The wheel goes to the innermost scrollable box under the cursor, and outwards from there. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (CTMLScroll.wheel(layout, mouseX, mouseY, scrollX, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(parent);
    }
}
