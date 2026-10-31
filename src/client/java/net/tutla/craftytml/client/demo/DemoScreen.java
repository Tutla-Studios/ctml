package net.tutla.craftytml.client.demo;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.TitleScreen;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.CTMLScreen;
import net.tutla.craftytml.client.render.UIRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DemoScreen {
    private static boolean openedFirstUI = false;
    private static final String DEMO_SCREEN_NAME = "demo_screen.ctml";

    public static void openFirstUIOnMainMenu(){
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (openedFirstUI || !(screen instanceof TitleScreen)) return;

            Map.Entry<String, DOMTreeElement> first = firstScreenUI();
            if (first == null) return;

            openedFirstUI = true;
            DOMContext ctx = new DOMContext(first.getValue());
            registerDemoCallbacks(ctx);
            registerCookieCounter(ctx);
            client.execute(() -> client.setScreenAndShow(new CTMLScreen(first.getKey(), first.getValue(), screen, ctx)));
        });
    }

    private static void registerDemoCallbacks(DOMContext ctx){
        ctx.putCallBack("togglePanel", callback -> {
            if (!callback.isPress()) return; // hovering the button is not pressing it

            DOMTreeElement panel = callback.getCtx().getElementById("callback-panel");
            if (panel == null) return;

            boolean showing = panel.getStyleValue(ElementStyle.DISPLAY) != ElementStyle.Display.NONE;
            panel.property("class", classes(panel, "hidden", showing), null);
            callback.getElement().property("text", showing ? "show the panel" : "hide the panel", null);
        });
    }

    private static void registerCookieCounter(DOMContext ctx){
        ctx.putVariable("cookies", 0);
        ctx.putVariable("cookieWord", "no cookies yet");
        ctx.putVariable("cookieMood", "text-slate-500");

        ctx.putCallBack("clickCookie", callback -> {
            if (!callback.isPress()) return;

            DOMContext context = callback.getCtx();
            int count = context.getVariableValue("cookies") instanceof Integer had ? had + 1 : 1;
            context.putVariable("cookies", count);
            context.putVariable("cookieWord", count == 1 ? "one cookie" : count + " cookies");
            context.putVariable("cookieMood", count < 5 ? "text-amber-300" : "text-amber-100");
        });
    }

    private static String classes(DOMTreeElement element, String className, boolean add){
        Object written = element.getPropertyValue("class");
        List<String> list = new ArrayList<>();
        if (written != null) {
            for (String each : written.toString().strip().split("\\s+")) {
                if (!each.isEmpty() && !each.equals(className)) list.add(each);
            }
        }
        if (add) list.add(className);
        return String.join(" ", list);
    }

    private static Map.Entry<String, DOMTreeElement> firstScreenUI(){
        return Map.entry(DEMO_SCREEN_NAME, UIRegistry.getUIFromName(DEMO_SCREEN_NAME));
    }
}
