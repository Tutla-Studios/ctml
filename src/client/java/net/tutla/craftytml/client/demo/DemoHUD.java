package net.tutla.craftytml.client.demo;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.CTMLHud;
import net.tutla.craftytml.client.render.UIRegistry;

import java.util.Map;

public class DemoHUD {
    private static final String FPS_COUNTER_UI = "fpscounter.ctml";

    public static void addFpsCounterHud(){
        DOMTreeElement tree = UIRegistry.getUIFromName(FPS_COUNTER_UI);
        if (tree == null) return;

        DOMContext ctx = new DOMContext(tree);
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("craftytml", "fps-counter"),
                new CTMLHud(tree, ctx));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            int fps = client.getFps();
            ctx.putVariable("fps", fps);
            ctx.putVariable("fpsMood", fps < 30 ? "text-rose-400" : fps < 60 ? "text-amber-300" : "text-emerald-300");
        });
    }
}
