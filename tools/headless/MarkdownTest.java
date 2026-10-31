import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.CTMLLayout;
import net.tutla.craftytml.client.tusan.CTMLTusan;
import net.tutla.craftytml.client.tusan.register.PropertyRegistry;
import net.tutla.tums.tusan.MinecraftEnvironment;
import net.tutla.tums.tusan.interpreter.Interpreter;

import java.nio.file.Files;
import java.nio.file.Path;

/** The elements a piece of markdown turns into: lists and their markers, the text ones, hr and br. */
public class MarkdownTest extends Harness {
    public static void main(String[] args) throws Exception {
        PropertyRegistry.init();
        text();
        blocks();
        markers();
        counting();
        position();
        nesting();
        breaks();
        parsing();
        report();
    }

    static DOMTreeElement item(String content){
        return text(tag("li", null), content);
    }

    /** The marker of a list item, which is the line it keeps left of its own content. */
    static String marker(CTMLLayout.Box item){
        for (CTMLLayout.TextLine line : item.lines()) {
            if (line.x() < 0) return line.content();
        }
        return null;
    }

    /** How many rows of text a box draws, since one row is a run per style rather than one line. */
    static int rows(CTMLLayout.Box box){
        java.util.Set<Integer> ys = new java.util.TreeSet<>();
        for (CTMLLayout.TextLine line : box.lines()) ys.add(line.y());
        return ys.size();
    }

    /** The markers of a whole list, in order. */
    static String markers(DOMTreeElement list){
        StringBuilder out = new StringBuilder();
        for (CTMLLayout.Box item : only(list).children()) {
            if (!out.isEmpty()) out.append(" ");
            out.append(marker(item));
        }
        return out.toString();
    }

    static void text(){
        section("the ones that only dress the text");
        check("b is bold", only(tag("b", null)).style().get(ElementStyle.BOLD), true);
        check("strong too", only(tag("strong", null)).style().get(ElementStyle.BOLD), true);
        check("i is italic", only(tag("i", null)).style().get(ElementStyle.ITALIC), true);
        check("em too", only(tag("em", null)).style().get(ElementStyle.ITALIC), true);
        check("s is struck through", only(tag("s", null)).style().get(ElementStyle.STRIKETHROUGH), true);
        check("del too", only(tag("del", null)).style().get(ElementStyle.STRIKETHROUGH), true);
        check("u is underlined", only(tag("u", null)).style().get(ElementStyle.UNDERLINE), true);
        check("ins too", only(tag("ins", null)).style().get(ElementStyle.UNDERLINE), true);

        check("and they all run along the line", only(tag("em", null)).style().get(ElementStyle.DISPLAY),
                ElementStyle.Display.INLINE);

        // they nest, since each one only says its own thing and the rest comes down the tree
        DOMTreeElement line = div("w-[200px]");
        DOMTreeElement bold = tag("b", null);
        DOMTreeElement both = text(tag("em", null), "both");
        bold.append(both);
        line.append(bold);
        CTMLLayout.Box nested = only(line).children().getFirst().children().getFirst();
        check("bold inside italic is both", nested.style().get(ElementStyle.BOLD) + ","
                + nested.style().get(ElementStyle.ITALIC), "true,true");
    }

    static void blocks(){
        section("the ones that are blocks of their own");
        CTMLLayout.Box heading = only(text(tag("h1", null), "title"));
        check("h1 is bold", heading.style().get(ElementStyle.BOLD), true);
        check("and twice the size", heading.style().get(ElementStyle.FONT_SIZE), 32);
        check("so its line is twice as tall", heading.lines().getFirst().scale(), 2.0f);
        check("h6 is the smallest", only(tag("h6", null)).style().get(ElementStyle.FONT_SIZE), 14);
        check("and they are blocks", only(tag("h3", null)).style().get(ElementStyle.DISPLAY),
                ElementStyle.Display.BLOCK);

        // a rule is the top border of a box with no height, so it comes out one pixel of the text colour
        DOMTreeElement page = div("w-[100px]");
        page.append(tag("hr", null));
        CTMLLayout.Box rule = only(page).children().getFirst();
        check("hr is a line across the block", rule.width() + "x" + rule.height(), "100x1");
        check("with room above and below it", rule.y(), 4);

        CTMLLayout.Box quote = only(text(tag("blockquote", null), "said"));
        check("blockquote keeps a line down its left", quote.style().get(ElementStyle.BORDER_LEFT_WIDTH), 2);
        check("and holds its text clear of it", quote.contentX() - quote.x(), 6);

        CTMLLayout.Box pre = only(text(tag("pre", null), "two  spaces"));
        check("pre keeps the spacing it was written with", pre.lines().getFirst().content(), "two  spaces");
        check("where a paragraph would not", only(text(tag("p", null), "two  spaces"))
                .lines().getFirst().content(), "two spaces");
        check("and a paragraph has room above and below", only(text(tag("p", null), "words"))
                .style().get(ElementStyle.MARGIN_TOP).toString(), "4px");
    }

