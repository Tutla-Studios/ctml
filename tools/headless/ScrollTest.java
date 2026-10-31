import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.style.TailwindParser;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.CTMLLayout;
import net.tutla.craftytml.client.render.CTMLScroll;
import net.tutla.craftytml.client.tusan.register.PropertyRegistry;

/** Overflow: what gets clipped, what scrolls, the bars, the wheel, the gutter and truncation. */
public class ScrollTest extends Harness {
    public static void main(String[] args){
        PropertyRegistry.init();
        ports();
        clamping();
        wheel();
        bars();
        gutter();
        axes();
        hiddenBars();
        text();
        truncation();
        gutterModes();
        overscroll();
        keyboard();
        smooth();
        anchoring();
        intoView();
        shares();
        fromScript();
        report();
    }

    /** The scrollport is the padding box, and the scroll size reaches to the far padding edge. */
    static void ports(){
        section("scrollport and scroll size");
        DOMTreeElement box = div("w-[100px] h-[50px] p-[10px] overflow-auto");
        box.append(div("w-[200px] h-[30px]")).append(div("w-[40px] h-[60px]"));

        CTMLLayout.Box laid = only(box);
        CTMLLayout.Port port = laid.port();
        check("box size", laid.width() + "x" + laid.height(), "100x50");
        // it overflows both ways, so there is a bar on each axis and each takes its room off the port
        check("port size", port.width() + "x" + port.height(), "96x46");
        check("content size", laid.contentWidth() + "x" + laid.contentHeight(), "76x26");
        check("scroll size", port.scrollWidth() + "x" + port.scrollHeight(), "220x110");
        check("max scroll", port.maxScrollX() + "," + port.maxScrollY(), "124,64");
    }

    /** A stored offset gets pulled back into range when the content no longer reaches that far. */
    static void clamping(){
        section("clamping");
        DOMTreeElement box = div("w-[100px] h-[50px] overflow-scroll");
        DOMTreeElement child = div("w-[100px] h-[200px]");
        box.append(child);

        box.setScrollOffset(0, 500);
        check("clamped to the end", only(box).port().scrollY(), 150);
        check("written back", box.getScrollY(), 150);

        child.getElement().setStyle(null); // the content shrinks under the offset
        child.getElement().applyClassStyles("w-[100px] h-[60px]");
        check("clamped again", only(box).port().scrollY(), 10);

        box.setScrollOffset(0, -20);
        check("never negative", only(box).port().scrollY(), 0);
    }

    /** The wheel reaches the innermost box that can still move, and the children move with it. */
    static void wheel(){
        section("wheel");
        DOMTreeElement outer = div("w-[100px] h-[100px] overflow-auto");
        DOMTreeElement inner = div("w-full h-[40px] overflow-auto");
        inner.append(div("w-full h-[200px]"));
        outer.append(inner).append(div("w-full h-[300px]"));
        DOMTreeElement page = root("w-full h-full");
        page.append(outer);

        CTMLLayout.Box laid = lay(page, 854, 480);
        check("wheel over the inner box", CTMLScroll.wheel(laid, 50, 20, 0, -1), true);
        check("inner moved", inner.getScrollY(), CTMLScroll.WHEEL_STEP);
        check("outer stayed", outer.getScrollY(), 0);

        inner.setScrollOffset(0, 160); // the inner box is at its end, so the turn falls out to the outer one
        laid = lay(page, 854, 480);
        check("falls out to the outer box", CTMLScroll.wheel(laid, 50, 20, 0, -1), true);
        check("outer moved", outer.getScrollY(), CTMLScroll.WHEEL_STEP);

        laid = lay(page, 854, 480);
        check("child follows the scroll", laid.children().get(0).children().get(0).y(), -CTMLScroll.WHEEL_STEP);
        check("the box itself stays", laid.children().get(0).y(), 0);

        outer.setScrollOffset(0, 0);
        inner.setScrollOffset(0, 0);
        laid = lay(page, 854, 480);
        check("nothing to scroll sideways", CTMLScroll.wheel(laid, 50, 20, 1, 0), false);
    }

