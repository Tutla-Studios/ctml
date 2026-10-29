package net.tutla.craftytml.client.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.tutla.craftytml.client.dom.DOMTreeElement;

public class CTMLScreen extends Screen {
    private final DOMTreeElement domTreeHeadElement;
    private final Screen parent;

    /** The last frame's boxes, which is what the mouse gets tested against. */
    private CTMLLayout.Box layout;

    private DOMTreeElement dragged;
    private double dragX;
    private double dragY;
    private int minDragX;
    private int maxDragX;
    private int minDragY;
    private int maxDragY;

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

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && layout != null && grab(layout, null, event.x(), event.y())) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (dragged == null) return super.mouseDragged(event, deltaX, deltaY);

        dragX += deltaX;
        dragY += deltaY;
        dragged.setDragOffset(
                Math.clamp(Math.round(dragX), minDragX, maxDragX),
                Math.clamp(Math.round(dragY), minDragY, maxDragY));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragged != null) {
            dragged = null;
            return true;
        }
        return super.mouseReleased(event);
    }

    /**
     * Takes hold of the deepest draggable box under the cursor, if there is one.
     * Later siblings win, since those are the ones drawn over the earlier ones.
     */
    private boolean grab(CTMLLayout.Box box, CTMLLayout.Box parentBox, double mouseX, double mouseY){
        if (mouseX < box.x() || mouseX >= box.right() || mouseY < box.y() || mouseY >= box.bottom()) return false;

        boolean grabbed = false;
        for (CTMLLayout.Box child : box.children()) grabbed |= grab(child, box, mouseX, mouseY);
        if (grabbed) return true;

        if (parentBox == null || !box.element().isDraggable()) return false;
        startDrag(box, parentBox);
        return true;
    }

    /** The element can go anywhere inside its parent's content box, so that is where the limits come from. */
    private void startDrag(CTMLLayout.Box box, CTMLLayout.Box parentBox){
        dragged = box.element();
        dragX = dragged.getDragX();
        dragY = dragged.getDragY();

        int baseX = box.x() - dragged.getDragX(); // where layout would have put it, drag aside
        int baseY = box.y() - dragged.getDragY();

        minDragX = parentBox.contentX() - baseX;
        maxDragX = Math.max(minDragX, parentBox.contentRight() - box.width() - baseX);
        minDragY = parentBox.contentY() - baseY;
        maxDragY = Math.max(minDragY, parentBox.contentBottom() - box.height() - baseY);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(parent);
    }
}
