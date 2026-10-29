package net.tutla.craftytml.client.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.tutla.craftytml.client.dom.DOMTreeElement;

public class CTMLScreen extends Screen {
    /** Sdl numbers the mouse buttons from one, so left is 1 and not 0 the way glfw had it. */
    private static final int LEFT_BUTTON = 1;

    private final DOMTreeElement domTreeHeadElement;
    private final Screen parent;

    /** The last frame's boxes, which is what the cursor gets tested against. */
    private CTMLLayout.Box layout;

    private CTMLDrag.Grip grip;

    public CTMLScreen(String name, DOMTreeElement domTreeHeadElement, Screen parent){
        super(Component.literal(name));
        this.domTreeHeadElement = domTreeHeadElement;
        this.parent = parent;
    }

    public DOMTreeElement getDomTreeHeadElement(){
        return domTreeHeadElement;
    }

    public Screen getParent(){
        return parent;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        layout = CTMLRenderer.renderScreen(domTreeHeadElement, this, graphics);
    }

    /** Takes hold of whatever draggable box the press landed on, and swallows the click if it found one. */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == LEFT_BUTTON) {
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
        if (grip == null) return super.mouseDragged(event, deltaX, deltaY);
        grip.follow(event.x(), event.y());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (grip != null && event.button() == LEFT_BUTTON) {
            grip = null;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(parent);
    }
}
