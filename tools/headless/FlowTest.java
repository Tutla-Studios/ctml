import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.dom.DOMTreeNode;
import net.tutla.craftytml.client.render.CTMLDrag;
import net.tutla.craftytml.client.render.CTMLLayout;
import net.tutla.craftytml.client.render.CTMLScroll;
import net.tutla.craftytml.client.tusan.register.PropertyRegistry;

/** Block flow, line boxes, text nodes, vertical-align, collapsing margins, sticky, fixed and caching. */
public class FlowTest extends Harness {
    public static void main(String[] args){
        PropertyRegistry.init();
        interleaving();
        whitespace();
        fragmentation();
        wrapping();
        verticalAlign();
        breaking();
        rightToLeft();
        clamping();
        margins();
        intrinsicWidths();
        collapsingThroughEdges();
        sticky();
        fixed();
        caching();
        reuse();
        dragClamp();
        dragResize();
        stickyNesting();
        report();
    }

    /** Text nodes keep their place among the elements, which is what interleaving means. */
    static void interleaving(){
        section("text between elements");
        DOMTreeElement box = div("w-[200px]");
        box.append(raw("ab"));
        box.append(text(span(null), "XY"));
        box.append(raw("cd"));

        CTMLLayout.Box laid = only(box);
        check("two runs of text, one each side", laid.lines().size(), 2);
        check("the first comes before the span", laid.lines().get(0).content() + "@" + laid.lines().get(0).x(), "ab@0");
        check("the span sits between them", rect(laid.children().get(0)), "12,0 12x9");
        check("and the second run follows it", laid.lines().get(1).content() + "@" + laid.lines().get(1).x(), "cd@24");
        check("all of it on one line", laid.height(), 9);

        // the text property reads as the first thing inside the element, so it shares the line too
        DOMTreeElement property = text(div("w-[200px]"), "ab");
        property.append(text(span(null), "XY"));
        laid = only(property);
        check("the text property joins the line", laid.children().get(0).x(), 12);

        // a block child closes the line it finds open
        DOMTreeElement mixed = text(div("w-[200px]"), "ab");
        mixed.append(div("w-[10px] h-[4px]"));
        laid = only(mixed);
        check("a block goes under the text", rect(laid.children().get(0)), "0,9 10x4");
        check("so the box holds both", laid.height(), 13);
    }

    static void whitespace(){
        section("whitespace");
        DOMTreeElement box = div("w-[200px]");
        box.append(raw("  a   b  "));
        CTMLLayout.Box laid = only(box);
        check("runs of spaces collapse into one", laid.lines().get(0).content(), "a b");
        check("and the leading and trailing ones go", laid.lines().get(0).width(), 18);

        // a space between two elements is still a space
        DOMTreeElement between = div("w-[200px]");
        between.append(text(span(null), "a")).append(raw(" ")).append(text(span(null), "b"));
        laid = only(between);
        check("a space between two spans holds them apart", laid.children().get(1).x(), 12);

        DOMTreeElement pre = div("w-[200px] whitespace-pre");
        pre.append(raw("a\nbb"));
        laid = only(pre);
        check("pre breaks where it was written", laid.lines().size(), 2);
        check("and keeps the lines in order", laid.lines().get(1).content() + "@" + laid.lines().get(1).y(), "bb@9");
    }

