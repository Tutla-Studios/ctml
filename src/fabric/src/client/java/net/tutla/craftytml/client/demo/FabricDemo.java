package net.tutla.craftytml.client.demo;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.TitleScreen;
import net.tutla.craftytml.client.render.ScreenRenderer;
import net.tutla.craftytml.client.render.UIRenderer;

import java.util.function.Predicate;

/** Opens a demo on the title screen the first time it comes up: the only part of the demos that is Fabric's. */
public final class FabricDemo {
    private static boolean opened = false;

    private FabricDemo(){}

    public static void openIntroOnMainMenu(){
        onFirstTitleScreen(DemoIntro::open);
    }

    public static void openFirstUIOnMainMenu(){
        onFirstTitleScreen(DemoScreen::open);
    }

    private static void onFirstTitleScreen(Predicate<UIRenderer> open){
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (opened || !(screen instanceof TitleScreen)) return;
            opened = open.test(new ScreenRenderer(screen));
        });
    }
}
