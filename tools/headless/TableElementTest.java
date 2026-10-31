import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.CTMLLayout;
import net.tutla.craftytml.client.tusan.CTMLTusan;
import net.tutla.craftytml.client.tusan.register.PropertyRegistry;
import net.tutla.tums.tusan.MinecraftEnvironment;
import net.tutla.tums.tusan.interpreter.Interpreter;

import java.nio.file.Files;
import java.nio.file.Path;

/** The table elements themselves: what they default to, how they are written, and what the spans do. */
public class TableElementTest extends Harness {
    public static void main(String[] args) throws Exception {
        PropertyRegistry.init();
        defaults();
        colspan();
        rowspan();
        grouping();
        caption();
        collapse();
        parsing();
        report();
    }

    /** A row with the given cells in it. */
    static DOMTreeElement row(String... texts){
        DOMTreeElement row = tag("tr", null);
        for (String text : texts) row.append(cell("td", text, 1, 1));
        return row;
    }

    static void defaults(){
        section("what the elements come as");
        DOMTreeElement table = tag("table", "w-[100px]");
        table.append(row("a", "b"));
        CTMLLayout.Box laid = only(table);
        check("a table lays itself out as one", laid.children().size(), 1);
        check("with a row inside it", laid.children().get(0).element().getTag(), "tr");
        check("and cells in that", laid.children().get(0).children().get(0).element().getTag(), "td");

        DOMTreeElement headings = tag("table", "w-[100px]");
        DOMTreeElement tr = tag("tr", null);
        tr.append(cell("th", "ab", 1, 1)).append(cell("td", "cd", 1, 1));
        headings.append(tr);
        laid = only(headings);
        CTMLLayout.Box heading = laid.children().get(0).children().get(0);
        check("a th comes bold", heading.style().get(net.tutla.craftytml.client.ctml.ElementStyle.BOLD), true);
        check("and centred", heading.lines().get(0).x() > 0, true);
    }

    static void colspan(){
        section("colspan");
        DOMTreeElement table = tag("table", "w-[90px] table-fixed");
        DOMTreeElement first = tag("tr", null);
        first.append(cell("td", "a", 2, 1)).append(cell("td", "b", 1, 1));
        DOMTreeElement second = row("c", "d", "e");
        table.append(first).append(second);

        CTMLLayout.Box laid = only(table);
        CTMLLayout.Box wide = laid.children().get(0).children().get(0);
        check("a cell across two columns is twice as wide", wide.width(), 60);
        check("and the one after it takes the third", laid.children().get(0).children().get(1).x(), 60);
        check("the row under it has three of its own", laid.children().get(1).children().size(), 3);
        check("lining up with the columns", laid.children().get(1).children().get(2).x(), 60);

        // a span wider than the table is cut down to it
        DOMTreeElement over = tag("table", "w-[90px] table-fixed");
        DOMTreeElement only = tag("tr", null);
        only.append(cell("td", "a", 9, 1));
        over.append(only);
        check("a span can only be as wide as the table", only(over).children().get(0).children().get(0).width(), 90);
    }

    static void rowspan(){
        section("rowspan");
        DOMTreeElement table = tag("table", "w-[60px] table-fixed");
        DOMTreeElement first = tag("tr", null);
        first.append(cell("td", "a", 1, 2)).append(cell("td", "b", 1, 1));
        DOMTreeElement second = tag("tr", null);
        second.append(cell("td", "c", 1, 1));
        table.append(first).append(second);

        CTMLLayout.Box laid = only(table);
        CTMLLayout.Box tall = laid.children().get(0).children().get(0);
        check("a cell down two rows is twice as tall", tall.height(), 18);
        check("the row below starts under the first", laid.children().get(1).y(), 9);
        check("and its cell steps over the one above it", laid.children().get(1).children().get(0).x(), 30);
        check("the table is both rows tall", laid.height(), 18);
    }

    static void grouping(){
        section("thead, tbody and tfoot");
        DOMTreeElement table = tag("table", "w-[60px] table-fixed");
        DOMTreeElement foot = tag("tfoot", null);
        foot.append(row("foot"));
        DOMTreeElement body = tag("tbody", null);
        body.append(row("body"));
        DOMTreeElement head = tag("thead", null);
        head.append(row("head"));
        // written in the wrong order on purpose
        table.append(foot).append(body).append(head);

        CTMLLayout.Box laid = only(table);
        check("three groups", laid.children().size(), 3);
        check("the head comes first whatever the order", laid.children().get(0).element().getTag(), "thead");
        check("then the body", laid.children().get(1).element().getTag(), "tbody");
        check("and the foot last", laid.children().get(2).element().getTag(), "tfoot");
        check("a group holds its rows", laid.children().get(0).children().get(0).element().getTag(), "tr");
        check("and is as tall as them", laid.children().get(0).height(), 9);
        check("one after the other", laid.children().get(2).y(), 18);
    }