    /** Where the bars sit, how long the thumb is and where a press on the track puts it. */
    static void bars(){
        section("bars");
        DOMTreeElement box = div("w-[100px] h-[100px] overflow-y-auto");
        box.append(div("w-full h-[200px]"));
        DOMTreeElement page = root("w-full h-full");
        page.append(box);

        CTMLLayout.Box laid = lay(page, 854, 480);
        CTMLLayout.Box scroller = laid.children().get(0);
        CTMLScroll.Bar bar = CTMLScroll.verticalBar(scroller);
        check("a bar is offered", bar != null, true);
        check("sits in the room kept for it", bar.x(), 100 - CTMLScroll.DEFAULT_THICKNESS);
        check("track runs the whole port", bar.y() + "+" + bar.height(), "0+100");
        check("thumb is half the track", bar.thumbHeight(), 50);
        check("thumb starts at the top", bar.thumbY(), 0);
        check("no bar across", CTMLScroll.horizontalBar(scroller), null);

        CTMLScroll.Grip grip = CTMLScroll.grab(laid, 98, 75);
        check("the bar takes the press", grip != null, true);
        check("thumb centred on the cursor", box.getScrollY(), 100);

        grip.follow(98, 65);
        check("drag maps onto the content", box.getScrollY(), 80);
        grip.follow(98, -400);
        check("drag stops at the start", box.getScrollY(), 0);
        grip.follow(98, 400);
        check("drag stops at the end", box.getScrollY(), 100);

        box.setScrollOffset(0, 100);
        bar = CTMLScroll.verticalBar(lay(page, 854, 480).children().get(0));
        check("thumb at the end", bar.thumbY(), 50);
    }

    /** With a gutter the bar gets its own room and the content is laid out in what is left. */
    static void gutter(){
        section("gutter");
        DOMTreeElement box = div("w-[100px] h-[100px] overflow-y-scroll scrollbar-gutter");
        box.append(div("w-full h-[200px]"));

        CTMLLayout.Box laid = only(box);
        check("box keeps its width", laid.width(), 100);
        check("room kept for the bar", laid.port().gutterRight(), CTMLScroll.DEFAULT_THICKNESS);
        check("port is narrower", laid.port().width(), 96);
        check("content is narrower", laid.contentWidth(), 96);
        check("a full width child fits inside", laid.children().get(0).width(), 96);
        check("nothing overflows sideways", laid.port().maxScrollX(), 0);
        check("bar sits in its room", CTMLScroll.verticalBar(laid).x(), 96);

        // and with nothing said at all the bar still takes its room, since these bars are not overlays:
        // the room appears along with the bar rather than being kept whether there is one or not
        DOMTreeElement plain = div("w-[100px] h-[100px] overflow-y-scroll");
        plain.append(div("w-full h-[200px]"));
        CTMLLayout.Box classic = only(plain);
        check("a bar takes its room without being asked", classic.port().gutterRight(),
                CTMLScroll.DEFAULT_THICKNESS);
        check("so the content stops short of it", classic.children().get(0).width(), 96);
        check("and nothing runs under it", classic.port().maxScrollX(), 0);

        DOMTreeElement spare = div("w-[100px] h-[100px] overflow-y-auto");
        spare.append(div("w-full h-[50px]"));
        CTMLLayout.Box roomy = only(spare);
        check("with nothing to scroll there is no bar and no room", roomy.port().gutterRight(), 0);
        check("so the content has the lot", roomy.children().get(0).width(), 100);

        DOMTreeElement bare = div("w-[100px] h-[100px] overflow-y-scroll scrollbar-hidden");
        bare.append(div("w-full h-[200px]"));
        CTMLLayout.Box full = only(bare);
        check("hidden bars keep no room", full.port().gutterRight(), 0);
        check("so the content has the lot there too", full.children().get(0).width(), 100);
    }

    /** One axis cannot spill out of a box that scrolls the other way. */
    static void axes(){
        section("axis pairs");
        ElementStyle only = TailwindParser.parse("overflow-y-scroll");
        check("the other axis turns into auto", CTMLScroll.overflowX(only), ElementStyle.Overflow.AUTO);
        check("the named axis is kept", CTMLScroll.overflowY(only), ElementStyle.Overflow.SCROLL);

        ElementStyle clipped = TailwindParser.parse("overflow-x-clip overflow-y-auto");
        check("clip next to a scroll is hidden", CTMLScroll.overflowX(clipped), ElementStyle.Overflow.HIDDEN);

        ElementStyle plain = TailwindParser.parse("overflow-clip");
        check("clip on its own stays clip", CTMLScroll.overflowX(plain), ElementStyle.Overflow.CLIP);
        check("clip keeps no offset", CTMLScroll.scrolls(ElementStyle.Overflow.CLIP), false);
        check("clip still cuts off", CTMLScroll.clips(ElementStyle.Overflow.CLIP), true);
        check("hidden is not for the user", CTMLScroll.userScrolls(ElementStyle.Overflow.HIDDEN), false);

        DOMTreeElement box = div("w-[100px] h-[100px] overflow-hidden");
        box.append(div("w-[100px] h-[300px]"));
        DOMTreeElement page = root("w-full h-full");
        page.append(box);
        box.setScrollOffset(0, 50);
        check("hidden scrolls from code", lay(page, 854, 480).children().get(0).port().scrollY(), 50);
        check("but not from the wheel", CTMLScroll.wheel(lay(page, 854, 480), 50, 50, 0, -1), false);
    }

