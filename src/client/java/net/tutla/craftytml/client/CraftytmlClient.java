package net.tutla.craftytml.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.dom.CTMLCallback;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.CTMLScreen;
import net.tutla.craftytml.client.render.CTMLTextures;
import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.render.UIRegistry;
import net.tutla.craftytml.client.tusan.CTMLTusan;
import net.tutla.tums.Tums;
import net.tutla.tums.tusan.MinecraftEnvironment;
import net.tutla.tums.tusan.interpreter.Interpreter;
import net.tutla.tums.tusan.lang.Tusan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class CraftytmlClient implements ClientModInitializer {
    Path gameDir = FabricLoader.getInstance().getGameDir();
    Path ctmlDir = gameDir.resolve("ctml/ui");

    private final CTMLTusan tusan = CTMLTusan.tusan();

    private static boolean openedFirstUI = false;

    @Override
    public void onInitializeClient() {
        resolveCTMLDir();
        CTMLTusan.init();

        runAllScripts(ctmlDir, tusan, MinecraftEnvironment.CLIENT);

        forgetTextureSizesOnReload();
        openFirstUIOnMainMenu();
    }


    public static void forgetTextureSizesOnReload(){
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                Identifier.fromNamespaceAndPath("craftytml", "texture-sizes"),
                new SimpleReloadListener<Void>() {
                    @Override
                    protected Void prepare(PreparableReloadListener.SharedState state){
                        return null;
                    }

                    @Override
                    protected void apply(Void nothing, PreparableReloadListener.SharedState state){
                        CTMLTextures.forget();
                    }
                });
    }

    public static void openFirstUIOnMainMenu(){
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (openedFirstUI || !(screen instanceof TitleScreen)) return;

            Map.Entry<String, DOMTreeElement> first = UIRegistry.getFirstUI();
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

    /**
     * And the other half of the demo: a counter that lives in the context as a variable, which the
     * script shows by writing ${cookies} in the middle of a string. Nothing here touches the element
     * that shows it, or knows which element that is: putting the variable is the whole of it, and
     * every string naming it is read again on the next frame.
     *
     * <p>A class list is a string like any other, so the colour of the word under the count is a
     * variable too, and a variable nobody has set yet reads as nothing at all.
     */
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

    public void resolveCTMLDir(){
        try {
            Files.createDirectories(ctmlDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void runAllScripts(Path scriptsDir, Tusan tusan, MinecraftEnvironment environment){
        try (Stream<Path> stream = Files.list(scriptsDir)) {
            stream.filter(Files::isRegularFile)
                    .forEach((p) -> {
                        runScript(p, tusan, environment);});
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void runScript(Path script, Tusan tusan, MinecraftEnvironment environment){
        Interpreter interpreter = new Interpreter(tusan);
        interpreter.setup(null,null,null, script, environment);

        try{
            interpreter.compile(); // where every script in the dir gets executed
        } catch (Exception e){
            e.printStackTrace();
        }

        Tums.getRegister().add(interpreter.data);

        if (interpreter.returned != null){
            UIRegistry.register(script.getFileName().toString(), (DOMTreeElement) interpreter.returned);
            System.out.println(((DOMTreeElement) interpreter.returned).render(" "));
        }
    }
}
