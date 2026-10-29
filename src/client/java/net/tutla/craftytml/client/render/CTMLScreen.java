package net.tutla.craftytml.client.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.tutla.craftytml.client.dom.DOMTreeElement;

public class CTMLScreen extends Screen {
    private final DOMTreeElement domTreeHeadElement;
    private final Screen parent;

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
        CTMLRenderer.renderScreen(domTreeHeadElement, this, graphics);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(parent);
    }
}