    static void caption(){
        section("caption");
        DOMTreeElement table = tag("table", "w-[60px] table-fixed");
        DOMTreeElement caption = cell("caption", "cap", 1, 1);
        table.append(caption).append(row("a"));

        CTMLLayout.Box laid = only(table);
        check("the caption goes above the table", laid.children().get(0).element().getTag(), "caption");
        check("across the whole of it", laid.children().get(0).width(), 60);
        check("with the rows under it", laid.children().get(1).y(), 9);
        check("and the table as tall as both", laid.height(), 18);

        DOMTreeElement below = tag("table", "w-[60px] table-fixed");
        DOMTreeElement under = tag("caption", "caption-bottom");
        under.property("text", "cap", null);
        below.append(under).append(row("a"));
        laid = only(below);
        check("caption-bottom puts it underneath", laid.children().get(1).y(), 9);
        check("with the row on top", laid.children().get(0).element().getTag(), "tr");
    }

    static void collapse(){
        section("border-collapse");
        DOMTreeElement separate = tag("table", "w-[100px] table-fixed");
        DOMTreeElement row = tag("tr", null);
        row.append(cell("td", "a", 1, 1)).append(cell("td", "b", 1, 1));
        row.getChildElements().forEach(cell -> cell.getElement().applyClassStyles("border-2"));
        separate.append(row);
        CTMLLayout.Box laid = only(separate);
        check("apart, the two borders each keep their room", laid.children().get(0).children().get(1).x(), 50);

        DOMTreeElement collapsed = tag("table", "w-[100px] table-fixed border-collapse");
        DOMTreeElement row2 = tag("tr", null);
        row2.append(cell("td", "a", 1, 1)).append(cell("td", "b", 1, 1));
        row2.getChildElements().forEach(cell -> cell.getElement().applyClassStyles("border-2"));
        collapsed.append(row2);
        laid = only(collapsed);
        check("collapsed, the second sits on the first's edge", laid.children().get(0).children().get(1).x(), 49);
        check("so the columns have more room", laid.children().get(0).children().get(0).width(), 51);
    }

    /** And the whole thing written out the way it would be in a script. */
    static void parsing() throws Exception {
        section("written as ctml");
        Path file = Path.of(System.getProperty("java.io.tmpdir"), "ctml-table-test.ctm");
        Files.writeString(file, """
                <ctml class="w-full h-full">
                  <table class="w-[120px] table-fixed border-collapse">
                    <caption text="numbers"></caption>
                    <thead>
                      <tr><th text="one"></th><th text="two"></th><th text="three"></th></tr>
                    </thead>
                    <tbody>
                      <tr><td colspan=2 text="wide"></td><td text="c"></td></tr>
                      <tr><td rowspan=2 text="tall"></td><td text="d"></td><td text="e"></td></tr>
                      <tr><td text="f"></td><td text="g"></td></tr>
                    </tbody>
                  </table>
                </ctml>
                """);

        Interpreter interpreter = new Interpreter(CTMLTusan.tusan());
        interpreter.setup(null, null, null, file, MinecraftEnvironment.CLIENT);
        interpreter.compile();
        DOMTreeElement root = (DOMTreeElement) interpreter.returned;

        CTMLLayout.Box page = lay(root, 854, 480);
        CTMLLayout.Box table = page.children().get(0);
        check("the table came out of the file", table.element().getTag(), "table");
        check("with its caption", table.children().get(0).element().getTag(), "caption");
        check("a head and a body", table.children().get(1).element().getTag() + "," + table.children().get(2).element().getTag(), "thead,tbody");
        check("three rows in the body", table.children().get(2).children().size(), 3);

        CTMLLayout.Box wide = table.children().get(2).children().get(0).children().get(0);
        check("colspan=2 was read off the tag", wide.width() > 50, true);
        check("and rowspan=2 as well", table.children().get(2).children().get(1).children().get(0).height(), 18);
        check("the last row steps over the tall cell", table.children().get(2).children().get(2).children().get(0).x() > table.children().get(2).children().get(1).children().get(0).x(), true);
    }
}