    static void markers(){
        section("what an item is marked with");
        DOMTreeElement list = tag("ul", null);
        list.append(item("one")).append(item("two"));
        check("a ul is bulleted", markers(list), "• •");
        check("and the bullet is one character", marker(only(list).children().getFirst()).length(), 1);

        check("list-circle", markers(withClass("ul", "list-circle", 2)), "◦ ◦");
        check("list-square", markers(withClass("ul", "list-square", 2)), "■ ■");
        check("list-none has none at all", marker(only(withClass("ul", "list-none", 1)).children().getFirst()), null);

        check("an ol is numbered", markers(withClass("ol", null, 3)), "1. 2. 3.");
        check("list-decimal-leading-zero", markers(withClass("ol", "list-decimal-leading-zero", 2)), "01. 02.");
        check("list-lower-alpha", markers(withClass("ol", "list-lower-alpha", 3)), "a. b. c.");
        check("list-upper-alpha", markers(withClass("ol", "list-upper-alpha", 2)), "A. B.");
        check("list-lower-roman", markers(withClass("ol", "list-lower-roman", 4)), "i. ii. iii. iv.");
        check("list-upper-roman", markers(withClass("ol", "list-upper-roman", 6)), "I. II. III. IV. V. VI.");
        check("and the arbitrary spelling", markers(withClass("ol", "list-[upper-alpha]", 2)), "A. B.");
        check("a type nothing knows is reported",
                net.tutla.craftytml.client.ctml.style.TailwindParser
                        .parseInto(ElementStyle.builder(), "list-[squiggle]").size(), 1);

        // the type comes down the tree, so a list says it once and any one item can say otherwise
        DOMTreeElement mixed = tag("ul", null);
        mixed.append(item("plain")).append(text(tag("li", "list-square"), "its own"));
        check("an item can be marked its own way", markers(mixed), "\u2022 \u25A0");

        DOMTreeElement said = tag("ul", "list-circle");
        said.append(item("a")).append(item("b"));
        check("and what the list says reaches all of them", markers(said), "\u25E6 \u25E6");

        // a bullet is a type, not an element: anything can be a list item
        DOMTreeElement loose = div("w-[100px] list-item list-square");
        check("list-item marks a plain div", marker(only(loose)), "■");
    }

    static DOMTreeElement withClass(String tag, String classes, int items){
        DOMTreeElement list = tag(tag, classes);
        for (int i = 0; i < items; i++) list.append(item("item"));
        return list;
    }

    static void counting(){
        section("counting the items");
        DOMTreeElement from = tag("ol", null);
        from.property("start", 5, null);
        from.append(item("a")).append(item("b"));
        check("start says what to count from", markers(from), "5. 6.");

        DOMTreeElement down = tag("ol", null);
        down.property("reversed", true, null);
        down.append(item("a")).append(item("b")).append(item("c"));
        check("reversed counts down from the last", markers(down), "3. 2. 1.");

        DOMTreeElement said = tag("ol", null);
        DOMTreeElement third = item("c");
        third.property("value", 10, null);
        said.append(item("a")).append(item("b")).append(third).append(item("d"));
        check("an item can name its own number, and the rest carry on", markers(said), "1. 2. 10. 11.");

        DOMTreeElement mixed = tag("ol", null);
        mixed.append(item("a")).append(div("w-full h-[2px]")).append(item("b"));
        check("only the items are counted", markers(mixed), "1. null 2.");

        DOMTreeElement loose = tag("li", null);
        check("an item with no list around it is the first", marker(only(loose)), "•");
    }

    static void position(){
        section("where the marker sits");
        DOMTreeElement outside = tag("ul", "w-[100px]");
        outside.append(item("text"));
        CTMLLayout.Box item = only(outside).children().getFirst();
        check("outside, it hangs left of the content", item.lines().getLast().x() < 0, true);
        check("and the content has the whole item", item.contentWidth(), item.width());
        check("in the room the list's own padding leaves", only(outside).contentX() - only(outside).x(), 10);

        DOMTreeElement inside = tag("ul", "w-[100px] list-inside");
        inside.append(item("text"));
        CTMLLayout.Box held = only(inside).children().getFirst();
        check("inside, it takes its room out of the content", held.contentWidth() < held.width(), true);
        check("which is as wide as the marker and its gap", held.width() - held.contentWidth(), 6 + 2);
        check("and the text starts past it", held.contentX() - held.x(), 8);
        check("the marker is still drawn at the item's own edge",
                held.contentX() + held.lines().getLast().x() - held.x(), 0);
    }

