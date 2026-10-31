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

public class DemoIntro {
    private static final String INTRO_UI = "intro.ctml";

    private static final String WIKI = "https://wiki.tutla.net/ctml";
    private static final String DISCORD = "https://discord.tutla.net";
    private static final String MODRINTH = "https://modrinth.com/mod/ctml";

    private static boolean opened = false;

    public static void openIntroOnMainMenu(){
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (opened || !(screen instanceof TitleScreen)) return;

            DOMTreeElement tree = UIRegistry.getUIFromName(INTRO_UI);
            if (tree == null) return;

            opened = true;
            DOMContext ctx = new DOMContext(tree);

            CTMLScreen intro = new CTMLScreen(INTRO_UI, tree, screen, ctx);
            registerLinks(ctx, intro);

            client.execute(() -> client.setScreenAndShow(intro));
        });
    }

    private static void registerLinks(DOMContext ctx, Screen intro){
        ctx.putVariable("linkWord", "built on tums, read by tusan, made by tutla");
        ctx.putVariable("linkMood", "text-slate-500");

        link(ctx, intro, "openWiki", WIKI, "the wiki");
        link(ctx, intro, "openDiscord", DISCORD, "the discord");
        link(ctx, intro, "openModrinth", MODRINTH, "modrinth");

        demo(ctx, intro);
    }

    private static void demo(DOMContext ctx, Screen intro){
        ctx.putCallBack("openDemo", callback -> {
            DOMContext context = callback.getCtx();

            if (!callback.isPress()) {
                context.putVariable("linkWord", "everything the engine draws, in one screen");
                context.putVariable("linkMood", "text-slate-400");
                return;
            }

            CTMLScreen demo = DemoScreen.build(intro);
            if (demo == null) { // the demo file is not in the ui folder, so say so and stay put
                context.putVariable("linkWord", "demo_screen.ctml is not in ctml/ui");
                context.putVariable("linkMood", "text-rose-400");
                return;
            }
            Minecraft.getInstance().setScreenAndShow(demo);
        });
    }

    private static void link(DOMContext ctx, Screen intro, String name, String url, String what){
        String shown = url.replaceFirst("^https://", "");

        ctx.putCallBack(name, callback -> {
            DOMContext context = callback.getCtx();

            if (!callback.isPress()) {
                context.putVariable("linkWord", shown);
                context.putVariable("linkMood", "text-slate-400");
                return;
            }

            context.putVariable("linkWord", "opening " + what);
            context.putVariable("linkMood", "text-emerald-300");
            ConfirmLinkScreen.confirmLinkNow(intro, URI.create(url));
        });
    }
}
