package net.tutla.craftytml.client;

import net.tutla.craftytml.client.dom.DOMTreeElement;
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

public class CTMLAPI {
    private static final CTMLTusan tusan = CTMLTusan.tusan();

    public static void runAllScripts(Path scriptsDir){
        try (Stream<Path> stream = Files.list(scriptsDir)) {
            stream.filter(Files::isRegularFile)
                    .forEach(CTMLAPI::createUI);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void createUI(Path script){
        Interpreter interpreter = setupInterpreter(null, script);
        runScript(script.getFileName().toString(), interpreter);
    }

    public static void createUI(String name, String script){
        Interpreter interpreter = setupInterpreter(script, null);
        runScript(name, interpreter);
    }

    private static Interpreter setupInterpreter(String script, Path scriptPath){
        Interpreter interpreter = new Interpreter(tusan);
        if (script != null){
            interpreter.setup(null,null,script, null, MinecraftEnvironment.CLIENT);
        } else {
            interpreter.setup(null,null, null, scriptPath, MinecraftEnvironment.CLIENT);
        }

        return interpreter;
    }

    private static void runScript(String name, Interpreter interpreter){
        try{
            interpreter.compile(); // where every script in the dir gets executed
        } catch (Exception e){
            e.printStackTrace();
        }

        Tums.getRegister().add(interpreter.data);

        if (interpreter.returned != null){
            UIRegistry.register(name, (DOMTreeElement) interpreter.returned);
        }
    }
}
