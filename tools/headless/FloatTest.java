import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.CTMLLayout;
import net.tutla.craftytml.client.tusan.register.PropertyRegistry;

/** Floats: text running past them, boxes stacking beside each other, and clear getting past the lot. */
public class FloatTest extends Harness {
    public static void main(String[] args){
        PropertyRegistry.init();
        beside();
        onTheRight();
        stacking();
        clearing();
        contained();
        nested();
        report();
    }

    static void beside(){
        section("text beside a float");
        DOMTreeElement box = div("w-[100px]");
        box.append(div("float-left w-[20px] h-[20px]"));
        box.append(raw("aa bb cc dd ee ff gg hh ii jj kk ll mm nn"));

        CTMLLayout.Box laid = only(box);
        check("the float sits in the corner", rect(laid.children().get(0)), "0,0 20x20");
        check("the first line starts past it", laid.lines().get(0).x(), 20);
        check("with the room that is left", laid.lines().get(0).content(), "aa bb cc dd");
        check("the second line too", laid.lines().get(1).x(), 20);
        check("and the third, which still reaches into it", laid.lines().get(2).x(), 20);
        check("the one clear of it goes back to the edge", laid.lines().get(3).x(), 0);
        check("which is below the float", laid.lines().get(3).y(), 27);
    }

    static void onTheRight(){
        section("float-right");
        DOMTreeElement box = div("w-[100px]");
        box.append(div("float-right w-[20px] h-[20px]"));
        box.append(raw("aa bb cc dd ee"));

        CTMLLayout.Box laid = only(box);
        check("the float goes to the far side", rect(laid.children().get(0)), "80,0 20x20");
        check("the text starts at the edge", laid.lines().get(0).x(), 0);
        check("and stops short of the float", laid.lines().get(0).content(), "aa bb cc dd");
    }

    static void stacking(){
        section("floats beside each other");
        DOMTreeElement box = div("w-[100px]");
        box.append(div("float-left w-[40px] h-[10px]"));
        box.append(div("float-left w-[40px] h-[10px]"));
        box.append(div("float-left w-[40px] h-[10px]"));

        CTMLLayout.Box laid = only(box);
        check("two fit across", laid.children().get(1).x(), 40);
        check("the third drops below them", rect(laid.children().get(2)), "0,10 40x10");

        DOMTreeElement margins = div("w-[100px]");
        margins.append(div("float-left w-[20px] h-[10px] mr-1"));
        margins.append(raw("aa"));
        laid = only(margins);
        check("a float's margin keeps the text off it", laid.lines().get(0).x(), 24);
    }

    static void clearing(){
        section("clear");
        DOMTreeElement box = div("w-[100px]");
        box.append(div("float-left w-[20px] h-[20px]"));
        box.append(div("w-[10px] h-[4px]"));
        box.append(div("w-[10px] h-[4px] clear-left"));

        CTMLLayout.Box laid = only(box);
        check("a block alongside is not pushed down", laid.children().get(1).y(), 0);
        check("but one that clears starts below the float", laid.children().get(2).y(), 20);

        DOMTreeElement right = div("w-[100px]");
        right.append(div("float-right w-[20px] h-[20px]"));
        right.append(div("w-[10px] h-[4px] clear-left"));
        check("clear-left pays no attention to a float on the right", only(right).children().get(1).y(), 0);
    }

    static void contained(){
        section("what a float does to its box's height");
        DOMTreeElement loose = div("w-[100px]");
        loose.append(div("float-left w-[20px] h-[20px]"));
        check("a plain block lets its float hang out", only(loose).height(), 0);

        DOMTreeElement held = div("w-[100px] overflow-hidden");
        held.append(div("float-left w-[20px] h-[20px]"));
        check("a box that keeps to itself grows to hold it", only(held).height(), 20);

        DOMTreeElement flexed = div("w-[100px] flex flex-row");
        flexed.append(div("float-left w-[20px] h-[20px]"));
        check("and a float in a flex container is just an item", only(flexed).children().get(0).x(), 0);
    }

    static void nested(){
        section("a float and the blocks under it");
        // the text in the paragraph flows past the float, even though the float is its uncle
        DOMTreeElement box = div("w-[100px]");
        box.append(div("float-left w-[20px] h-[20px]"));
        DOMTreeElement paragraph = text(div("w-full"), "aa bb cc dd ee ff gg hh ii jj kk ll mm nn");
        box.append(paragraph);

        CTMLLayout.Box laid = only(box);
        CTMLLayout.Box inner = laid.children().get(1);
        check("the block itself still spans the box", inner.width(), 100);
        check("but its text starts past the float", inner.lines().get(0).x(), 20);
        check("and comes back once it is past", inner.lines().get(3).x(), 0);
    }
}
