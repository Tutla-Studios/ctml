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
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.CTMLScreen;
import net.tutla.craftytml.client.render.CTMLTextures;
import net.tutla.craftytml.client.render.DOMContext;
import net.tutla.craftytml.client.render.UIRegistry;
import net.tutla.craftytml.client.tusan.CTMLTusan;
import net.tutla.tums.Tums;
import net.tutla.tums.tusan.MinecraftEnvironment;
import net.tutla.tums.tusan.interpreter.Interpreter;
import net.tutla.tums.tusan.lang.Tusan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    /**
     * A background image is measured once and the size kept, since nothing on the gui side will say how
     * big a texture is. A resource reload can swap the image for one a different size, so the sizes are
     * dropped whenever the packs are read again.
     */
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
            client.execute(() -> client.setScreenAndShow(new CTMLScreen(first.getKey(), first.getValue(), screen, ctx)));
        });
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
