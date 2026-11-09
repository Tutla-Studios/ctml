package net.tutla.craftytml.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;

import java.net.URI;

/**
 * The client's {@link UIRenderer}: a tree is opened as a {@link CTMLScreen}, over the screen that was
 * showing when it opened, and a link goes through minecraft's own confirm screen.
 */
public final class ScreenRenderer implements UIRenderer {
    /** What the next screen shown goes back to, where that is not simply whatever is showing now. */
    private Screen parent;

    /** Opens each tree over whatever screen is showing when it is shown. */
    public ScreenRenderer(){
        this(null);
    }

    /**
     * Opens the first tree over this screen, and every one after it over whatever is showing then: the
     * way to open something from a screen that is still being built, which is not showing yet.
     */
    public ScreenRenderer(Screen parent){
        this.parent = parent;
    }

    @Override
    public void show(String name, DOMTreeElement tree, DOMContext ctx){
        Minecraft client = Minecraft.getInstance();
        CTMLScreen screen = new CTMLScreen(name, tree, parent != null ? parent : client.gui.screen(), ctx);
        parent = null;
        client.execute(() -> client.setScreenAndShow(screen));
    }

    @Override
    public void openLink(String url){
        ConfirmLinkScreen.confirmLinkNow(Minecraft.getInstance().gui.screen(), URI.create(url));
    }
}
