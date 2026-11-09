package net.tutla.craftytml.client.demo;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.CTMLScreen;
import net.tutla.craftytml.client.render.UIRegistry;

import java.net.URI;
import java.util.function.Predicate;

/**
 * The demos on a client: shown as screens off the trees the ui folder registered, each one over the
 * screen that was showing when it opened, and opened on the title screen the first time it comes up.
 */
public final class FabricDemo implements DemoHost {
    private static boolean opened = false;

    /** What the next screen shown goes back to, where that is not simply whatever is showing now. */
    private Screen parent;

    private FabricDemo(Screen parent){
        this.parent = parent;
    }

    public static void openIntroOnMainMenu(){
        onFirstTitleScreen(DemoIntro::open);
    }

    public static void openFirstUIOnMainMenu(){
        onFirstTitleScreen(DemoScreen::open);
    }

    private static void onFirstTitleScreen(Predicate<DemoHost> open){
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (opened || !(screen instanceof TitleScreen)) return;
            opened = open.test(new FabricDemo(screen));
        });
    }

    @Override
    public DOMTreeElement tree(String name){
        return UIRegistry.getUIFromName(name);
    }

    @Override
    public void show(String name, DOMTreeElement tree, DOMContext ctx){
        Minecraft client = Minecraft.getInstance();
        CTMLScreen screen = new CTMLScreen(name, tree, parent != null ? parent : client.gui.screen(), ctx);
        parent = null; // anything shown from here on goes back to the screen it was opened from
        client.execute(() -> client.setScreenAndShow(screen));
    }

    @Override
    public void openLink(String url){
        ConfirmLinkScreen.confirmLinkNow(Minecraft.getInstance().gui.screen(), URI.create(url));
    }
}
