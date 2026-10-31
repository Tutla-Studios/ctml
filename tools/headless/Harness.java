import net.minecraft.util.FormattedCharSequence;
import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.element.*;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.dom.DOMTreeText;
import net.tutla.craftytml.client.render.CTMLLayout;
import net.tutla.craftytml.client.tusan.CTMLTusan;
import net.tutla.craftytml.client.tusan.register.ElementRegistry;

/** Shared bits for the headless layout tests: fixed sized text, tiny tree builders, a check. */
public class Harness {
    public static int failures = 0;

    /** 6 pixels a character, 9 pixel lines, baseline 7 down, so every number below is worked out by hand. */
    public record Stub() implements CTMLLayout.TextMetrics {
        public int width(String text){ return text.length() * 6; }
        public int width(FormattedCharSequence text){
            int[] count = { 0 };
            text.accept((i, s, c) -> { count[0]++; return true; });
            return count[0] * 6;
        }
        public int lineHeight(){ return 9; }
        public int ascent(){ return 7; }
    }

    public static DOMTreeElement div(String classes){
        DOMTreeElement element = new DOMTreeElement(new Division());
        if (classes != null) element.getElement().applyClassStyles(classes);
        return element;
    }

    public static DOMTreeElement span(String classes){
        DOMTreeElement element = new DOMTreeElement(new Span());
        if (classes != null) element.getElement().applyClassStyles(classes);
        return element;
    }

    /** Any registered element, by the tag it is written as: the same ones the parser builds. */
    public static DOMTreeElement tag(String name, String classes){
        CTMLTusan.init(); // fills the element and property registries, once however often it is called

        CTMLElement found = null;
        for (CTMLElement element : ElementRegistry.getRegister()) {
            if (element.name.equals(name)) found = element;
        }
        if (found == null) throw new IllegalArgumentException("no element is registered as <" + name + ">");

        CTMLElement made;
        try {
            made = found.getClass().getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }

        DOMTreeElement node = new DOMTreeElement(made);
        if (classes != null) node.getElement().applyClassStyles(classes);
        return node;
    }

    /** A cell with text in it, and however many rows and columns of the table it takes up. */
    public static DOMTreeElement cell(String name, String text, int colspan, int rowspan){
        DOMTreeElement node = tag(name, null);
        if (text != null) node.property("text", text, null);
        if (colspan > 1) node.property("colspan", colspan, null);
        if (rowspan > 1) node.property("rowspan", rowspan, null);
        return node;
    }

    public static DOMTreeElement root(String classes){
        DOMTreeElement element = new DOMTreeElement(new CTML());
        if (classes != null) element.getElement().applyClassStyles(classes);
        return element;
    }

    public static DOMTreeElement text(DOMTreeElement element, String text){
        element.property("text", text, null);
        return element;
    }

    public static DOMTreeText raw(String text){
        return new DOMTreeText(text);
    }

    public static CTMLLayout.Box lay(DOMTreeElement root, int width, int height){
        return new CTMLLayout(new Stub(), width, height).layout(root, 0, 0, width, height);
    }

    /** Lays a tree out as the only thing on a 854x480 screen and hands back the box for it. */
    public static CTMLLayout.Box only(DOMTreeElement element){
        DOMTreeElement page = root("w-full h-full");
        page.append(element);
        return lay(page, 854, 480).children().get(0);
    }

    public static void check(String what, Object got, Object want){
        boolean ok = String.valueOf(got).equals(String.valueOf(want));
        if (!ok) failures++;
        System.out.printf("%s %-48s got %s want %s%n", ok ? "ok  " : "FAIL", what, got, want);
    }

    public static void section(String name){
        System.out.println("-- " + name + " --");
    }

    public static void report(){
        System.out.println(failures == 0 ? "\nall ok" : "\n" + failures + " FAILED");
        if (failures > 0) System.exit(1);
    }

    /** The x,y,w,h of a box as one string, for a check that reads like the layout it describes. */
    public static String rect(CTMLLayout.Box box){
        return box.x() + "," + box.y() + " " + box.width() + "x" + box.height();
    }
}
