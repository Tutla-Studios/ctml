package net.tutla.craftytml.client.demo;

import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.element.input.TextArea;
import net.tutla.craftytml.client.dom.CTMLCallback;
import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;

import java.util.ArrayList;
import java.util.List;

/** The full demo, with its callbacks and its counter on it, shown by whatever host asks for it. */
public class DemoScreen {
    public static final String DEMO_SCREEN_NAME = "demo_screen.ctml";

    /**
     * Shows the demo, and says false where the file is not in the ui folder, so whoever asked for it can
     * leave the user where they are rather than showing an empty screen.
     */
    public static boolean open(DemoHost host){
        DOMTreeElement tree = host.tree(DEMO_SCREEN_NAME);
        if (tree == null) return false;

        DOMContext ctx = new DOMContext(tree);
        registerDemoCallbacks(ctx);
        registerCookieCounter(ctx);
        registerInputs(ctx);
        host.show(DEMO_SCREEN_NAME, tree, ctx);
        return true;
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

    private static void registerInputs(DOMContext ctx){
        ctx.putVariable("inputSays", "touch an input");

        ctx.putCallBack("inputChanged", callback -> {
            // the same name hears the cursor arrive too, and only a change is worth telling of
            if (callback.getCallbackType() != CTMLCallback.CallbackType.INPUT) return;

            DOMTreeElement input = callback.getElement();
            if (input.getElement() instanceof TextArea area) { // a textarea can say a good deal about itself
                TextArea.Details d = area.details();
                callback.getCtx().putVariable("inputSays", "textarea: " + d.lines() + " lines, " + d.words()
                        + " words, " + d.length() + " chars, at " + d.row() + ":" + d.column());
                return;
            }
            String kind = input.getTag().equals("input") ? String.valueOf(input.getPropertyValue("type")) : input.getTag();
            String now = kind.equals("checkbox")
                    ? (Boolean.TRUE.equals(input.getPropertyValue("checked")) ? "ticked" : "unticked")
                    : String.valueOf(input.getPropertyValue("value"));
            callback.getCtx().putVariable("inputSays", kind + ": " + now);
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
}
