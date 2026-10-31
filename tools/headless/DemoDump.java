import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.CTMLLayout;
import net.tutla.craftytml.client.tusan.CTMLTusan;
import net.tutla.tums.tusan.MinecraftEnvironment;
import net.tutla.tums.tusan.interpreter.Interpreter;

import java.nio.file.Path;

/** Lays a real .ctm file out and prints the tree, so the demo can be eyeballed without the game. */
public class DemoDump {
    public static void main(String[] args) throws Exception {
        CTMLTusan tusan = CTMLTusan.tusan();
        Interpreter interpreter = new Interpreter(tusan);
        interpreter.setup(null, null, null, Path.of(args[0]), MinecraftEnvironment.CLIENT);
        interpreter.compile();
        DOMTreeElement root = (DOMTreeElement) interpreter.returned;

        int width = args.length > 1 ? Integer.parseInt(args[1]) : 854;
        int height = args.length > 2 ? Integer.parseInt(args[2]) : 480;
        CTMLLayout.Box box = new CTMLLayout(new Harness.Stub(), width, height).layout(root, 0, 0, width, height);
        System.out.println("viewport " + width + "x" + height);
        dump(box, "");
    }

    static void dump(CTMLLayout.Box box, String indent){
        if (box == null) { System.out.println(indent + "(null)"); return; }
        StringBuilder note = new StringBuilder();
        if (box.clipsX() || box.clipsY()) {
            CTMLLayout.Port port = box.port();
            note.append(" scroll=").append(port.scrollWidth()).append("x").append(port.scrollHeight());
            if (port.gutterRight() > 0 || port.gutterBottom() > 0) {
                note.append(" gutter=").append(port.gutterRight()).append(",").append(port.gutterBottom());
            }
        }
        if (box.style().get(ElementStyle.POSITION) == ElementStyle.Position.STICKY) note.append(" sticky");
        if (box.isFixed()) note.append(" fixed");

        System.out.printf("%s<%s> %d,%d %dx%d base=%d%s%n", indent, box.element().getTag(),
                box.x(), box.y(), box.width(), box.height(), box.baseline(), note);
        for (CTMLLayout.TextLine line : box.lines()) {
            System.out.printf("%s  \"%s\" at %d,%d w%d x%.2f%n", indent, line.content(), line.x(), line.y(), line.width(), line.scale());
        }
        for (CTMLLayout.Box child : box.children()) dump(child, indent + "  ");
    }
}
