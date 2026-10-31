import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.CTMLLayout;
import net.tutla.craftytml.client.tusan.register.PropertyRegistry;


/** Everything flexbox: basis, grow, shrink, order, wrapping, align-self, align-content, baselines. */
public class FlexTest extends Harness {
    public static void main(String[] args){
        PropertyRegistry.init();
        grow();
        shrink();
        basis();
        order();
        wrap();
        reversed();
        alignSelf();
        alignContent();
        baseline();
        intrinsic();
        gaps();
        textItem();
        column();
        rounding();
        report();
    }

    static void grow(){
        section("flex-grow");
        DOMTreeElement box = div("w-[100px] h-[20px] flex flex-row");
        box.append(div("w-[20px] h-[10px] flex-1")).append(div("w-[20px] h-[10px] flex-1"));
        CTMLLayout.Box laid = only(box);
        check("free space shared evenly", rect(laid.children().get(0)), "0,0 50x10");
        check("second item follows", rect(laid.children().get(1)), "50,0 50x10");

        DOMTreeElement weighted = div("w-[100px] h-[20px] flex flex-row");
        weighted.append(div("w-0 h-[10px] grow")).append(div("w-0 h-[10px] grow-3"));
        laid = only(weighted);
        check("grow-3 takes three times as much", laid.children().get(0).width() + "+" + laid.children().get(1).width(), "25+75");

        DOMTreeElement withGap = div("w-[100px] h-[20px] flex flex-row gap-[10px]");
        withGap.append(div("w-0 h-[10px] flex-1")).append(div("w-0 h-[10px] flex-1"));
        laid = only(withGap);
        check("the gap comes off first", laid.children().get(0).width(), 45);
        check("and the second item clears it", laid.children().get(1).x(), 55);

        DOMTreeElement none = div("w-[100px] h-[20px] flex flex-row");
        none.append(div("w-[20px] h-[10px]")).append(div("w-[20px] h-[10px]"));
        laid = only(none);
        check("nothing grows without being asked", laid.children().get(1).right(), 40);
    }

    static void shrink(){
        section("flex-shrink");
        DOMTreeElement box = div("w-[100px] h-[20px] flex flex-row");
        box.append(div("w-[60px] h-[10px] min-w-0")).append(div("w-[60px] h-[10px] min-w-0"));
        CTMLLayout.Box laid = only(box);
        check("the overflow is taken back evenly", laid.children().get(0).width() + "+" + laid.children().get(1).width(), "50+50");

        DOMTreeElement held = div("w-[100px] h-[20px] flex flex-row");
        held.append(div("w-[60px] h-[10px] shrink-0")).append(div("w-[60px] h-[10px] min-w-0"));
        laid = only(held);
        check("shrink-0 keeps its size", laid.children().get(0).width(), 60);
        check("so the other one gives it all up", laid.children().get(1).width(), 40);

        DOMTreeElement floored = div("w-[100px] h-[20px] flex flex-row");
        floored.append(div("w-[60px] h-[10px] min-w-[50px]")).append(div("w-[60px] h-[10px] min-w-0"));
        laid = only(floored);
        check("an item stops at its own minimum", laid.children().get(0).width(), 50);
        check("and the rest goes to the other", laid.children().get(1).width(), 50);
    }

    static void basis(){
        section("flex-basis");
        DOMTreeElement box = div("w-[100px] h-[20px] flex flex-row");
        box.append(div("w-[80px] h-[10px] basis-[30px] shrink-0")).append(div("w-[10px] h-[10px] shrink-0"));
        CTMLLayout.Box laid = only(box);
        check("basis beats width", laid.children().get(0).width(), 30);

        DOMTreeElement fraction = div("w-[100px] h-[20px] flex flex-row");
        fraction.append(div("h-[10px] basis-1/4 shrink-0")).append(div("h-[10px] basis-1/2 shrink-0"));
        laid = only(fraction);
        check("a fraction of the container", laid.children().get(0).width() + "+" + laid.children().get(1).width(), "25+50");
    }

