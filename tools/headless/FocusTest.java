import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.CTMLFocus;
import net.tutla.craftytml.client.render.CTMLLayout;
import net.tutla.craftytml.client.render.CTMLScroll;
import net.tutla.craftytml.client.tusan.register.PropertyRegistry;

/** What the keyboard can be put on, where tab goes next, and what a press focuses. */
public class FocusTest extends Harness {
    public static void main(String[] args){
        PropertyRegistry.init();
        focusable();
        tabOrder();
        pressing();
        keys();
        report();
    }

    static DOMTreeElement tabbable(String classes, int index){
        DOMTreeElement element = div(classes);
        element.property("tabindex", index, null);
        return element;
    }

    /** The tab order as the text of each box, which is what the markup reads as. */
    static String walk(CTMLLayout.Box root, int steps){
        StringBuilder out = new StringBuilder();
        DOMTreeElement at = null;
        for (int i = 0; i < steps; i++) {
            at = CTMLFocus.next(root, at, false);
            if (!out.isEmpty()) out.append(" ");
            out.append(at == null ? "none" : name(root, at));
        }
        return out.toString();
    }

    static String name(CTMLLayout.Box root, DOMTreeElement element){
        CTMLLayout.Box box = CTMLScroll.path(root, element).getLast();
        return box.lines().isEmpty() ? "?" : box.lines().getFirst().content();
    }

    static void focusable(){
        section("what can take the keyboard");
        DOMTreeElement page = div("w-[100px] h-[100px]");
        DOMTreeElement plain = div("w-full h-[10px]");
        DOMTreeElement scroller = div("w-full h-[10px] overflow-y-auto");
        scroller.append(div("w-full h-[50px]"));
        DOMTreeElement said = tabbable("w-full h-[10px]", 0);
        DOMTreeElement taken = tabbable("w-full h-[10px]", -1);
        DOMTreeElement clipped = div("w-full h-[10px] overflow-y-hidden");
        clipped.append(div("w-full h-[50px]"));
        page.append(plain).append(scroller).append(said).append(taken).append(clipped);

        CTMLLayout.Box laid = only(page);
        check("a plain box cannot", CTMLFocus.focusable(laid.children().get(0)), false);
        check("one that scrolls can, with nothing said", CTMLFocus.focusable(laid.children().get(1)), true);
        check("and tab stops on it", CTMLFocus.tabbable(laid.children().get(1)), true);
        check("tabindex 0 can", CTMLFocus.focusable(laid.children().get(2)), true);
        check("and tab stops on that", CTMLFocus.tabbable(laid.children().get(2)), true);
        check("tabindex below zero still can", CTMLFocus.focusable(laid.children().get(3)), true);
        check("but tab walks past it", CTMLFocus.tabbable(laid.children().get(3)), false);
        check("a box that only hides its overflow cannot", CTMLFocus.focusable(laid.children().get(4)), false);

        DOMTreeElement unseen = div("w-[100px] h-[100px] invisible");
        unseen.append(tabbable("w-full h-[10px]", 0));
        check("nor anything that is not drawn", CTMLFocus.order(only(unseen)).size(), 0);
    }

