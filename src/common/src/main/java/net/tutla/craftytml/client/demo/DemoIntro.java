package net.tutla.craftytml.client.demo;

import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;

/** The intro, with its links and the way into the full demo, shown by whatever host asks for it. */
public class DemoIntro {
    public static final String INTRO_UI = "intro.ctml";

    private static final String WIKI = "https://wiki.tutla.net/ctml";
    private static final String DISCORD = "https://discord.tutla.net";
    private static final String MODRINTH = "https://modrinth.com/mod/ctml";

    /** Shows the intro, and says false where the host has no intro to show. */
    public static boolean open(DemoHost host){
        DOMTreeElement tree = host.tree(INTRO_UI);
        if (tree == null) return false;

        DOMContext ctx = new DOMContext(tree);
        registerLinks(ctx, host);
        host.show(INTRO_UI, tree, ctx);
        return true;
    }

    private static void registerLinks(DOMContext ctx, DemoHost host){
        ctx.putVariable("linkWord", "built on tums, read by tusan, made by tutla");
        ctx.putVariable("linkMood", "text-slate-500");

        link(ctx, host, "openWiki", WIKI, "the wiki");
        link(ctx, host, "openDiscord", DISCORD, "the discord");
        link(ctx, host, "openModrinth", MODRINTH, "modrinth");

        demo(ctx, host);
    }

    private static void demo(DOMContext ctx, DemoHost host){
        ctx.putCallBack("openDemo", callback -> {
            DOMContext context = callback.getCtx();

            if (!callback.isPress()) {
                context.putVariable("linkWord", "everything the engine draws, in one screen");
                context.putVariable("linkMood", "text-slate-400");
                return;
            }

            if (!DemoScreen.open(host)) { // the demo file is not in the ui folder, so say so and stay put
                context.putVariable("linkWord", "demo_screen.ctml is not in the ui folder");
                context.putVariable("linkMood", "text-rose-400");
            }
        });
    }

    private static void link(DOMContext ctx, DemoHost host, String name, String url, String what){
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
            host.openLink(url);
        });
    }
}