    static void order(){
        section("order");
        DOMTreeElement box = div("w-[90px] h-[20px] flex flex-row");
        box.append(text(div("w-[30px] h-[10px] order-last"), "a"))
           .append(text(div("w-[30px] h-[10px]"), "b"))
           .append(text(div("w-[30px] h-[10px] order-first"), "c"));
        CTMLLayout.Box laid = only(box);
        // the boxes come back in the order flex placed them, which is the order css paints them in
        check("order-first goes to the front", laid.children().get(0).lines().get(0).content(), "c");
        check("the unordered one stays in the middle", laid.children().get(1).lines().get(0).content(), "b");
        check("order-last goes to the back", laid.children().get(2).lines().get(0).content(), "a");
        check("and they are placed in that order", laid.children().get(0).x() + "," + laid.children().get(2).x(), "0,60");
    }

    static void wrap(){
        section("flex-wrap");
        DOMTreeElement box = div("w-[100px] flex flex-row flex-wrap");
        for (int i = 0; i < 3; i++) box.append(div("w-[40px] h-[10px] shrink-0"));
        CTMLLayout.Box laid = only(box);
        check("two fit on the first line", laid.children().get(1).x() + "," + laid.children().get(1).y(), "40,0");
        check("the third drops onto the next", laid.children().get(2).x() + "," + laid.children().get(2).y(), "0,10");
        check("the container is two lines tall", laid.height(), 20);

        DOMTreeElement gapped = div("w-[100px] flex flex-row flex-wrap gap-[10px]");
        for (int i = 0; i < 3; i++) gapped.append(div("w-[40px] h-[10px] shrink-0"));
        laid = only(gapped);
        check("the gap counts when filling a line", laid.children().get(2).y(), 20);
        check("and between the lines", laid.height(), 30);

        DOMTreeElement reversed = div("w-[100px] flex flex-row flex-wrap-reverse");
        for (int i = 0; i < 3; i++) reversed.append(div("w-[40px] h-[10px] shrink-0"));
        laid = only(reversed);
        check("wrap-reverse puts the last line first", laid.children().get(0).y() + "," + laid.children().get(0).x(), "0,0");

        DOMTreeElement nowrap = div("w-[100px] flex flex-row flex-nowrap");
        for (int i = 0; i < 3; i++) nowrap.append(div("w-[40px] h-[10px]"));
        laid = only(nowrap);
        check("nowrap keeps one line", laid.children().get(2).y(), 0);
    }

    /** A reversed direction turns the axis round without moving the items out of their order. */
    static void reversed(){
        section("reverse directions");
        DOMTreeElement box = div("w-[100px] h-[20px] flex flex-row-reverse");
        box.append(text(div("w-[20px] h-[10px]"), "a")).append(text(div("w-[20px] h-[10px]"), "b"));
        CTMLLayout.Box laid = only(box);
        check("the first item starts at the far end", laid.children().get(0).x(), 80);
        check("and the next one comes before it", laid.children().get(1).x(), 60);
        check("they stay in the order they were written", laid.children().get(0).lines().get(0).content(), "a");

        DOMTreeElement ended = div("w-[100px] h-[20px] flex flex-row-reverse justify-end");
        ended.append(div("w-[20px] h-[10px]")).append(div("w-[20px] h-[10px]"));
        laid = only(ended);
        check("justify-end goes to the other end with it", laid.children().get(1).x(), 0);

        DOMTreeElement column = div("w-[20px] h-[100px] flex flex-col-reverse");
        column.append(div("w-[10px] h-[20px]")).append(div("w-[10px] h-[20px]"));
        laid = only(column);
        check("a reversed column starts at the bottom", laid.children().get(0).y(), 80);
        check("with the next one above it", laid.children().get(1).y(), 60);
    }

    static void alignSelf(){
        section("align-self");
        DOMTreeElement box = div("w-[100px] h-[40px] flex flex-row items-start");
        box.append(div("w-[10px] h-[10px]"))
           .append(div("w-[10px] h-[10px] self-center"))
           .append(div("w-[10px] h-[10px] self-end"))
           .append(div("w-[10px] self-stretch"));
        CTMLLayout.Box laid = only(box);
        check("the container's own alignment", laid.children().get(0).y(), 0);
        check("self-center overrides it", laid.children().get(1).y(), 15);
        check("self-end too", laid.children().get(2).y(), 30);
        check("self-stretch fills the line", laid.children().get(3).height(), 40);
    }

