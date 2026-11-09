package net.tutla.craftytml.client;

import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.UIRegistry;
import net.tutla.craftytml.client.tusan.CTMLTusan;
import net.tutla.tums.Tums;
import net.tutla.tums.tusan.MinecraftEnvironment;
import net.tutla.tums.tusan.Variable;
import net.tutla.tums.tusan.interpreter.Interpreter;
import net.tutla.tums.tusan.interpreter.InterpreterData;
import net.tutla.tums.tusan.lang.Tusan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The door into ctml: it runs a .ctml file, or markup held in a string, and puts the tree that comes
 * out of it in the {@link net.tutla.craftytml.client.render.UIRegistry} under a name.
 *
 * <p>Running a script is all this does. Nothing is shown by it: a tree becomes a screen or a hud only
 * when something shows it: a {@code CTMLScreen} or {@code CTMLHud} on Fabric, a
 * {@code CTMLWindows} window on Paper, and the same tree can be any of them. A script that fails is reported and skipped, so one bad file cannot stop the rest loading.
 *
 * <p>Scripts run as {@link MinecraftEnvironment#CLIENT} unless they are handed another environment, which
 * is how a server runs them as {@link MinecraftEnvironment#SERVER}.
 */
public class CTMLAPI {
    private static final CTMLTusan tusan = CTMLTusan.tusan();

    public static void runAllScripts(Path scriptsDir){
        runAllScripts(scriptsDir, MinecraftEnvironment.CLIENT);
    }

    public static void runAllScripts(Path scriptsDir, MinecraftEnvironment environment){
        try (Stream<Path> stream = Files.list(scriptsDir)) {
            stream.filter(Files::isRegularFile)
                    .forEach(script -> createUI(script, environment));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void createUI(Path script){
        createUI(script, MinecraftEnvironment.CLIENT);
    }

    public static void createUI(Path script, MinecraftEnvironment environment){
        Interpreter interpreter = setupInterpreter(null, script, environment);
        runScript(script.getFileName().toString(), interpreter);
    }

    public static void createUI(String name, String script){
        createUI(name, script, MinecraftEnvironment.CLIENT);
    }

    public static void createUI(String name, String script, MinecraftEnvironment environment){
        Interpreter interpreter = setupInterpreter(script, null, environment);
        runScript(name, interpreter);
    }

    /**
     * Runs a script for one viewer and hands back the tree it builds, or null where it builds none. The
     * variables are in the script's scope before its first line, which is how a server says who it is
     * running for. Nothing is registered: the tree is the caller's alone, so two viewers never share
     * one, and any event the script listens for is not heard, since that would hear it for everyone.
     */
    public static DOMTreeElement runFor(Path script, MinecraftEnvironment environment, Map<String, Variable> variables){
        Interpreter interpreter = new Interpreter(tusan);
        InterpreterData data = new InterpreterData(new HashMap<>(variables), null, null, null);
        interpreter.setup(data, null, null, script, environment);
        try {
            interpreter.compile();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return interpreter.returned instanceof DOMTreeElement tree ? tree : null;
    }

    private static Interpreter setupInterpreter(String script, Path scriptPath, MinecraftEnvironment environment){
        Interpreter interpreter = new Interpreter(tusan);
        if (script != null){
            interpreter.setup(null,null,script, null, environment);
        } else {
            interpreter.setup(null,null, null, scriptPath, environment);
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
