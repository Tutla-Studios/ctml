import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.CTMLLayout;
import net.tutla.craftytml.client.tusan.register.PropertyRegistry;

/** Grid: templates, fr shares, auto tracks, spans, explicit placement, the flow and the alignments. */
public class GridTest extends Harness {
    public static void main(String[] args){
        PropertyRegistry.init();
        templates();
        autoTracks();
        spans();
        placement();
        flow();
        alignment();
        gaps();
        text();
        report();
    }

    static void templates(){
        section("templates and fr shares");
        DOMTreeElement box = div("w-[90px] h-[20px] grid grid-cols-3");
        for (int i = 0; i < 3; i++) box.append(div("h-[10px]"));
        CTMLLayout.Box laid = only(box);
        check("three equal shares", rect(laid.children().get(0)), "0,0 30x10");
        check("the second one along", laid.children().get(1).x(), 30);
        check("and the third", laid.children().get(2).x(), 60);

        DOMTreeElement mixed = div("w-[100px] h-[20px] grid grid-cols-[20px_1fr_2fr]");
        for (int i = 0; i < 3; i++) mixed.append(div("h-[10px]"));
        laid = only(mixed);
        check("a fixed track keeps its size", laid.children().get(0).width(), 20);
        check("and the shares split what is left", laid.children().get(1).width() + "+" + laid.children().get(2).width(), "27+53");

        DOMTreeElement rows = div("w-[60px] h-[90px] grid grid-rows-3 grid-cols-1");
        for (int i = 0; i < 3; i++) rows.append(div("w-full"));
        laid = only(rows);
        check("rows share the height the same way", rect(laid.children().get(1)), "0,30 60x30");

        DOMTreeElement repeated = div("w-[80px] h-[20px] grid grid-cols-[repeat(4,1fr)]");
        for (int i = 0; i < 4; i++) repeated.append(div("h-[10px]"));
        check("repeat() says the same thing four times", only(repeated).children().get(3).x(), 60);
    }

    static void autoTracks(){
        section("auto tracks");
        // an auto column is as wide as the widest thing in it, a fr column takes the rest
        DOMTreeElement box = div("w-[100px] h-[20px] grid grid-cols-[auto_1fr]");
        box.append(div("w-[30px] h-[10px]")).append(div("h-[10px]"));
        CTMLLayout.Box laid = only(box);
        check("auto takes what its content needs", laid.children().get(0).width(), 30);
        check("and the share takes the rest", laid.children().get(1).width(), 70);

        DOMTreeElement texted = div("w-[100px] h-[20px] grid grid-cols-[auto_1fr]");
        texted.append(text(div(null), "abcd")).append(div("h-[10px]"));
        laid = only(texted);
        check("text sizes an auto column too", laid.children().get(0).width(), 24);

        // with no template at all the items each get a column of their own
        DOMTreeElement implicit = div("w-[90px] h-[20px] grid");
        for (int i = 0; i < 2; i++) implicit.append(div("h-[10px]"));
        laid = only(implicit);
        check("no template means one column", laid.children().get(1).y(), 10);

        DOMTreeElement autoRows = div("w-[60px] grid grid-cols-1 auto-rows-[12px]");
        for (int i = 0; i < 2; i++) autoRows.append(div("w-full"));
        laid = only(autoRows);
        check("auto-rows sizes the rows it makes", laid.children().get(1).y(), 12);
        check("and the grid is as tall as they are", laid.height(), 24);
    }

    static void spans(){
        section("spans");
        DOMTreeElement box = div("w-[90px] grid grid-cols-3 auto-rows-[10px]");
        box.append(div("col-span-2")).append(div(null)).append(div("col-span-3"));
        CTMLLayout.Box laid = only(box);
        check("a span covers two tracks", laid.children().get(0).width(), 60);
        check("the next item takes the last one", laid.children().get(1).x(), 60);
        check("and a full width span starts a new row", rect(laid.children().get(2)), "0,10 90x10");

        DOMTreeElement rowSpan = div("w-[60px] grid grid-cols-2 auto-rows-[10px]");
        rowSpan.append(div("row-span-2")).append(div(null)).append(div(null));
        laid = only(rowSpan);
        check("a row span is two rows tall", laid.children().get(0).height(), 20);
        check("so the next items go beside it", laid.children().get(1).x() + "," + laid.children().get(1).y(), "30,0");
        check("one under the other", laid.children().get(2).x() + "," + laid.children().get(2).y(), "30,10");
        check("and the grid is two rows tall", laid.height(), 20);
    }