    static void alignContent(){
        section("align-content");
        DOMTreeElement box = div("w-[100px] h-[60px] flex flex-row flex-wrap content-center");
        for (int i = 0; i < 3; i++) box.append(div("w-[60px] h-[10px] shrink-0"));
        CTMLLayout.Box laid = only(box);
        // three 60 wide items make three lines of 10, so 30 of the 60 is left over, half of it on top
        check("the lines are centred as a block", laid.children().get(0).y(), 15);
        check("and keep their order", laid.children().get(2).y(), 35);

        DOMTreeElement between = div("w-[100px] h-[60px] flex flex-row flex-wrap content-between");
        for (int i = 0; i < 2; i++) between.append(div("w-[60px] h-[10px] shrink-0"));
        laid = only(between);
        check("content-between pushes them apart", laid.children().get(0).y() + "," + laid.children().get(1).y(), "0,50");

        DOMTreeElement stretch = div("w-[100px] h-[60px] flex flex-row flex-wrap content-stretch items-stretch");
        for (int i = 0; i < 2; i++) stretch.append(div("w-[60px] shrink-0"));
        laid = only(stretch);
        check("content-stretch thickens the lines", laid.children().get(1).y(), 30);
    }

    static void baseline(){
        section("items-baseline");
        DOMTreeElement box = div("w-[100px] h-[40px] flex flex-row items-baseline");
        box.append(text(div("w-[30px]"), "a"));
        box.append(text(div("w-[30px] pt-[5px]"), "b"));
        CTMLLayout.Box laid = only(box);
        // each line sits 7 under its own box's top, so the padded one keeps the deeper baseline and the
        // plain one drops to meet it
        check("the padded item keeps the deeper baseline", laid.children().get(1).baseline(), 12);
        check("so the plain one drops to meet it", laid.children().get(0).y(), 5);
        check("the padded one stays put", laid.children().get(1).y(), 0);
        CTMLLayout.Box plain = laid.children().get(0);
        CTMLLayout.Box padded = laid.children().get(1);
        check("their text ends up on one line", (plain.y() + plain.baseline()) + "," + (padded.y() + padded.baseline()), "12,12");
    }

    /** The two gaps run their own way: one along a row, the other between the lines. */
    /** The free space has to be used up to the pixel, whatever it divides into. */
    static void rounding(){
        section("spacing that does not divide evenly");
        // 100 wide with three 11 wide items leaves 67 to share out, which divides into nothing neatly
        DOMTreeElement between = div("w-[100px] h-[20px] flex flex-row justify-between");
        for (int i = 0; i < 3; i++) between.append(div("w-[11px] h-[10px] shrink-0"));
        CTMLLayout.Box laid = only(between);
        check("the last item still ends on the far edge", laid.children().get(2).right(), 100);
        check("and the middle one lands halfway", laid.children().get(1).x(), 45);

        DOMTreeElement evenly = div("w-[100px] h-[20px] flex flex-row justify-evenly");
        for (int i = 0; i < 3; i++) evenly.append(div("w-[11px] h-[10px] shrink-0"));
        laid = only(evenly);
        check("evenly leaves no pixels over", 100 - laid.children().get(2).right(), 17);
        check("with the gaps as even as pixels allow", laid.children().get(1).x() - laid.children().get(0).right(), 17);

        DOMTreeElement lines = div("w-[100px] h-[61px] flex flex-row flex-wrap content-between");
        for (int i = 0; i < 3; i++) lines.append(div("w-[60px] h-[10px] shrink-0"));
        laid = only(lines);
        check("the last line sits on the bottom edge", laid.children().get(2).bottom(), 61);
    }

    static void gaps(){
        section("row-gap and column-gap");
        DOMTreeElement box = div("w-[100px] flex flex-row flex-wrap gap-x-[10px] gap-y-[4px]");
        for (int i = 0; i < 3; i++) box.append(div("w-[40px] h-[10px] shrink-0"));
        CTMLLayout.Box laid = only(box);
        check("the column gap spaces a row", laid.children().get(1).x(), 50);
        check("the row gap spaces the lines", laid.children().get(2).y(), 14);
        check("so the box is both lines plus the one gap", laid.height(), 24);

        DOMTreeElement column = div("h-[100px] w-[20px] flex flex-col gap-y-[6px]");
        column.append(div("w-[10px] h-[10px] shrink-0")).append(div("w-[10px] h-[10px] shrink-0"));
        laid = only(column);
        check("down a column the row gap is the one that counts", laid.children().get(1).y(), 16);

        DOMTreeElement both = div("w-[100px] h-[20px] flex flex-row gap-[8px]");
        both.append(div("w-[10px] h-[10px] shrink-0")).append(div("w-[10px] h-[10px] shrink-0"));
        check("gap on its own still sets both", only(both).children().get(1).x(), 18);
    }

