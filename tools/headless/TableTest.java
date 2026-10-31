import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.CTMLLayout;
import net.tutla.craftytml.client.tusan.register.PropertyRegistry;

/** Tables and multi column: column widths from the content, rows as tall as their cells, balanced columns. */
public class TableTest extends Harness {
    public static void main(String[] args){
        PropertyRegistry.init();
        columnWidths();
        rowsAndCells();
        fixed();
        multiColumn();
        report();
    }

    static DOMTreeElement row(String... texts){
        DOMTreeElement row = div("table-row");
        for (String text : texts) row.append(text(div("table-cell"), text));
        return row;
    }

    static void columnWidths(){
        section("column widths");
        // "aaaa" is 24 wide and "b" is 6, and there is 100 to go round, so each gets what it asked for
        DOMTreeElement table = div("w-[100px] table");
        table.append(row("aaaa", "b"));
        CTMLLayout.Box laid = only(table);
        CTMLLayout.Box cells = laid.children().get(0);
        check("a column takes what its content needs", cells.children().get(0).width() > 24, true);
        check("and the rest is shared out", cells.children().get(0).width() + cells.children().get(1).width(), 100);

        // too little room: the columns give up the same share of what they asked for
        DOMTreeElement tight = div("w-[30px] table");
        tight.append(row("aaaa aaaa", "bb bb"));
        laid = only(tight);
        cells = laid.children().get(0);
        // 24 and 12 is the least the two of them can do, which is more than there is, so it overflows
        check("they stop at what they need", cells.children().get(0).width() + "+" + cells.children().get(1).width(), "24+12");
    }

    static void rowsAndCells(){
        section("rows and cells");
        DOMTreeElement table = div("w-[100px] table");
        table.append(row("a", "b")).append(row("c", "d"));
        CTMLLayout.Box laid = only(table);
        check("two rows", laid.children().size(), 2);
        check("each with two cells", laid.children().get(0).children().size(), 2);
        check("one under the other", laid.children().get(1).y(), 9);
        check("and the row spans the table", laid.children().get(0).width(), 100);

        // a cell with two lines in it makes the whole row taller, and the others stretch to match
        DOMTreeElement tall = div("w-[30px] table");
        DOMTreeElement first = div("table-row");
        first.append(text(div("table-cell"), "aa bb cc")).append(text(div("table-cell"), "d"));
        tall.append(first);
        laid = only(tall);
        int rowHeight = laid.children().get(0).height();
        check("the row is as tall as its tallest cell", rowHeight > 9, true);
        check("which is the one that had to wrap", laid.children().get(0).children().get(0).height(), rowHeight);
        check("and the short cell is stretched to it", laid.children().get(0).children().get(1).height(), rowHeight);

        DOMTreeElement spaced = div("w-[100px] table gap-x-[10px] gap-y-[4px]");
        spaced.append(row("a", "b")).append(row("c", "d"));
        laid = only(spaced);
        check("the column gap sits between the cells", laid.children().get(0).children().get(1).x(), 55);
        check("and the row gap between the rows", laid.children().get(1).y(), 13);
    }

    static void fixed(){
        section("table-fixed");
        DOMTreeElement table = div("w-[99px] table table-fixed");
        table.append(row("aaaaaaaa", "b", "c"));
        CTMLLayout.Box cells = only(table).children().get(0);
        check("every column the same width", cells.children().get(0).width() + "," + cells.children().get(1).width(), "33,33");
        check("whatever is in them", cells.children().get(2).width(), 33);
    }

    static void multiColumn(){
        section("multi column");
        DOMTreeElement box = div("w-[100px] columns-2 gap-x-[10px]");
        for (int i = 0; i < 4; i++) box.append(div("w-full h-[10px]"));
        CTMLLayout.Box laid = only(box);
        check("the children are split between the columns", laid.children().get(2).x(), 55);
        check("two to each", laid.children().get(2).y(), 0);
        check("and the box is one column tall", laid.height(), 20);
        check("each child as wide as its column", laid.children().get(0).width(), 45);

        // an odd one out goes in the first column, since nothing is split down the middle
        DOMTreeElement odd = div("w-[100px] columns-2");
        for (int i = 0; i < 3; i++) odd.append(div("w-full h-[10px]"));
        laid = only(odd);
        check("two in the first column", laid.children().get(1).y(), 10);
        check("one in the second", laid.children().get(2).x() + "," + laid.children().get(2).y(), "50,0");

        DOMTreeElement byWidth = div("w-[100px] columns-[30px]");
        for (int i = 0; i < 3; i++) byWidth.append(div("w-full h-[10px]"));
        laid = only(byWidth);
        check("columns by width: three of them fit", laid.children().get(2).x(), 66);
    }
}