    /** An inline element is broken across the lines it reaches, one box for each of them. */
    static void fragmentation(){
        section("inline fragments");
        DOMTreeElement box = div("w-[36px]");
        box.append(raw("aa"));
        DOMTreeElement run = text(span(null), "bb cc dd");
        box.append(run);

        CTMLLayout.Box laid = only(box);
        check("the span is split in two", laid.children().size(), 2);
        check("both halves are the same element", laid.children().get(0).element() == run && laid.children().get(1).element() == run, true);
        check("the first carries on from the text", rect(laid.children().get(0)), "12,0 12x9");
        check("the second starts the next line", rect(laid.children().get(1)), "0,9 30x9");
        check("each holds the words it got", laid.children().get(0).lines().get(0).content() + "|" + laid.children().get(1).lines().get(0).content(), "bb|cc dd");
        check("and the block keeps its own text", laid.lines().get(0).content(), "aa");
        check("two lines in all", laid.height(), 18);

        // the padding of an inline element goes on the ends, not on every line
        DOMTreeElement padded = div("w-[100px]");
        DOMTreeElement inset = text(span("px-[2px]"), "ab");
        padded.append(raw("x")).append(inset);
        laid = only(padded);
        check("the padding takes room on the line", rect(laid.children().get(0)), "6,0 16x9");
        check("with the text inside it", laid.children().get(0).lines().get(0).x(), 0);

        // a span inside a span becomes a box inside a box
        DOMTreeElement outer = div("w-[100px]");
        DOMTreeElement around = span(null);
        DOMTreeElement inner = text(span(null), "in");
        around.append(raw("a")).append(inner);
        outer.append(around);
        laid = only(outer);
        check("the outer span is one box", laid.children().size(), 1);
        check("holding the inner one", laid.children().get(0).children().size(), 1);
        check("which carries its own text", laid.children().get(0).children().get(0).lines().get(0).content(), "in");
        check("and the outer one keeps the rest", laid.children().get(0).lines().get(0).content(), "a");

        // an inline element with a block inside it cannot be split, so it stays one box
        DOMTreeElement blocky = div("w-[36px]");
        DOMTreeElement holder = span(null);
        holder.append(div("w-[10px] h-[4px]"));
        blocky.append(raw("aa")).append(holder);
        laid = only(blocky);
        check("a span holding a block is left whole", laid.children().size(), 1);
        check("and laid out as one box of its own", laid.children().get(0).children().size(), 1);
        check("with the block inside it, hanging off the line", rect(laid.children().get(0).children().get(0)), "12,3 10x4");
    }

    static void wrapping(){
        section("line boxes");
        // words of 12 with 6 pixel spaces: aa bb cc is 48 and dd will not fit in 60
        DOMTreeElement box = text(div("w-[60px]"), "aa bb cc dd");
        CTMLLayout.Box laid = only(box);
        check("the line fills up and breaks", laid.lines().size(), 2);
        check("the first line stops at the last space", laid.lines().get(0).content() + "@" + laid.lines().get(0).width(), "aa bb cc@48");
        check("the rest goes on the next line", laid.lines().get(1).content() + "@" + laid.lines().get(1).y(), "dd@9");
        check("two lines make the box", laid.height(), 18);

        DOMTreeElement nowrap = text(div("w-[60px] whitespace-nowrap"), "aa bb cc dd");
        laid = only(nowrap);
        check("nowrap keeps it on one line", laid.lines().size(), 1);

        DOMTreeElement centred = text(div("w-[60px] text-center"), "aa");
        laid = only(centred);
        check("text-center centres the line", laid.lines().get(0).x(), 24);

        DOMTreeElement leading = text(div("w-[60px] leading-[21px]"), "aa");
        laid = only(leading);
        check("line-height sets the line box", laid.height(), 21);
        check("with the leading split above and below", laid.lines().get(0).y(), 6);
    }

    static void verticalAlign(){
        section("vertical-align");
        CTMLLayout.Box laid = only(inlineRow("align-baseline", 10));
        check("baseline hangs the box off the text line", laid.children().get(0).y(), 0);
        check("which drops the text to meet it", laid.lines().get(0).y(), 3);
        check("and the line box holds both", laid.height(), 12);

        laid = only(inlineRow("align-middle", 10));
        check("middle lines the box up with the text", laid.children().get(0).y(), 1);
        check("over a line of its own height", laid.height(), 11);

        laid = only(inlineRow("align-top", 10));
        check("top goes against the line box", laid.children().get(0).y(), 0);
        check("leaving the text at the top too", laid.lines().get(0).y(), 0);

        laid = only(inlineRow("align-bottom", 6));
        check("bottom drops a short box to the floor", laid.children().get(0).y(), 3);
    }

    static DOMTreeElement inlineRow(String align, int height){
        DOMTreeElement box = div("w-[200px]");
        box.append(raw("x"));
        box.append(div("inline-block w-[10px] h-[" + height + "px] " + align));
        return box;
    }