    /** A flex container's own text is an item like any other, so it takes part in the main axis. */
    static void textItem(){
        section("the anonymous text item");
        DOMTreeElement box = text(div("w-[100px] h-[20px] flex flex-row"), "ab");
        box.append(div("w-[20px] h-[10px] shrink-0"));
        CTMLLayout.Box laid = only(box);
        check("the text comes first", laid.lines().get(0).content() + "@" + laid.lines().get(0).x(), "ab@0");
        check("and the box after it", laid.children().get(0).x(), 12);

        DOMTreeElement ended = text(div("w-[100px] h-[20px] flex flex-row justify-end"), "ab");
        ended.append(div("w-[20px] h-[10px] shrink-0"));
        laid = only(ended);
        check("justify-end moves the text with everything else", laid.lines().get(0).x(), 68);
        check("with the box still after it", laid.children().get(0).x(), 80);

        DOMTreeElement centred = text(div("w-[100px] h-[20px] flex flex-row items-center"), "ab");
        centred.append(div("w-[20px] h-[20px] shrink-0"));
        laid = only(centred);
        check("and align-items lines it up on the cross axis", laid.lines().get(0).y(), 5);

        DOMTreeElement column = text(div("w-[100px] h-[40px] flex flex-col"), "ab");
        column.append(div("w-[20px] h-[10px] shrink-0"));
        laid = only(column);
        check("down a column it stacks", laid.children().get(0).y(), 9);
    }

    /** A column's cross axis is its width, and an item is only stretched when it is told to be. */
    static void column(){
        section("columns");
        DOMTreeElement stretched = div("w-[100px] h-[40px] flex flex-col");
        stretched.append(text(div(null), "ab"));
        check("stretch is the default, so it fills the width", only(stretched).children().get(0).width(), 100);

        DOMTreeElement started = div("w-[100px] h-[40px] flex flex-col items-start");
        started.append(text(div(null), "ab"));
        check("items-start shrinks it to its content", only(started).children().get(0).width(), 12);

        DOMTreeElement ended = div("w-[100px] h-[40px] flex flex-col items-end");
        ended.append(text(div(null), "ab"));
        check("items-end puts that against the far side", only(ended).children().get(0).x(), 88);

        // two lines of text are 18 tall in a 10 tall column: the content is the floor, so they overflow
        DOMTreeElement squashed = div("w-[100px] h-[10px] flex flex-col");
        squashed.append(text(div(null), "ab")).append(text(div(null), "cd"));
        CTMLLayout.Box laid = only(squashed);
        check("an item is not squashed under its own content", laid.children().get(0).height(), 9);
        check("so the second one keeps its place", laid.children().get(1).y(), 9);
    }

    static void intrinsic(){
        section("items sized by their content");
        // the old bug: a flex item with no width of its own, wrapped around a box that does have one
        DOMTreeElement box = div("w-[100px] h-[20px] flex flex-row");
        DOMTreeElement wrapper = div(null);
        wrapper.append(div("w-8 h-[10px]"));
        box.append(wrapper).append(div("w-[10px] h-[10px] shrink-0"));
        CTMLLayout.Box laid = only(box);
        check("the wrapper is as wide as what is in it", laid.children().get(0).width(), 32);
        check("so nothing spills out of it", laid.children().get(0).children().get(0).right(), 32);

        DOMTreeElement texted = div("flex flex-row w-[200px] h-[20px]");
        texted.append(text(div(null), "six"));
        laid = only(texted);
        check("text sizes an item too", laid.children().get(0).width(), 18);

        DOMTreeElement padded = div("flex flex-row w-[200px] h-[20px]");
        padded.append(text(div("px-[4px]"), "six"));
        laid = only(padded);
        check("padding counts towards it", laid.children().get(0).width(), 26);
    }
}