    /** scrollbar-hidden leaves the scrolling alone and only takes the bars away. */
    static void hiddenBars(){
        section("hidden bars");
        DOMTreeElement box = div("w-[100px] h-[100px] overflow-scroll scrollbar-hidden scrollbar-gutter");
        box.append(div("w-[100px] h-[200px]"));
        DOMTreeElement page = root("w-full h-full");
        page.append(box);

        CTMLLayout.Box laid = lay(page, 854, 480);
        CTMLLayout.Box scroller = laid.children().get(0);
        check("no bar to draw", CTMLScroll.verticalBar(scroller), null);
        check("no room kept either", scroller.port().gutterRight(), 0);
        check("nothing to grab", CTMLScroll.grab(laid, 98, 50), null);
        check("the wheel still works", CTMLScroll.wheel(laid, 50, 50, 0, -1), true);

        DOMTreeElement styled = root("w-full h-full scrollbar-thin scrollbar-thumb-red-500");
        DOMTreeElement child = div("w-[100px] h-[100px] overflow-scroll");
        child.append(div("w-[100px] h-[200px]"));
        styled.append(child);
        CTMLLayout.Box inherited = lay(styled, 854, 480).children().get(0);
        check("thickness inherited", CTMLScroll.thickness(inherited.style()), 2);
        check("thumb colour inherited", CTMLScroll.thumbColor(inherited.style()) != CTMLScroll.DEFAULT_THUMB_COLOR, true);
    }

    /** A line that is not allowed to wrap is something to scroll through. */
    static void text(){
        section("text that will not wrap");
        DOMTreeElement box = text(div("w-[60px] h-[20px] overflow-x-auto whitespace-nowrap"),
                "a line far wider than the box");
        CTMLLayout.Box laid = only(box);
        check("the line is measured", laid.lines().get(0).width(), 29 * 6);
        check("and can be scrolled", laid.port().maxScrollX(), 29 * 6 - 60);
    }

    /** truncate cuts the line short with dots; a scrollable box keeps all of it. */
    static void truncation(){
        section("truncate");
        DOMTreeElement box = text(div("w-[60px] h-[20px] truncate"), "a line far wider than the box");
        CTMLLayout.TextLine line = only(box).lines().get(0);
        check("cut short with an ellipsis", line.content(), "a line fa\u2026");
        check("and it fits", line.width() <= 60, true);

        DOMTreeElement kept = text(div("w-[60px] h-[20px] text-ellipsis overflow-x-auto whitespace-nowrap"),
                "a line far wider than the box");
        CTMLLayout.Box scroller = only(kept);
        check("a scrollable box keeps the line", scroller.lines().get(0).width(), 29 * 6);
        check("and still scrolls", scroller.port().maxScrollX(), 29 * 6 - 60);

        DOMTreeElement spilling = text(div("w-[60px] h-[20px] text-ellipsis whitespace-nowrap"),
                "a line far wider than the box");
        check("nothing is cut without a clip", only(spilling).lines().get(0).width(), 29 * 6);
    }