    static void placement(){
        section("placing by hand");
        DOMTreeElement box = div("w-[90px] grid grid-cols-3 auto-rows-[10px]");
        box.append(div("col-start-3")).append(div("col-start-1 row-start-2")).append(div("col-start-2 col-end-4"));
        CTMLLayout.Box laid = only(box);
        check("col-start puts it where it says", laid.children().get(0).x(), 60);
        check("a row of its own too", rect(laid.children().get(1)), "0,10 30x10");
        check("col-end works out the span", laid.children().get(2).width(), 60);
        check("in the first row with room for it", laid.children().get(2).y(), 10);
    }

    static void flow(){
        section("the auto flow");
        DOMTreeElement rows = div("w-[60px] grid grid-cols-2 auto-rows-[10px]");
        for (int i = 0; i < 4; i++) rows.append(div(null));
        CTMLLayout.Box laid = only(rows);
        check("filling along the row", laid.children().get(1).x() + "," + laid.children().get(1).y(), "30,0");
        check("then on to the next", laid.children().get(2).x() + "," + laid.children().get(2).y(), "0,10");

        DOMTreeElement columns = div("w-[60px] h-[40px] grid grid-rows-2 grid-cols-2 grid-flow-col");
        for (int i = 0; i < 4; i++) columns.append(div(null));
        laid = only(columns);
        check("grid-flow-col fills downwards first", laid.children().get(1).x() + "," + laid.children().get(1).y(), "0,20");
        check("then along", laid.children().get(2).x() + "," + laid.children().get(2).y(), "30,0");
    }

    static void alignment(){
        section("alignment in a cell");
        DOMTreeElement box = div("w-[80px] h-[40px] grid grid-cols-2 grid-rows-1");
        box.append(div("w-[10px] h-[10px] justify-self-center self-center"));
        box.append(div("w-[10px] h-[10px] justify-self-end self-end"));
        CTMLLayout.Box laid = only(box);
        check("centred in its cell", rect(laid.children().get(0)), "15,15 10x10");
        check("and against the far corner of its own", rect(laid.children().get(1)), "70,30 10x10");

        DOMTreeElement stretched = div("w-[80px] h-[40px] grid grid-cols-2 grid-rows-1");
        stretched.append(div(null));
        check("stretch is what a cell does by default", rect(only(stretched).children().get(0)), "0,0 40x40");

        DOMTreeElement started = div("w-[80px] h-[40px] grid grid-cols-2 grid-rows-1 items-start justify-items-start");
        started.append(text(div(null), "ab"));
        check("items-start leaves it its own size", rect(only(started).children().get(0)), "0,0 12x9");
    }

    static void gaps(){
        section("grid gaps");
        DOMTreeElement box = div("w-[100px] grid grid-cols-3 auto-rows-[10px] gap-x-[5px] gap-y-[8px]");
        for (int i = 0; i < 6; i++) box.append(div(null));
        CTMLLayout.Box laid = only(box);
        check("the gaps come off the tracks", laid.children().get(0).width(), 30);
        check("the second column clears one", laid.children().get(1).x(), 35);
        check("the second row clears the other", laid.children().get(3).y(), 18);
        check("and the grid is both rows plus the gap", laid.height(), 28);
    }

    static void text(){
        section("text in a grid");
        DOMTreeElement box = text(div("w-[60px] grid grid-cols-2 auto-rows-[10px]"), "ab");
        box.append(div(null));
        CTMLLayout.Box laid = only(box);
        check("the text is an item of its own", laid.lines().get(0).x(), 0);
        check("so the box takes the next cell", laid.children().get(0).x(), 30);
    }
}
