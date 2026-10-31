import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.CTMLLayout;
import net.tutla.craftytml.client.tusan.CTMLTusan;
import net.tutla.tums.tusan.MinecraftEnvironment;
import net.tutla.tums.tusan.interpreter.Interpreter;

import java.nio.file.Path;
import java.util.List;

/** How long the real demo takes to lay out, with a change deep in it and with one at the root. */
public class Bench {
    public static void main(String[] args) throws Exception {
        CTMLTusan tusan = CTMLTusan.tusan();
        Interpreter interpreter = new Interpreter(tusan);
        interpreter.setup(null, null, null, Path.of(args[0]), MinecraftEnvironment.CLIENT);
        interpreter.compile();
        DOMTreeElement root = (DOMTreeElement) interpreter.returned;

        List<DOMTreeElement> all = root.find("div");
        DOMTreeElement leaf = all.get(all.size() - 1);

        // run each case twice, in both orders, so the jit does not decide the answer
        time("nothing     ", root, null, 400);
        time("one leaf    ", root, leaf, 400);
        time("whole tree  ", root, root, 400);
        time("one leaf    ", root, leaf, 400);
        time("nothing     ", root, null, 400);
    }

    static void time(String what, DOMTreeElement root, DOMTreeElement changed, int passes){
        for (int i = 0; i < 20; i++) pass(root, changed);
        long start = System.nanoTime();
        for (int i = 0; i < passes; i++) pass(root, changed);
        long each = (System.nanoTime() - start) / passes / 1000;
        System.out.println(what + " changed: " + each + "us a layout");
    }

    static void pass(DOMTreeElement root, DOMTreeElement changed){
        if (changed != null) changed.getElement().updateStyle("min-width", null); // nothing but a new revision
        new CTMLLayout(new Harness.Stub(), 854, 480).layout(root, 0, 0, 854, 480);
    }
}