    /** Where a line is allowed to break: at spaces, inside a wall of cjk, and inside a word when asked. */
    static void breaking(){
        section("break opportunities");
        // the stub makes every character six wide, so 24 pixels is four of them to a line
        DOMTreeElement wall = text(div("w-[24px]"), "\u65e5\u672c\u8a9e\u30c6\u30ad\u30b9\u30c8");
        CTMLLayout.Box laid = only(wall);
        check("a wall of cjk wraps between its characters", laid.lines().size(), 2);
        check("filling each line up", laid.lines().get(0).content(), "\u65e5\u672c\u8a9e\u30c6");

        DOMTreeElement kept = text(div("w-[24px] break-keep"), "\u65e5\u672c\u8a9e\u30c6\u30ad\u30b9\u30c8");
        check("break-keep holds it together", only(kept).lines().size(), 1);

        DOMTreeElement comma = text(div("w-[24px]"), "\u3042\u3044\u3046\u3001\u3048\u304a");
        laid = only(comma);
        check("nothing is left starting with a comma", laid.lines().get(0).content(), "\u3042\u3044\u3046\u3001");

        DOMTreeElement long1 = text(div("w-[24px]"), "aaaaaaaaaa");
        check("a long word overflows by default", only(long1).lines().size(), 1);

        DOMTreeElement broken = text(div("w-[24px] break-words"), "aaaaaaaaaa");
        laid = only(broken);
        check("break-words splits it", laid.lines().size(), 3);
        check("four to a line", laid.lines().get(0).content(), "aaaa");
        check("and the rest on the last", laid.lines().get(2).content(), "aa");

        DOMTreeElement all = text(div("w-[24px] break-all"), "ab cde fghi");
        laid = only(all);
        check("break-all fills every line to the edge", laid.lines().get(0).content(), "ab c");

        DOMTreeElement wrapped = text(div("w-[24px] break-words"), "aa bbbbbb");
        laid = only(wrapped);
        check("a word that fits a line of its own is left alone", laid.lines().get(1).content(), "bbbb");
    }

    /** A line that reads right to left puts its runs in that order, not just its characters. */
    static void rightToLeft(){
        section("right to left");
        // two hebrew words with a styled one between them: read right to left, the first word is rightmost
        DOMTreeElement box = div("w-[200px]");
        box.append(raw("\u05d0\u05d1"));
        box.append(text(span("font-bold"), "\u05d2\u05d3"));
        box.append(raw("\u05d4\u05d5"));

        CTMLLayout.Box laid = only(box);
        int first = xOf(laid, "\u05d0\u05d1");
        int last = xOf(laid, "\u05d4\u05d5");
        int middle = laid.children().get(0).x();
        check("the first run ends up on the right", first > middle, true);
        check("the styled one stays between them", middle > last, true);
        check("and they are laid out side by side", first - middle, 12);

        // plain left to right text is left exactly as it was
        DOMTreeElement plain = div("w-[200px]");
        plain.append(raw("ab"));
        plain.append(text(span(null), "cd"));
        plain.append(raw("ef"));
        laid = only(plain);
        check("nothing moves in a left to right line", laid.lines().get(0).x() + "," + laid.children().get(0).x(), "0,12");
    }

    /** Where a line of text with the given content ended up. */
    static int xOf(CTMLLayout.Box box, String content){
        for (CTMLLayout.TextLine line : box.lines()) if (line.content().equals(content)) return line.x();
        return -1;
    }

    /** line-clamp keeps the first few lines and puts the dots on the end of the last one. */
    static void clamping(){
        section("line-clamp");
        DOMTreeElement box = text(div("w-[30px] line-clamp-2"), "aa bb cc dd ee");
        CTMLLayout.Box laid = only(box);
        check("only as many lines as asked for", laid.lines().size(), 2);
        check("with the dots on the end of the last", laid.lines().get(1).content().endsWith("\u2026"), true);
        check("so the box is two lines tall", laid.height(), 18);

        DOMTreeElement room = text(div("w-[30px] line-clamp-5"), "aa bb cc dd ee");
        laid = only(room);
        check("a clamp with room to spare changes nothing", laid.lines().size(), 3);
        check("and leaves the text alone", laid.lines().get(2).content(), "ee");

        DOMTreeElement none = text(div("w-[30px] line-clamp-2 line-clamp-none"), "aa bb cc dd ee");
        check("line-clamp-none lifts it again", only(none).lines().size(), 3);

        // an inline box on a line that was cut away is not drawn
        DOMTreeElement withBox = div("w-[30px] line-clamp-1");
        withBox.append(raw("aa bb"));
        withBox.append(div("inline-block w-[8px] h-[8px]"));
        laid = only(withBox);
        check("a box past the clamp is hidden", laid.children().get(0).style().get(net.tutla.craftytml.client.ctml.ElementStyle.VISIBILITY),
                net.tutla.craftytml.client.ctml.ElementStyle.Visibility.HIDDEN);
    }

