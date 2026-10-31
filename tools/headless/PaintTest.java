import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.CTMLLayout;
import net.tutla.craftytml.client.render.CTMLRenderer;
import net.tutla.craftytml.client.tusan.register.PropertyRegistry;

import java.util.List;

/**
 * The parts of painting that can be checked without a window: what order the boxes come out in once
 * z-index has had its say, the room letter-spacing takes, where the text sits in a taller box, and
 * what each side of a border carries.
 */
public class PaintTest extends Harness {
    public static void main(String[] args){
        PropertyRegistry.init();
        stacking();
        contexts();
        spacing();
        centring();
        borders();
        report();
    }

    /** A box's children, named by the class they were given, in the order they are painted. */
    static String painted(CTMLLayout.Box box){
        StringBuilder out = new StringBuilder();
        for (CTMLLayout.Box layer : CTMLRenderer.order(box)) {
            if (layer == box) continue;
            if (!out.isEmpty()) out.append(" ");
            out.append(name(layer));
        }
        return out.toString();
    }

    /** Whatever text is in a box, or its tag, so a check reads as the markup does. */
    static String name(CTMLLayout.Box box){
        return box.lines().isEmpty() ? box.element().getTag() : box.lines().getFirst().content();
    }

    static DOMTreeElement labelled(String text, String classes){
        return text(div(classes), text);
    }

    static void stacking(){
        section("what covers what");
        DOMTreeElement box = div("w-[100px] h-[100px] relative");
        box.append(labelled("a", "w-[20px] h-[20px]"));
        box.append(labelled("b", "w-[20px] h-[20px]"));
        check("with nothing said they go in the order they are written", painted(only(box)), "a b");

        DOMTreeElement lifted = div("w-[100px] h-[100px] relative");
        lifted.append(labelled("a", "w-[20px] h-[20px] z-10"));
        lifted.append(labelled("b", "w-[20px] h-[20px]"));
        check("a z-index lifts one over the one after it", painted(only(lifted)), "b a");

        DOMTreeElement pushed = div("w-[100px] h-[100px] relative");
        pushed.append(labelled("a", "w-[20px] h-[20px]"));
        pushed.append(labelled("b", "w-[20px] h-[20px] z-[-1]"));
        check("and a negative one drops it under", painted(only(pushed)), "b a");

        DOMTreeElement tied = div("w-[100px] h-[100px] relative");
        tied.append(labelled("a", "w-[20px] h-[20px] z-5"));
        tied.append(labelled("b", "w-[20px] h-[20px] z-5"));
        check("the same z-index keeps the written order", painted(only(tied)), "a b");
    }

    static void contexts(){
        section("over an uncle box, which is what a stacking context is for");
        // a is inside the first child, c is the second: without contexts a could never be lifted over it,
        // since its parent paints and finishes before c is reached
        DOMTreeElement page = div("w-[100px] h-[100px] relative");
        DOMTreeElement first = div("w-[50px] h-[50px]");
        first.append(labelled("a", "w-[10px] h-[10px] z-10"));
        page.append(first);
        page.append(labelled("c", "w-[50px] h-[50px]"));

        CTMLLayout.Box laid = only(page);
        check("a child's z-index is sorted against the whole context", painted(laid), "div c a");

        // the same tree with the parent made into a context of its own, which shuts a in
        DOMTreeElement held = div("w-[100px] h-[100px] relative");
        DOMTreeElement opaque = div("w-[50px] h-[50px] opacity-50");
        opaque.append(labelled("a", "w-[10px] h-[10px] z-10"));
        held.append(opaque);
        held.append(labelled("c", "w-[50px] h-[50px]"));
        check("one inside a faded box cannot climb out of it", painted(only(held)), "div c");
        check("because the faded box is a context of its own",
                CTMLRenderer.order(only(held).children().getFirst()).size(), 2);

        DOMTreeElement clipped = div("w-[100px] h-[100px] relative");
        DOMTreeElement scroller = div("w-[50px] h-[50px] overflow-hidden");
        scroller.append(labelled("a", "w-[10px] h-[10px] z-10"));
        clipped.append(scroller);
        clipped.append(labelled("c", "w-[50px] h-[50px]"));
        check("nor out of one that clips", painted(only(clipped)), "div c");
    }