    static void nesting(){
        section("a list inside a list");
        DOMTreeElement outer = tag("ul", "w-[100px]");
        DOMTreeElement item = item("outer");
        DOMTreeElement inner = tag("ol", null);
        inner.append(item("a")).append(item("b"));
        item.append(inner);
        outer.append(item);

        CTMLLayout.Box laid = only(outer);
        CTMLLayout.Box held = laid.children().getFirst();
        check("the item holds the list", held.children().getFirst().element().getTag(), "ol");
        check("set in by the inner list's padding", held.children().getFirst().children().getFirst().x(),
                held.contentX() + 10);
        check("the outer item keeps its bullet", marker(held), "•");
        check("and the inner ones count from one",
                marker(held.children().getFirst().children().getFirst()), "1.");
        // its own line, then the inner list: a margin above it, two items, and a margin that
        // collapses out of the bottom of the item
        check("the outer item is as tall as its text and the list under it", held.height(), 9 + 4 + 18);
    }

    static void breaks(){
        section("br");
        DOMTreeElement line = text(div("w-[200px]"), "before");
        line.append(tag("br", null));
        line.append(raw("after"));

        CTMLLayout.Box laid = only(line);
        check("the line carries on underneath", laid.lines().size(), 2);
        check("what came before stays put", laid.lines().getFirst().content(), "before");
        check("and the rest starts a line of its own", laid.lines().get(1).content(), "after");
        check("one line down", laid.lines().get(1).y(), 9);
        check("so the box is two lines tall", laid.height(), 18);

        DOMTreeElement twice = text(div("w-[200px]"), "a");
        twice.append(tag("br", null)).append(tag("br", null)).append(raw("b"));
        check("two of them leave a line empty", only(twice).lines().get(1).y(), 18);
        check("and it holds nothing", only(twice).lines().get(1).content(), "b");

        check("br holds nothing itself", tag("br", null).getElement().hasChildren, false);
        check("and neither does hr", tag("hr", null).getElement().hasChildren, false);
    }

    /** And all of it written out the way a page of markdown would come out. */
    static void parsing() throws Exception {
        section("written as ctml");
        Path file = Path.of(System.getProperty("java.io.tmpdir"), "ctml-markdown-test.ctm");
        Files.writeString(file, """
                <ctml class="w-full h-full">
                  <h2 text="Notes"></h2>
                  <p>plain, <b text="bold"></b>, <em text="slanted"></em> and <del text="dropped"></del><br>and a second line</p>
                  <hr>
                  <ul>
                    <li text="first"></li>
                    <li text="second"></li>
                  </ul>
                  <ol start=2 class="list-lower-roman">
                    <li text="two"></li>
                    <li text="three"></li>
                  </ol>
                  <blockquote text="as they say"></blockquote>
                  <pre text="  spacing  kept"></pre>
                </ctml>
                """);

        Interpreter interpreter = new Interpreter(CTMLTusan.tusan());
        interpreter.setup(null, null, null, file, MinecraftEnvironment.CLIENT);
        interpreter.compile();
        DOMTreeElement root = (DOMTreeElement) interpreter.returned;

        CTMLLayout.Box page = lay(root, 854, 480);
        StringBuilder tags = new StringBuilder();
        for (CTMLLayout.Box child : page.children()) tags.append(child.element().getTag()).append(" ");
        check("every element came out of the file", tags.toString().strip(),
                "h2 p hr ul ol blockquote pre");
        check("the parse was clean", interpreter.caughtError, false);

        CTMLLayout.Box paragraph = page.children().get(1);
        // a line is drawn in a run per style, so what the break did is show in how many rows there are
        check("the br broke the paragraph in two", rows(paragraph), 2);
        check("the bold word is in there", paragraph.children().getFirst().element().getTag(), "b");

        check("the bullets are there", marker(page.children().get(3).children().getFirst()), "•");
        check("and the numbers start where they were told",
                marker(page.children().get(4).children().getFirst()), "ii.");
        check("hr needs no closing tag", page.children().get(2).height(), 1);
    }
}