    static void margins(){
        section("collapsing margins");
        DOMTreeElement box = div("w-[100px]");
        box.append(div("w-full h-[10px] mb-4")).append(div("w-full h-[10px] mt-2"));
        CTMLLayout.Box laid = only(box);
        check("the bigger of the two margins wins", laid.children().get(1).y(), 26);
        check("so the box is only as tall as it has to be", laid.height(), 36);

        DOMTreeElement through = div("w-[100px]");
        through.append(div("w-full h-[10px]")).append(div("w-full my-2")).append(div("w-full h-[10px]"));
        laid = only(through);
        check("an empty block collapses right through", laid.children().get(2).y(), 18);
        check("and takes no room of its own", laid.height(), 28);

        DOMTreeElement first = div("w-[100px]");
        first.append(div("w-full h-[10px] mt-2"));
        laid = only(first);
        check("the first child's margin still applies", laid.children().get(0).y(), 8);

        DOMTreeElement across = text(div("w-[100px]"), "ab");
        across.append(div("w-full h-[10px] mt-2"));
        laid = only(across);
        check("margins do not collapse across text", laid.children().get(0).y(), 17);
    }

    /** A margin goes out through an edge that has nothing on it, which is what parent/child collapsing is. */
    static void collapsingThroughEdges(){
        section("collapsing through a parent's edge");
        DOMTreeElement outer = div("w-[100px]");
        DOMTreeElement wrapper = div("w-full");
        wrapper.append(div("w-full h-[10px] mt-4"));
        outer.append(wrapper).append(div("w-full h-[10px]"));

        CTMLLayout.Box laid = only(outer);
        check("the child's margin comes out of its parent", laid.children().get(0).y(), 16);
        check("so it does not push it down inside", laid.children().get(0).children().get(0).y(), 16);
        check("and the parent is only as tall as its child", laid.children().get(0).height(), 10);

        // a padding or a border on that edge is something to keep apart, so it holds the margin in
        DOMTreeElement padded = div("w-[100px]");
        DOMTreeElement held = div("w-full pt-[2px]");
        held.append(div("w-full h-[10px] mt-4"));
        padded.append(held);
        laid = only(padded);
        check("padding on the edge keeps the margin inside", laid.children().get(0).y(), 0);
        check("where it still pushes the child down", laid.children().get(0).children().get(0).y(), 18);

        // the last child's margin goes out at the bottom the same way
        DOMTreeElement trailing = div("w-[100px]");
        DOMTreeElement tail = div("w-full");
        tail.append(div("w-full h-[10px] mb-4"));
        trailing.append(tail).append(div("w-full h-[10px] mt-2"));
        laid = only(trailing);
        check("the bottom margin leaves the parent too", laid.children().get(0).height(), 10);
        check("and collapses with the next one along", laid.children().get(1).y(), 26);

        // a box that scrolls starts a context of its own, so nothing collapses out of it
        DOMTreeElement scroller = div("w-[100px] overflow-hidden");
        scroller.append(div("w-full h-[10px] mt-4"));
        DOMTreeElement page = div("w-[100px]");
        page.append(scroller);
        laid = only(page);
        check("a scroll container holds its child's margin in", laid.children().get(0).children().get(0).y(), 16);
        check("so the margin is inside its height", laid.children().get(0).height(), 26);
    }

