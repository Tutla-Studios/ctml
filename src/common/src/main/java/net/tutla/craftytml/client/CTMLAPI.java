package net.tutla.craftytml.client;

import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.UIRegistry;
import net.tutla.craftytml.client.tusan.CTMLTusan;
import net.tutla.tums.Tums;
import net.tutla.tums.tusan.MinecraftEnvironment;
import net.tutla.tums.tusan.Variable;
import net.tutla.tums.tusan.interpreter.Interpreter;
import net.tutla.tums.tusan.interpreter.InterpreterData;

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
 * <p>Running a script is all this does, and it does it the same on every platform. Nothing is shown by it:
 * a tree is shown when it is handed to a {@link net.tutla.craftytml.client.render.UIRenderer}, which is
 * the one part that is different on a client and on a server. A script that fails is reported and
 * skipped, so one bad file cannot stop the rest loading.
 *
 * <p>Scripts run as {@link MinecraftEnvironment#CLIENT} unless the platform says otherwise once, with
 * {@link #setEnvironment}, which is how a server runs them as {@link MinecraftEnvironment#SERVER}.
 */
public class CTMLAPI {
    private static final CTMLTusan tusan = CTMLTusan.tusan();

    private static MinecraftEnvironment environment = MinecraftEnvironment.CLIENT;

    /** Which side every script runs on from here on: set once by the platform as it starts. */
    public static void setEnvironment(MinecraftEnvironment side){
        environment = side;
    }

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

    /**
     * Runs a script and hands back the tree it builds, without registering it, or null where it builds
     * none. Every call is a tree of its own, which is what a server wants when each player is to have
     * theirs. The variables are in the script's scope before its first line, so a script can be told who
     * it is running for. Any event the script listens for is not heard, since nothing registers it.
     */
    public static DOMTreeElement buildUI(Path script, Map<String, Object> variables){
        return build(null, script, variables);
    }

    /** The same, for markup held in a string rather than a file. */
    public static DOMTreeElement buildUI(String markup, Map<String, Object> variables){
        return build(markup, null, variables);
    }

    private static DOMTreeElement build(String markup, Path script, Map<String, Object> variables){
        HashMap<String, Variable> scope = new HashMap<>();
        variables.forEach((name, value) -> scope.put(name, new Variable(name, value, null)));

        Interpreter interpreter = new Interpreter(tusan);
        interpreter.setup(new InterpreterData(scope, null, null, null), null, markup, script, environment);
        try {
            interpreter.compile();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return interpreter.returned instanceof DOMTreeElement tree ? tree : null;
    }

    private static Interpreter setupInterpreter(String script, Path scriptPath){
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
