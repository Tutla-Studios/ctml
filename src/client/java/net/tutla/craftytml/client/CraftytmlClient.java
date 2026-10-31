package net.tutla.craftytml.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.tutla.craftytml.client.demo.Demo;
import net.tutla.craftytml.client.demo.DemoHUD;
import net.tutla.craftytml.client.demo.DemoScreen;
import net.tutla.craftytml.client.render.CTMLTextures;
import net.tutla.craftytml.client.tusan.CTMLTusan;
import net.tutla.tums.tusan.MinecraftEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CraftytmlClient implements ClientModInitializer {
    Path gameDir = FabricLoader.getInstance().getGameDir();
    Path ctmlDir = gameDir.resolve("ctml/ui");

    private final CTMLTusan tusan = CTMLTusan.tusan();

    @Override
    public void onInitializeClient() {
        resolveCTMLDir();
        CTMLTusan.init();

        CTMLAPI.runAllScripts(ctmlDir, tusan, MinecraftEnvironment.CLIENT); // run and register all client ones

        forgetTextureSizesOnReload();

        if (Demo.doDemo){
            DemoScreen.openFirstUIOnMainMenu();
            DemoHUD.addFpsCounterHud();
        }
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

    public void resolveCTMLDir(){
        try {
            Files.createDirectories(ctmlDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