    static void intrinsicWidths(){
        section("min-content and max-content");
        DOMTreeElement narrow = text(div("w-min"), "aa bb");
        check("w-min is the longest word", only(narrow).width(), 12);

        DOMTreeElement wide = text(div("w-max"), "aa bb");
        check("w-max is the whole line", only(wide).width(), 30);

        DOMTreeElement padded = div("w-[100px]");
        padded.append(text(div("w-max p-[10%]"), "ab"));
        check("percentage padding measures against the parent", only(padded).children().get(0).width(), 32);

        DOMTreeElement nested = div("w-max");
        nested.append(div("w-[40px] h-[4px]")).append(div("w-[70px] h-[4px]"));
        check("blocks only need their widest", only(nested).width(), 70);

        DOMTreeElement inline = div("w-max");
        inline.append(text(span(null), "aa")).append(text(span(null), "bbb"));
        check("inline children add up along the line", only(inline).width(), 30);

        DOMTreeElement wrapped = text(div("w-fit max-w-[40px]"), "aa bb cc");
        check("fit-content gives way to max-width", only(wrapped).width(), 40);

        // every run is rounded to whole pixels as it is drawn, so max-content has to be measured the
        // same way: the whole string in one go came out a pixel short of its words and wrapped anyway
        DOMTreeElement scaled = text(div("w-max text-xs"), "hide the panel");
        CTMLLayout.Box tight = only(scaled);
        check("max-content counts the runs the text is drawn in", tight.width(), 65);
        check("so text at max-content never wraps", tight.lines().size(), 1);
    }

    static void sticky(){
        section("position sticky");
        DOMTreeElement scroller = div("w-[100px] h-[50px] overflow-y-auto");
        DOMTreeElement header = div("w-full h-[10px] sticky top-0");
        scroller.append(header).append(div("w-full h-[200px]"));

        CTMLLayout.Box laid = only(scroller);
        check("it sits where the flow put it to begin with", laid.children().get(0).y(), 0);

        scroller.setScrollOffset(0, 30);
        laid = only(scroller);
        check("and holds the top as the content goes by", laid.children().get(0).y(), 0);
        check("while the content behind it moves", laid.children().get(1).y(), -20);

        scroller.setScrollOffset(0, 160);
        laid = only(scroller);
        check("it keeps its grip all the way down", laid.children().get(0).y(), 0);

        // a sticky box can never leave the box it sits in
        DOMTreeElement bounded = div("w-[100px] h-[50px] overflow-y-auto");
        DOMTreeElement group = div("w-full h-[40px]");
        group.append(div("w-full h-[10px] sticky top-0"));
        bounded.append(group).append(div("w-full h-[200px]"));
        bounded.setScrollOffset(0, 60);
        laid = only(bounded);
        check("so it goes with its parent once that is past", laid.children().get(0).children().get(0).y(), -30);
    }

    static void fixed(){
        section("position fixed");
        DOMTreeElement scroller = div("w-[100px] h-[50px] overflow-y-auto");
        scroller.append(div("fixed top-[5px] left-[5px] w-[8px] h-[8px]"));
        scroller.append(div("w-full h-[200px]"));

        scroller.setScrollOffset(0, 40);
        CTMLLayout.Box laid = only(scroller);
        CTMLLayout.Box pinned = null;
        for (CTMLLayout.Box child : laid.children()) if (child.isFixed()) pinned = child;
        check("a fixed box is measured against the viewport", rect(pinned), "5,5 8x8");
        check("and scrolling does not move it", pinned.y(), 5);
    }

    static void caching(){
        section("laying out again only when it has to");
        DOMTreeElement page = root("w-full h-full overflow-y-auto");
        page.append(text(div("w-full"), "ab"));

        lay(page, 854, 480);
        long settled = DOMTreeNode.revision();
        lay(page, 854, 480);
        check("laying out changes nothing by itself", DOMTreeNode.revision(), settled);

        page.getChildElements().get(0).getElement().applyClassStyles("w-1/2");
        check("a style change says to do it again", DOMTreeNode.revision() != settled, true);

        settled = DOMTreeNode.revision();
        page.append(div("w-full h-[4px]"));
        check("so does the tree changing shape", DOMTreeNode.revision() != settled, true);

        settled = DOMTreeNode.revision();
        page.setScrollOffset(0, 0);
        check("but setting a scroll back to what it was does not", DOMTreeNode.revision(), settled);
    }