    static void spacing(){
        section("letter-spacing");
        // the stub font is six pixels a character, so five of them are thirty with nothing between.
        // the spacing is counted after every glyph, the last one included, which is what css does
        CTMLLayout.Box plain = only(labelled("abcde", "w-[200px]"));
        check("no tracking, no room between", plain.lines().getFirst().spacing(), 0);
        check("so the line is the glyphs alone", plain.lines().getFirst().width(), 30);

        CTMLLayout.Box wide = only(labelled("abcde", "w-[200px] tracking-wide"));
        check("tracking-wide is a pixel each", wide.lines().getFirst().spacing(), 1);
        check("which the line is measured with", wide.lines().getFirst().width(), 35);

        CTMLLayout.Box widest = only(labelled("abcde", "w-[200px] tracking-widest"));
        check("tracking-widest is three", widest.lines().getFirst().spacing(), 3);
        check("and the line grows by them", widest.lines().getFirst().width(), 45);

        CTMLLayout.Box tighter = only(labelled("abcde", "w-[200px] tracking-tighter"));
        check("tracking-tighter takes one back", tighter.lines().getFirst().spacing(), -1);
        check("and the line shrinks", tighter.lines().getFirst().width(), 25);

        // the extra room is part of the text, so it decides where the line breaks as well
        CTMLLayout.Box wrapped = only(labelled("aaaa bbbb", "w-[54px] tracking-widest"));
        check("the room counts towards the wrap", wrapped.lines().size(), 2);
        check("where without it the line would have fitted", only(labelled("aaaa bbbb", "w-[54px]")).lines().size(), 1);
    }

    static void centring(){
        section("text down the middle of a taller box");
        // three nine pixel lines in a sixty pixel box leave thirty three over
        String text = "aaa bbb ccc";
        CTMLLayout.Box top = only(labelled(text, "w-[20px] h-[60px]"));
        check("it starts at the top by itself", top.lines().getFirst().y(), 0);
        check("with three lines of it", top.lines().size(), 3);

        CTMLLayout.Box middle = only(labelled(text, "w-[20px] h-[60px] content-center"));
        check("content-center puts half the room above it", middle.lines().getFirst().y(), 16);
        check("and the lines stay their own distance apart", middle.lines().get(1).y(), 25);

        CTMLLayout.Box bottom = only(labelled(text, "w-[20px] h-[60px] content-end"));
        check("content-end drops it to the bottom", bottom.lines().getFirst().y(), 33);
        check("where the last line ends on the floor", bottom.lines().getLast().y() + 9, 60);

        // and it moves whatever else is in the box with it, so a box of text and a box lines up
        DOMTreeElement mixed = div("w-[40px] h-[60px] content-center");
        mixed.append(div("w-[10px] h-[10px]"));
        check("children come down with the text", only(mixed).children().getFirst().y(), 25);
    }

    static void borders(){
        section("each side of the border on its own");
        ElementStyle style = net.tutla.craftytml.client.ctml.style.TailwindParser.parse(
                "border-2 border-t-4 border-t-red-500 border-l-sky-500 border-dashed");
        check("a width for every side", style.get(ElementStyle.BORDER_RIGHT_WIDTH), 2);
        check("and one side's own over it", style.get(ElementStyle.BORDER_TOP_WIDTH), 4);
        check("a colour for the top", style.get(ElementStyle.BORDER_TOP_COLOR) != null, true);
        check("another for the left", style.get(ElementStyle.BORDER_LEFT_COLOR) != null, true);
        check("and they are not the same",
                style.get(ElementStyle.BORDER_TOP_COLOR).equals(style.get(ElementStyle.BORDER_LEFT_COLOR)), false);
        check("the sides with no colour of their own have none",
                style.get(ElementStyle.BORDER_BOTTOM_COLOR), null);
        check("the style reaches every side", style.get(ElementStyle.BORDER_BOTTOM_STYLE),
                ElementStyle.BorderStyle.DASHED);

        ElementStyle axes = net.tutla.craftytml.client.ctml.style.TailwindParser.parse("border-x-2 border-y-8");
        check("border-x is the two sides across", axes.get(ElementStyle.BORDER_LEFT_WIDTH) + ","
                + axes.get(ElementStyle.BORDER_RIGHT_WIDTH), "2,2");
        check("and border-y the two down", axes.get(ElementStyle.BORDER_TOP_WIDTH) + ","
                + axes.get(ElementStyle.BORDER_BOTTOM_WIDTH), "8,8");

        // and the room they take is each side's own, which is what layout has to agree with paint about
        CTMLLayout.Box box = only(labelled("a", "w-[60px] border-2 border-t-10 border-l-6"));
        check("the content starts past the left border", box.contentX() - box.x(), 6);
        check("and under the top one", box.contentY() - box.y(), 10);
        check("a border-none side takes no room at all",
                only(labelled("a", "w-[60px] border-4 border-none")).contentX(), 0);
    }
}