    /** Stable keeps the room whether a bar is there or not, and both-edges keeps it on the near side too. */
    static void gutterModes(){
        section("gutter modes");
        DOMTreeElement stable = div("w-[100px] h-[100px] overflow-y-auto scrollbar-gutter-stable");
        stable.append(div("w-full h-[50px]")); // nothing overflows, so there is no bar to show

        CTMLLayout.Box laid = only(stable);
        check("room kept with no bar", laid.port().gutterRight(), CTMLScroll.DEFAULT_THICKNESS);
        check("no bar to show", CTMLScroll.verticalBar(laid), null);
        check("port is narrower anyway", laid.port().width(), 96);
        check("child is narrower anyway", laid.children().get(0).width(), 96);

        DOMTreeElement both = div("w-[100px] h-[100px] overflow-y-scroll scrollbar-both-edges");
        both.append(div("w-full h-[200px]"));
        CTMLLayout.Box edges = only(both);
        check("room on both edges", edges.port().gutterLeft() + "," + edges.port().gutterRight(), "4,4");
        check("port between them", edges.port().x() + "+" + edges.port().width(), "4+92");
        check("content between them", edges.contentX() + "+" + edges.contentWidth(), "4+92");
        check("bar still on the far edge", CTMLScroll.verticalBar(edges).x(), 96);

        // auto keeps room only while there is a bar in it, which is what a classic bar does
        DOMTreeElement plain = div("w-[100px] h-[100px] overflow-y-scroll scrollbar-gutter-auto");
        plain.append(div("w-full h-[200px]"));
        check("auto keeps room for the bar it has", only(plain).port().gutterRight(),
                CTMLScroll.DEFAULT_THICKNESS);

        DOMTreeElement empty = div("w-[100px] h-[100px] overflow-y-auto scrollbar-gutter-auto");
        empty.append(div("w-full h-[50px]"));
        check("and none for one it has not", only(empty).port().gutterRight(), 0);

        // an axis that can never show a bar keeps no room for one, however stable the gutter is
        DOMTreeElement cut = div("w-[100px] h-[100px] overflow-y-hidden overflow-x-scroll scrollbar-gutter-stable");
        cut.append(div("w-[200px] h-[200px]"));
        CTMLLayout.Box clipped = only(cut);
        check("hidden keeps no room", clipped.port().gutterRight(), 0);
        check("but the scrolling axis does", clipped.port().gutterBottom(), CTMLScroll.DEFAULT_THICKNESS);

        DOMTreeElement bare = div("w-[100px] h-[100px] overflow-y-scroll scrollbar-gutter-stable scrollbar-hidden");
        bare.append(div("w-full h-[200px]"));
        check("no bars, no room", only(bare).port().gutterRight(), 0);
    }

    /** A box told to keep the wheel does not hand it outwards once it has nothing left to give. */
    static void overscroll(){
        section("overscroll-behavior");
        for (String behaviour : new String[]{ "overscroll-auto", "overscroll-contain" }) {
            DOMTreeElement outer = div("w-[100px] h-[100px] overflow-y-scroll");
            DOMTreeElement inner = div("w-[100px] h-[50px] overflow-y-scroll " + behaviour);
            inner.append(div("w-full h-[100px]"));
            outer.append(inner);
            outer.append(div("w-full h-[200px]"));

            inner.setScrollOffset(0, 50); // already at its end
            CTMLLayout.Box laid = only(outer);
            check(behaviour + " takes the notch", CTMLScroll.wheel(laid, 10, 10, 0, -1), true);
            check(behaviour + " hands it on", outer.getScrollY(), behaviour.endsWith("auto") ? 12 : 0);
        }
    }

    /** The arrows, the pages and the ends, against whatever the keyboard is pointed at. */
    static void keyboard(){
        section("keyboard");
        DOMTreeElement box = div("w-[100px] h-[100px] overflow-y-scroll");
        box.append(div("w-full h-[300px]"));
        CTMLLayout.Box laid = only(box);

        check("a line down", CTMLScroll.key(laid, box, CTMLScroll.Nudge.LINE_DOWN), true);
        check("one wheel step", box.getScrollY(), CTMLScroll.WHEEL_STEP);

        box.setScrollOffset(0, 0);
        CTMLScroll.key(laid, box, CTMLScroll.Nudge.PAGE_DOWN);
        check("a page down keeps a strip", box.getScrollY(), 100 - CTMLScroll.PAGE_OVERLAP);

        CTMLScroll.key(laid, box, CTMLScroll.Nudge.TO_END);
        check("to the end", box.getScrollY(), 200);
        check("and no further", CTMLScroll.key(laid, box, CTMLScroll.Nudge.LINE_DOWN), false);
        CTMLScroll.key(laid, box, CTMLScroll.Nudge.TO_START);
        check("back to the start", box.getScrollY(), 0);
        check("nothing to the left", CTMLScroll.key(laid, box, CTMLScroll.Nudge.LINE_RIGHT), false);

        // with nothing pointed at, the outermost scroll container takes it
        check("the page takes it", CTMLScroll.key(laid, null, CTMLScroll.Nudge.LINE_DOWN), true);
        check("and moves", box.getScrollY(), CTMLScroll.WHEEL_STEP);

        // and a press is what points it: the innermost box under the cursor the keys can do anything to
        check("a press points it at the box under the cursor",
                net.tutla.craftytml.client.render.CTMLFocus.at(laid, 10, 10), box);
    }