    static void tabOrder(){
        section("where tab goes");
        DOMTreeElement page = div("w-[100px] h-[100px]");
        page.append(text(tabbable("w-full h-[10px]", 0), "a"));
        page.append(text(tabbable("w-full h-[10px]", 0), "b"));
        page.append(text(div("w-full h-[10px]"), "plain"));
        page.append(text(tabbable("w-full h-[10px]", 0), "c"));

        CTMLLayout.Box laid = only(page);
        check("in the order they are written", walk(laid, 3), "a b c");
        check("and round again at the end", walk(laid, 4), "a b c a");

        DOMTreeElement last = laid.children().get(3).element();
        check("shift walks it back", name(laid, CTMLFocus.next(laid, last, true)), "b");
        check("and round the other way", name(laid, CTMLFocus.next(laid, laid.children().getFirst().element(), true)), "c");
        check("with nothing focused it starts at the first", name(laid, CTMLFocus.next(laid, null, false)), "a");
        check("or the last going backwards", name(laid, CTMLFocus.next(laid, null, true)), "c");

        // a number of its own comes before the ones that said nothing, in that number's order
        DOMTreeElement numbered = div("w-[100px] h-[100px]");
        numbered.append(text(tabbable("w-full h-[10px]", 0), "written-first"));
        numbered.append(text(tabbable("w-full h-[10px]", 2), "second"));
        numbered.append(text(tabbable("w-full h-[10px]", 1), "first"));
        numbered.append(text(tabbable("w-full h-[10px]", 0), "written-second"));
        check("numbers first, then the rest", walk(only(numbered), 4),
                "first second written-first written-second");

        DOMTreeElement none = div("w-[100px] h-[100px]");
        none.append(div("w-full h-[10px]"));
        check("nothing to focus, nowhere to go", CTMLFocus.next(only(none), null, false), null);

        // tab reaches into a scroll container and out again, since a box that scrolls is a stop too
        DOMTreeElement nested = div("w-[100px] h-[100px] overflow-y-auto");
        nested.append(text(tabbable("w-full h-[10px]", 0), "inside"));
        nested.append(div("w-full h-[200px]"));
        CTMLLayout.Box nestedBox = only(nested);
        check("the container first, then what is in it", CTMLFocus.order(nestedBox).size(), 2);
        check("the container is the outer one", CTMLFocus.order(nestedBox).getFirst() == nestedBox, true);
    }

    static void pressing(){
        section("what a press focuses");
        DOMTreeElement page = div("w-[100px] h-[100px]");
        DOMTreeElement outer = tabbable("w-full h-[40px]", 0);
        DOMTreeElement inner = text(div("w-full h-[20px]"), "text");
        outer.append(inner);
        page.append(outer);
        page.append(text(div("w-full h-[40px]"), "plain"));

        CTMLLayout.Box laid = only(page);
        check("a press on the text focuses the box holding it",
                CTMLFocus.at(laid, 10, 10) == outer, true);
        check("a press on nothing focusable finds nothing", CTMLFocus.at(laid, 10, 60), null);
        check("and a press right off the page finds nothing", CTMLFocus.at(laid, 500, 500), null);

        // the innermost one wins, so a focusable box inside a focusable box takes its own press
        DOMTreeElement both = div("w-[100px] h-[100px]");
        DOMTreeElement around = tabbable("w-full h-[40px]", 0);
        DOMTreeElement within = tabbable("w-full h-[20px]", 0);
        around.append(within);
        both.append(around);
        check("the innermost one takes it", CTMLFocus.at(only(both), 10, 10) == within, true);
        check("and the one around it below that", CTMLFocus.at(only(both), 10, 30) == around, true);
    }

    static void keys(){
        section("where the keys go");
        // the focus is on something that cannot scroll, so the keys fall out to the box around it
        DOMTreeElement scroller = div("w-[100px] h-[100px] overflow-y-auto");
        DOMTreeElement held = tabbable("w-full h-[10px]", 0);
        scroller.append(held).append(div("w-full h-[300px]"));
        DOMTreeElement page = root("w-full h-full");
        page.append(scroller);

        CTMLLayout.Box laid = lay(page, 854, 480);
        check("a key on a box that cannot scroll reaches the one that can",
                CTMLScroll.key(laid, held, CTMLScroll.Nudge.LINE_DOWN), true);
        check("and moves it", scroller.getScrollY(), CTMLScroll.WHEEL_STEP);

        scroller.setScrollOffset(0, 0);
        laid = lay(page, 854, 480);
        check("with nothing focused the outermost one takes it",
                CTMLScroll.key(laid, null, CTMLScroll.Nudge.PAGE_DOWN), true);
        check("by a page of it", scroller.getScrollY() > CTMLScroll.WHEEL_STEP, true);
    }
}
