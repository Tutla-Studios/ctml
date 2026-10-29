package net.tutla.craftytml.client.render;

import net.minecraft.client.gui.screens.Screen;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.dom.DOMTreeElement;

public class CTMLRenderer {
    public static void renderScreen(DOMTreeElement domTreeHeadElement, Screen screen){
        render(domTreeHeadElement);
    }

    public static void render(DOMTreeElement domHeadElement){
        CTMLElementProperties properties = domHeadElement.getElement().propertyList;
    }
}