    /** A box asked to slide heads for the offset over several frames rather than landing on it. */
    static void smooth(){
        section("smooth scrolling");
        // scroll-glide slides everything, the wheel and the keys included
        DOMTreeElement box = div("w-[100px] h-[100px] overflow-y-scroll scroll-glide");
        box.append(div("w-full h-[300px]"));
        CTMLLayout.Box laid = only(box);

        CTMLScroll.wheel(laid, 10, 10, 0, -1);
        check("heading there", box.getScrollTargetY(), 12);
        check("not there yet", box.getScrollY(), 0);

        check("a frame moves it", CTMLScroll.animate(laid, CTMLScroll.SLIDE_TAU), true);
        check("most of the way", box.getScrollY(), 8);
        CTMLScroll.animate(laid, 1.0f);
        check("and lands", box.getScrollY(), 12);
        check("nothing left to do", CTMLScroll.animate(laid, 1.0f), false);

        // a second notch adds to where the first was heading, not to where the content has got to
        box.slideScrollTo(0, 0);
        CTMLScroll.animate(laid, 0.001f);
        CTMLScroll.wheel(laid, 10, 10, 0, -1);
        CTMLScroll.wheel(laid, 10, 10, 0, -1);
        check("two notches add up", box.getScrollTargetY(), 24);

        // scroll-smooth is what css promises and no more: the wheel lands where it is put
        DOMTreeElement asked = div("w-[100px] h-[100px] overflow-y-scroll scroll-smooth");
        DOMTreeElement last = div("w-full h-[100px]");
        asked.append(div("w-full h-[100px]")).append(div("w-full h-[100px]")).append(last);

        CTMLLayout.Box plain = only(asked);
        CTMLScroll.wheel(plain, 10, 10, 0, -1);
        check("the wheel lands at once", asked.getScrollY(), CTMLScroll.WHEEL_STEP);
        check("with nothing left to slide", CTMLScroll.animate(plain, 1.0f), false);
        check("and the keys too", CTMLScroll.key(plain, asked, CTMLScroll.Nudge.LINE_DOWN), true);
        check("straight there", asked.getScrollY(), CTMLScroll.WHEEL_STEP * 2);

        // but a scroll that was gone looking for slides, since that is the one css slides
        asked.setScrollOffset(0, 0);
        CTMLLayout.Box again = only(asked);
        check("bringing something into view does slide", CTMLScroll.intoView(again, last), true);
        check("heading for the end", asked.getScrollTargetY(), 200);
        check("but not there yet", asked.getScrollY(), 0);
        CTMLScroll.animate(again, 1.0f);
        check("and it gets there", asked.getScrollY(), 200);

        // and a script setting the offset is asking as well
        DOMTreeElement scripted = div("w-[100px] h-[100px] overflow-y-scroll scroll-smooth");
        scripted.append(div("w-full h-[300px]"));
        CTMLLayout.Box code = only(scripted);
        scripted.scrollFromScript(0, 100);
        check("a script's scroll slides", scripted.getScrollY(), 0);
        check("towards where it asked", scripted.getScrollTargetY(), 100);
        CTMLScroll.animate(code, 1.0f);
        check("and lands there", scripted.getScrollY(), 100);
    }

    /** What is on show stays put when something above it grows. */
    static void anchoring(){
        section("scroll anchoring");
        DOMTreeElement box = div("w-[100px] h-[100px] overflow-y-scroll");
        DOMTreeElement first = div("w-full h-[100px]");
        box.append(first);
        box.append(div("w-full h-[100px]"));
        box.append(div("w-full h-[100px]"));

        box.setScrollOffset(0, 100);
        CTMLLayout.Box laid = only(box);
        check("nothing to correct yet", CTMLScroll.anchor(laid), false);
        check("the second child is held", box.getScrollAnchor(), box.getChildElements().get(1));
        check("where it shows", box.getScrollAnchorY(), 0);

        grow(first, "w-full h-[150px]"); // everything below it moves down 50
        CTMLLayout.Box grown = only(box);
        check("the drift is taken off the scroll", CTMLScroll.anchor(grown), true);
        check("so the same child is at the top", box.getScrollY(), 150);
        check("and it settles", CTMLScroll.anchor(only(box)), false);

        // a box told not to anchor lets the content slide under the eye instead
        DOMTreeElement loose = div("w-[100px] h-[100px] overflow-y-scroll overflow-anchor-none");
        DOMTreeElement top = div("w-full h-[100px]");
        loose.append(top);
        loose.append(div("w-full h-[100px]"));
        loose.append(div("w-full h-[100px]"));
        loose.setScrollOffset(0, 100);
        CTMLScroll.anchor(only(loose));
        grow(top, "w-full h-[150px]");
        check("nothing held", CTMLScroll.anchor(only(loose)), false);
        check("so the scroll stays put", loose.getScrollY(), 100);
    }