    /** A dragged box comes back to the spot it was put, not to the same pixel count. */
    static void dragResize(){
        section("dragging across a resize");
        DOMTreeElement parent = div("w-1/2 h-[50px]");
        DOMTreeElement dragged = div("w-[20px] h-[20px]");
        dragged.property("draggable", true, null);
        parent.append(dragged);
        DOMTreeElement page = root("w-full h-full");
        page.append(parent);

        // half way along a 200 wide parent, which is 90 of the 180 the box can move in
        CTMLLayout.Box laid = lay(page, 400, 100);
        CTMLLayout.Box box = laid.children().get(0).children().get(0);
        CTMLScroll.wheel(laid, 0, 0, 0, 0); // nothing, just to keep the layout honest
        CTMLDrag.Grip grip = CTMLDrag.grab(laid, box.x() + 5, box.y() + 5);
        check("the box can be grabbed", grip != null, true);
        grip.follow(box.x() + 5 + 90, box.y() + 5);
        check("dragged to the middle of its room", dragged.getDragX(), 90);
        check("which is half of it", Math.round(dragged.getDragShareX() * 100), 50);

        // the window halves: the room halves with it, and the box keeps its half way spot
        laid = lay(page, 200, 100);
        // the parent is now 100 wide, so the 20 wide box has 80 to move in and half of that is 40
        check("still half way along after a resize", laid.children().get(0).children().get(0).x(), 40);
        check("and the offset was worked out again", dragged.getDragX(), 40);

        laid = lay(page, 400, 100);
        check("and back again when the window comes back", dragged.getDragX(), 90);
    }

    /** A sticky box inside another one answers both of them. */
    static void stickyNesting(){
        section("sticky inside sticky");
        DOMTreeElement scroller = div("w-[100px] h-[50px] overflow-y-auto");
        DOMTreeElement group = div("w-full h-[100px] sticky top-0");
        DOMTreeElement inner = div("w-full h-[10px] sticky top-[4px]");
        group.append(inner);
        scroller.append(group).append(div("w-full h-[200px]"));

        scroller.setScrollOffset(0, 30);
        CTMLLayout.Box laid = only(scroller);
        CTMLLayout.Box outer = laid.children().get(0);
        check("the outer one holds the top", outer.y(), 0);
        check("and the inner one its own offset inside it", outer.children().get(0).y(), 4);

        // a sticky header inside a plain wrapper still sticks to the scrollport
        DOMTreeElement plain = div("w-[100px] h-[50px] overflow-y-auto");
        DOMTreeElement section = div("w-full");
        section.append(div("w-full h-[10px] sticky top-0")).append(div("w-full h-[200px]"));
        plain.append(section);
        plain.setScrollOffset(0, 40);
        laid = only(plain);
        check("a header in a wrapper sticks too", laid.children().get(0).children().get(0).y(), 0);
    }

    /** A box whose subtree has not changed is the same box, so one style change is one subtree's work. */
    static void reuse(){
        section("reusing what was laid out before");
        DOMTreeElement page = root("w-full h-full");
        DOMTreeElement left = div("w-1/2");
        DOMTreeElement right = div("w-1/2");
        left.append(text(div("w-full"), "left"));
        right.append(text(div("w-full"), "right"));
        page.append(left).append(right);

        CTMLLayout.Box first = lay(page, 400, 100);
        CTMLLayout.Box again = lay(page, 400, 100);
        check("nothing changed, so every box is the one from before", again.children().get(0) == first.children().get(0), true);

        // a change inside the left half is the left half's business and nobody else's
        left.getChildElements().get(0).getElement().applyClassStyles("text-center");
        CTMLLayout.Box third = lay(page, 400, 100);
        check("the half that changed is laid out again", third.children().get(0) != again.children().get(0), true);
        check("the half that did not is left alone", third.children().get(1) == again.children().get(1), true);

        // and a resize is everybody's business
        CTMLLayout.Box resized = lay(page, 300, 100);
        check("a resize does the lot", resized.children().get(1) != third.children().get(1), true);
    }

    static void dragClamp(){
        section("dragged boxes");
        DOMTreeElement parent = div("w-[100px] h-[50px] p-[5px]");
        DOMTreeElement dragged = div("w-[20px] h-[20px]");
        dragged.property("draggable", true, null);
        parent.append(dragged);

        dragged.setDragOffset(500, 500);
        CTMLLayout.Box laid = only(parent);
        // the parent's content box is 90x40 inside its padding, so a 20x20 box stops at 70,20
        check("a drag is pulled back inside the parent", rect(laid.children().get(0)), "75,25 20x20");
        check("and the offset itself is put right", dragged.getDragX() + "," + dragged.getDragY(), "70,20");

        dragged.setDragOffset(-500, -500);
        laid = only(parent);
        check("the same on the way out the other side", rect(laid.children().get(0)), "5,5 20x20");
    }
}
