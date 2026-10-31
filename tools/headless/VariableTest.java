import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.dom.DOMTreeNode;
import net.tutla.craftytml.client.dom.DOMTreeText;
import net.tutla.craftytml.client.render.CTMLLayout;

/**
 * Strings that name the context's variables: ${name} anywhere in one is read as the variable stands
 * now, so a host that changes the variable changes what the screen says without touching an element.
 */
public class VariableTest extends Harness {
    public static void main(String[] args){
        resolving();
        text();
        classes();
        redrawing();
        theId();
        report();
    }

    /** A context on its own, reading strings. */
    static void resolving(){
        section("reading a string");
        DOMContext ctx = new DOMContext(root(null));
        ctx.putVariable("who", "world");
        ctx.putVariable("count", 3);

        check("a string with no braces is itself", ctx.resolve("hello"), "hello");
        check("a name is filled in", ctx.resolve("hello ${who}"), "hello world");
        check("anything is, as it prints", ctx.resolve("${count} cookies"), "3 cookies");
        check("more than one in a string", ctx.resolve("${who}: ${count}"), "world: 3");
        check("space around the name is ignored", ctx.resolve("${ who }"), "world");
        check("a name nothing set reads as nothing", ctx.resolve("[${nobody}]"), "[]");
        check("and so does one taken away", read(ctx, "who"), "");
        check("$${ is how a string means it", ctx.resolve("$${who}"), "${who}");
        check("nothing closing it leaves it alone", ctx.resolve("a ${who"), "a ${who");
        check("null stays null", ctx.resolve(null), null);
        check("and a string is only looked at when it has one", DOMContext.hasVariables("plain"), false);
    }

    static String read(DOMContext ctx, String name){
        ctx.removeVariable(name);
        return ctx.resolve("${" + name + "}");
    }

    /** The text an element shows, and the text runs between elements. */
    static void text(){
        section("text");
        DOMTreeElement page = root("w-full h-full");
        DOMTreeElement box = tag("div", "w-max text-xs");
        box.property("text", "cookies: ${cookies}", null);
        page.append(box);

        DOMContext ctx = new DOMContext(page);
        ctx.putVariable("cookies", 0);
        check("the text is read through the context", box.getString("text"), "cookies: 0");
        check("the property still holds what was written",
                box.getPropertyValue("text"), "cookies: ${cookies}");

        ctx.putVariable("cookies", 12);
        check("and says what the variable says now", box.getString("text"), "cookies: 12");

        DOMTreeElement held = tag("div", null);
        DOMTreeText run = raw("say ${who}");
        held.append(run);
        page.append(held);
        ctx.putVariable("who", "hello");
        check("a text run reads the same way", run.getText(), "say hello");
        check("and keeps what it was written as", run.getWrittenText(), "say ${who}");
    }

    /** A class list is a string too, so a variable can name classes. */
    static void classes(){
        section("class lists");
        DOMTreeElement page = root("w-full h-full");
        DOMTreeElement box = tag("div", null);
        box.property("class", "w-4 ${mood}", null);
        page.append(box);

        // before the context, the placeholder is no class at all and nothing complains about it
        check("an unfilled placeholder styles nothing",
                box.getStyleValue(ElementStyle.DISPLAY), ElementStyle.Display.BLOCK);

        DOMContext ctx = new DOMContext(page);
        ctx.putVariable("mood", "hidden");
        check("the variable's classes land", box.getStyleValue(ElementStyle.DISPLAY),
                ElementStyle.Display.NONE);
        check("and the class the list wrote itself is still there",
                box.getStyle().get(ElementStyle.WIDTH).resolve(0, 854, 480), 16);

        ctx.putVariable("mood", "opacity-50");
        check("changing it reads the list again", box.getStyleValue(ElementStyle.DISPLAY),
                ElementStyle.Display.BLOCK);
        check("onto what the variable says now", box.getStyleValue(ElementStyle.OPACITY), 0.5f);
    }

    /** And the screen is told, which is what makes any of it show up. */
    static void redrawing(){
        section("saying so");
        DOMTreeElement page = root("w-full h-full");
        DOMTreeElement box = tag("div", "w-max text-xs");
        box.property("text", "${word}", null);
        page.append(box);

        DOMContext ctx = new DOMContext(page);
        ctx.putVariable("word", "hi");

        long before = DOMTreeNode.revision();
        ctx.putVariable("word", "hello");
        check("a variable that changed is a change to lay out again",
                DOMTreeNode.revision() > before, true);

        long quiet = DOMTreeNode.revision();
        ctx.putVariable("word", "hello");
        check("one set to what it already was is not", DOMTreeNode.revision(), quiet);

        ctx.putVariable("unmentioned", "nothing says this");
        check("and nor is one no string names", DOMTreeNode.revision(), quiet);

        CTMLLayout.Box laid = lay(page, 854, 480).children().get(0);
        check("the box is sized to what it says now", laid.width(), 5 * 6);
        check("which is what is drawn", laid.lines().get(0).content(), "hello");
    }

    /** The one string that is never read this way. */
    static void theId(){
        section("the id is not a string like the others");
        DOMTreeElement page = root(null);
        DOMTreeElement box = tag("div", null);
        box.property("id", "box-${n}", null);
        page.append(box);

        DOMContext ctx = new DOMContext(page);
        ctx.putVariable("n", 1);
        check("an id is what it was written as", box.getId(), "box-${n}");
        check("so it is found by that", ctx.getElementById("box-${n}") == box, true);
    }
}
