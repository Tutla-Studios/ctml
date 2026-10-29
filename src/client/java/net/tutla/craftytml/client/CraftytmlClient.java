package net.tutla.craftytml.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.dom.DOMTreeNode;
import net.tutla.craftytml.client.render.UIRegistry;
import net.tutla.craftytml.client.tusan.CTMLTusan;
import net.tutla.tums.Tums;
import net.tutla.tums.tusan.MinecraftEnvironment;
import net.tutla.tums.tusan.interpreter.Interpreter;
import net.tutla.tums.tusan.lang.Tusan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class CraftytmlClient implements ClientModInitializer {
    Path gameDir = FabricLoader.getInstance().getGameDir();
    Path ctmlDir = gameDir.resolve("ctml/ui");

    private final CTMLTusan tusan = CTMLTusan.tusan();

    @Override
    public void onInitializeClient() {
        resolveCTMLDir();
        CTMLTusan.init();

        runAllScripts(ctmlDir, tusan, MinecraftEnvironment.CLIENT);
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