    /** Restyles an element from scratch, since applying classes only fills in what is not set yet. */
    static void grow(DOMTreeElement element, String classes){
        element.getElement().setStyle(null);
        element.getElement().applyClassStyles(classes);
    }

    /** Scrolling a child into view, from the nearest edge and no further. */
    static void intoView(){
        section("scroll into view");
        DOMTreeElement box = div("w-[100px] h-[100px] overflow-y-scroll");
        box.append(div("w-full h-[100px]"));
        box.append(div("w-full h-[100px]"));
        DOMTreeElement third = div("w-full h-[100px]");
        box.append(third);

        CTMLLayout.Box laid = only(box);
        check("brings the last one up", CTMLScroll.intoView(laid, third), true);
        check("just far enough", box.getScrollY(), 200);
        check("and leaves it there", CTMLScroll.intoView(only(box), third), false);

        DOMTreeElement firstChild = box.getChildElements().get(0);
        check("and back for the first", CTMLScroll.intoView(only(box), firstChild), true);
        check("to the near edge", box.getScrollY(), 0);

        // a box two scroll containers deep is brought all the way in one pass, since the inner
        // container is told where the element has got to rather than where it started
        DOMTreeElement outer = div("w-[100px] h-[100px] overflow-y-scroll");
        DOMTreeElement inner = div("w-full h-[200px] overflow-y-scroll");
        DOMTreeElement deep = div("w-full h-[40px]");
        inner.append(div("w-full h-[300px]")).append(deep);
        outer.append(div("w-full h-[100px]")).append(inner);

        CTMLLayout.Box nested = only(outer);
        check("both of them move", CTMLScroll.intoView(nested, deep), true);
        check("the inner one to its end", inner.getScrollY(), 140);
        // and the outer one far enough that the part of the element the inner one shows is on screen
        check("the outer one as well", outer.getScrollY(), 200);
        check("and one pass was enough", CTMLScroll.intoView(only(outer), deep), false);
    }

    /** A window of another size puts the content back where it was looking, not on the same pixel. */
    static void shares(){
        section("scroll across a resize");
        DOMTreeElement page = root("w-full h-full");
        DOMTreeElement box = div("w-full h-[50vh] overflow-y-scroll");
        box.append(div("w-full h-[300px]"));
        page.append(box);

        lay(page, 854, 480); // the box is 240 tall, so there are 60 pixels to go
        box.setScrollOffset(0, 30);
        lay(page, 854, 480);
        check("half way along", box.getScrollShareY(), 0.5f);

        CTMLLayout.Box smaller = lay(page, 854, 240).children().get(0);
        check("the box is shorter", smaller.height(), 120);
        check("so there is further to go", smaller.port().maxScrollY(), 180);
        check("still half way along", box.getScrollY(), 90);
    }

    /** The three properties a script has for scrolling a box. */
    static void fromScript(){
        section("scrolling from a script");
        DOMTreeElement box = div("w-[100px] h-[100px] overflow-scroll");
        box.append(div("w-[300px] h-[300px]"));

        box.property("scrollx", 20, null);
        box.property("scrolly", 40, null);
        check("set sideways", box.getScrollX(), 20);
        check("set down", box.getScrollY(), 40);
        check("and laid out there", only(box).port().scrollY(), 40);

        box.property("scrolly", 9000, null); // past the end, so layout pulls it back
        check("clamped by layout", only(box).port().scrollY(), 200);

        DOMTreeElement last = div("w-full h-[100px]");
        last.property("id", "last", null);
        DOMTreeElement list = div("w-[100px] h-[100px] overflow-y-scroll");
        list.append(div("w-full h-[100px]"));
        list.append(div("w-full h-[100px]"));
        list.append(last);
        list.property("scrollinto", "last", null);

        check("brings an id into view", CTMLScroll.intoViewPending(only(list)), true);
        check("scrolled to it", list.getScrollY(), 200);
        check("and only once", CTMLScroll.intoViewPending(only(list)), false);
    }
}
