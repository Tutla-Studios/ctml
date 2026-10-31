import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.property.TabIndex;
import net.tutla.craftytml.client.dom.CTMLCallback;
import net.tutla.craftytml.client.dom.CTMLCallback.CallbackType;
import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.CTMLEvents;
import net.tutla.craftytml.client.render.CTMLFocus;
import net.tutla.craftytml.client.render.CTMLLayout;
import net.tutla.craftytml.client.tusan.CTMLTusan;
import net.tutla.craftytml.client.tusan.register.PropertyRegistry;
import net.tutla.tums.tusan.MinecraftEnvironment;
import net.tutla.tums.tusan.interpreter.Interpreter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The button, the ids every element ends up with, and the callbacks a press finds through them. */
public class ButtonTest extends Harness {
    public static void main(String[] args) throws Exception {
        PropertyRegistry.init();
        defaults();
        ids();
        lookup();
        clicks();
        keys();
        bubbling();
        hovering();
        classes();
        parsing();
        report();
    }

    /** Everything a callback was handed, in the order it was handed it. */
    static List<CTMLCallback> heard = new ArrayList<>();

    static DOMContext wired(DOMTreeElement root, String name){
        heard = new ArrayList<>();
        DOMContext ctx = new DOMContext(root);
        ctx.putCallBack(name, heard::add);
        return ctx;
    }

    static void defaults(){
        section("what a button is");
        DOMTreeElement button = text(tag("button", null), "press");
        CTMLLayout.Box laid = only(button);

        check("it sits in the line rather than breaking it",
                laid.style().get(ElementStyle.DISPLAY), ElementStyle.Display.INLINE_BLOCK);
        check("it is as wide as its text", laid.width(), 30);
        check("and centres it", laid.style().get(ElementStyle.TEXT_ALIGN), ElementStyle.TextAlign.CENTER);
        check("tab stops on it without being told to", CTMLFocus.tabbable(laid), true);
        check("which is a tab index of zero", button.getTabIndex(), 0);
        check("a press on it is an input", button.firesInput(), true);
        check("and its label stays on one line", laid.lines().size(), 1);
        check("where a press on a div is not", div(null).firesInput(), false);

        DOMTreeElement told = text(tag("button", null), "press");
        told.property("tabindex", -1, null);
        check("a tabindex of its own still wins", told.getTabIndex(), -1);
        check("so tab can be told to leave it out", CTMLFocus.tabbable(only(told)), false);
        check("a div is left out of the order", div(null).getTabIndex(), TabIndex.UNSET);
    }

    static void ids(){
        section("every element has an id");
        DOMTreeElement named = div(null);
        named.property("id", "panel", null);
        check("the one a script gave it is kept", named.getId(), "panel");

        DOMTreeElement first = div(null);
        DOMTreeElement second = div(null);
        check("one made up says what it is", first.getId().startsWith("div-"), true);
        check("and two of them are never the same", first.getId().equals(second.getId()), false);
        check("asking twice gives the same one back", first.getId(), first.getId());
        check("a button's says button", tag("button", null).getId().startsWith("button-"), true);

        // the made up ones are handed out in order and skip anything a script wrote, so a script that
        // happens to name an element "div-1" cannot be given the same name twice
        DOMTreeElement clashing = div(null);
        clashing.property("id", first.getId(), null);
        check("a script may take a name already handed out", clashing.getId(), first.getId());
        check("but nothing new lands on it", div(null).getId().equals(first.getId()), false);
    }

    static void lookup(){
        section("getElementById");
        DOMTreeElement page = root("w-full h-full");
        DOMTreeElement panel = div("w-4 h-4");
        panel.property("id", "panel", null);
        DOMTreeElement deep = div("w-2 h-2");
        deep.property("id", "deep", null);
        panel.append(deep);
        page.append(panel);

        DOMContext ctx = new DOMContext(page);
        check("a child is found", ctx.getElementById("panel") == panel, true);
        check("and one further down", ctx.getElementById("deep") == deep, true);
        check("the head node itself counts", ctx.getElementById(page.getId()) == page, true);
        check("nothing by that name is null", ctx.getElementById("nowhere"), null);
        check("and so is no name at all", ctx.getElementById(null), null);

        // building the context names the whole tree, so an element nobody named can still be reached
        String made = deep.getId();
        DOMTreeElement plain = div("w-1 h-1");
        panel.append(plain);
        check("one named by the context is reachable", new DOMContext(page).getElementById(plain.getId()) == plain, true);
        check("and naming it changed nothing above it", ctx.getElementById(made) == deep, true);
    }

    static void clicks(){
        section("a press finds the callback");
        DOMTreeElement page = root("w-full h-full");
        DOMTreeElement button = text(tag("button", "w-[40px] h-[20px]"), "press");
        button.property("callback", "pressed", null);
        page.append(button);

        DOMContext ctx = wired(page, "pressed");
        CTMLLayout.Box laid = lay(page, 854, 480);

        check("a left press on it is heard",
                CTMLEvents.click(laid, ctx, CTMLEvents.MouseButton.LEFT, 10, 10), true);
        check("once", heard.size(), 1);
        check("as an input, since that is what a button is for",
                heard.getFirst().getCallbackType(), CallbackType.INPUT);
        check("told which element it was", heard.getFirst().getElement() == button, true);
        check("and handed the context", heard.getFirst().getCtx() == ctx, true);
        check("with no key, since the mouse did it", heard.getFirst().getKeycode(), CTMLCallback.NO_KEY);

        check("a press away from it is not heard",
                CTMLEvents.click(laid, ctx, CTMLEvents.MouseButton.LEFT, 200, 100), false);
        check("so nothing else was called", heard.size(), 1);

        CTMLEvents.click(laid, ctx, CTMLEvents.MouseButton.RIGHT, 10, 10);
        check("the right button is a right click", heard.getLast().getCallbackType(), CallbackType.RIGHT_CLICK);
        CTMLEvents.click(laid, ctx, CTMLEvents.MouseButton.MIDDLE, 10, 10);
        check("and the middle one a middle click", heard.getLast().getCallbackType(), CallbackType.MIDDLE_CLICK);
        check("a button nobody knows about does nothing",
                CTMLEvents.click(laid, ctx, 7, 10, 10), false);

        // a div is not an input, so a press on one is a plain click
        DOMTreeElement plainPage = root("w-full h-full");
        DOMTreeElement box = div("w-[40px] h-[20px]");
        box.property("callback", "pressed", null);
        plainPage.append(box);
        DOMContext plain = wired(plainPage, "pressed");
        CTMLEvents.click(lay(plainPage, 854, 480), plain, CTMLEvents.MouseButton.LEFT, 10, 10);
        check("a press on a div is a click", heard.getFirst().getCallbackType(), CallbackType.LEFT_CLICK);

        // a name nothing was registered under is left alone: the press carries on to whatever else wants it
        DOMTreeElement lonePage = root("w-full h-full");
        DOMTreeElement lone = text(tag("button", "w-[40px] h-[20px]"), "press");
        lone.property("callback", "nobodyRegisteredThis", null);
        lonePage.append(lone);
        DOMContext empty = wired(lonePage, "pressed");
        check("a callback nothing was registered for is not swallowed",
                CTMLEvents.click(lay(lonePage, 854, 480), empty, CTMLEvents.MouseButton.LEFT, 10, 10), false);
        check("and a button naming none is not either",
                CTMLEvents.click(laid, null, CTMLEvents.MouseButton.LEFT, 10, 10), false);
    }

    static void keys(){
        section("the keyboard operates it");
        DOMTreeElement page = root("w-full h-full");
        DOMTreeElement button = text(tag("button", "w-[40px] h-[20px]"), "press");
        button.property("callback", "pressed", null);
        page.append(button);
        DOMContext ctx = wired(page, "pressed");

        check("enter on the focused button presses it", CTMLEvents.input(ctx, button, 40), true);
        check("as an input", heard.getFirst().getCallbackType(), CallbackType.INPUT);
        check("told which key did it", heard.getFirst().getKeycode(), 40);

        DOMTreeElement box = div("w-[40px] h-[20px]");
        box.property("callback", "pressed", null);
        page.append(box);
        check("a div is not something the keys operate", CTMLEvents.input(ctx, box, 40), false);
        check("nothing focused, nothing pressed", CTMLEvents.input(ctx, null, 40), false);
        check("and only the one call was made", heard.size(), 1);
    }

    static void bubbling(){
        section("the press goes up the tree");
        DOMTreeElement page = root("w-full h-full");
        DOMTreeElement button = tag("button", "w-[60px] h-[20px]");
        button.property("callback", "pressed", null);
        DOMTreeElement label = text(span(null), "press");
        button.append(label);
        page.append(button);

        DOMContext ctx = wired(page, "pressed");
        CTMLLayout.Box laid = lay(page, 854, 480);
        check("a press on the text inside presses the button",
                CTMLEvents.click(laid, ctx, CTMLEvents.MouseButton.LEFT, 5, 5), true);
        check("and it is the button that hears it", heard.getFirst().getElement() == button, true);

        // the innermost one that names a callback wins, the way a browser stops at the first handler
        DOMTreeElement inner = div("w-[20px] h-[20px]");
        inner.property("callback", "pressed", null);
        DOMTreeElement outer = div("w-[60px] h-[20px]");
        outer.property("callback", "pressed", null);
        outer.append(inner);
        DOMTreeElement nested = root("w-full h-full");
        nested.append(outer);

        DOMContext deep = wired(nested, "pressed");
        CTMLLayout.Box box = lay(nested, 854, 480);
        CTMLEvents.click(box, deep, CTMLEvents.MouseButton.LEFT, 5, 5);
        check("the innermost one takes it", heard.getFirst().getElement() == inner, true);
        CTMLEvents.click(box, deep, CTMLEvents.MouseButton.LEFT, 40, 5);
        check("and the one around it takes the rest", heard.getLast().getElement() == outer, true);
        check("only ever one of them", heard.size(), 2);
    }

    static void hovering(){
        section("the cursor arriving is not a press");
        DOMTreeElement page = root("w-full h-full");
        DOMTreeElement button = text(tag("button", "w-[40px] h-[20px]"), "press");
        button.property("callback", "pressed", null);
        page.append(button);

        DOMContext ctx = wired(page, "pressed");
        CTMLLayout.Box laid = lay(page, 854, 480);

        DOMTreeElement over = CTMLEvents.at(laid, ctx, 10, 10);
        check("the cursor is over the button", over == button, true);
        check("which hears about it", CTMLEvents.hover(ctx, over), true);
        check("as a hover", heard.getFirst().getCallbackType(), CallbackType.HOVER);
        check("with no key", heard.getFirst().getKeycode(), CTMLCallback.NO_KEY);
        check("nothing under the cursor, nothing told", CTMLEvents.hover(ctx, null), false);

        // one name is one function, so that function hears everything and says what it wants: a
        // callback written for a press that acts on all of it fires as the cursor merely arrives
        check("a hover is not a press", heard.getFirst().isPress(), false);
        CTMLEvents.input(ctx, button, 40);
        check("an input is", heard.getLast().isPress(), true);
        CTMLEvents.click(laid, ctx, CTMLEvents.MouseButton.LEFT, 10, 10);
        check("and so is a left click", heard.getLast().isPress(), true);
        CTMLEvents.click(laid, ctx, CTMLEvents.MouseButton.RIGHT, 10, 10);
        check("the other buttons are not", heard.getLast().isPress(), false);
        CTMLEvents.drag(ctx, button);
        check("and neither is a drag", heard.getLast().isPress(), false);
    }

    static void classes(){
        section("the class property says everything");
        DOMTreeElement box = div("w-4 h-4 hidden");
        check("hidden means it is not laid out", box.getStyleValue(ElementStyle.DISPLAY),
                ElementStyle.Display.NONE);

        // reading the class list again replaces the one before it, which is what lets a callback
        // show a box it had hidden: with the classes piling up, "hidden" could never be taken off
        box.property("class", "w-4 h-4", null);
        check("reading it again drops what it said before",
                box.getStyleValue(ElementStyle.DISPLAY), ElementStyle.Display.BLOCK);
        box.property("class", "w-4 h-4 hidden", null);
        check("and puts it back", box.getStyleValue(ElementStyle.DISPLAY), ElementStyle.Display.NONE);

        // a style set by hand is a layer of its own, over the classes and not wiped by them
        DOMTreeElement held = div("w-4 h-4");
        held.style(ElementStyle.OPACITY, 0.5f);
        held.property("class", "w-4 h-4 hidden", null);
        check("a style set by hand outlives the class list", held.getStyleValue(ElementStyle.OPACITY), 0.5f);
        check("and the classes still landed", held.getStyleValue(ElementStyle.DISPLAY),
                ElementStyle.Display.NONE);

        // there is no classList, but the property reads back as the list it was written with, so a
        // callback adds or takes away one class by editing that string rather than knowing the rest
        DOMTreeElement read = div(null);
        read.property("class", "w-4 h-4 bg-red-500", null);
        check("the class list reads back as written", read.getPropertyValue("class"), "w-4 h-4 bg-red-500");
        read.property("class", read.getPropertyValue("class") + " hidden", null);
        check("so one class can be put on the end", read.getStyleValue(ElementStyle.DISPLAY),
                ElementStyle.Display.NONE);
        read.property("class", withoutClass(read, "hidden"), null);
        check("and taken off again", read.getStyleValue(ElementStyle.DISPLAY), ElementStyle.Display.BLOCK);
        check("leaving the rest of the list alone", read.getPropertyValue("class"), "w-4 h-4 bg-red-500");
    }

    /** The element's class list with one class taken off it, the way a host callback would do it. */
    static String withoutClass(DOMTreeElement element, String className){
        Object written = element.getPropertyValue("class");
        if (written == null) return "";
        List<String> kept = new ArrayList<>();
        for (String each : written.toString().strip().split("\\s+")) {
            if (!each.isEmpty() && !each.equals(className)) kept.add(each);
        }
        return String.join(" ", kept);
    }

    /** And the whole thing written out the way it would be in a script. */
    static void parsing() throws Exception {
        section("written as ctml");
        Path file = Path.of(System.getProperty("java.io.tmpdir"), "ctml-button-test.ctm");
        Files.writeString(file, """
                <ctml class="w-full h-full">
                  <button class="w-[60px] h-[20px]" callback="togglePanel" text="hide the panel"></button>
                  <div id="callback-panel" class="w-full h-4" text="the panel"></div>
                </ctml>
                """);

        Interpreter interpreter = new Interpreter(CTMLTusan.tusan());
        interpreter.setup(null, null, null, file, MinecraftEnvironment.CLIENT);
        interpreter.compile();
        DOMTreeElement root = (DOMTreeElement) interpreter.returned;

        check("the parse was clean", interpreter.caughtError, false);
        check("the button came out of the file", root.getChildElements().getFirst().getTag(), "button");

        DOMTreeElement button = root.getChildElements().getFirst();
        check("with the callback it names", button.getCallback(), "togglePanel");

        DOMContext ctx = new DOMContext(root);
        DOMTreeElement panel = ctx.getElementById("callback-panel");
        check("and the panel is found by the id in the file", panel != null, true);

        // the same wiring the demo has: the callback hides and shows the panel by its class property
        ctx.putCallBack("togglePanel", callback -> {
            if (!callback.isPress()) return; // the same guard the demo has

            DOMTreeElement found = callback.getCtx().getElementById("callback-panel");
            boolean showing = found.getStyleValue(ElementStyle.DISPLAY) != ElementStyle.Display.NONE;
            found.property("class", showing
                    ? found.getPropertyValue("class") + " hidden"
                    : withoutClass(found, "hidden"), null);
            callback.getElement().property("text", showing ? "show the panel" : "hide the panel", null);
        });

        CTMLLayout.Box laid = lay(root, 854, 480);
        check("the panel is laid out to start with", laid.children().size(), 2);

        CTMLEvents.hover(ctx, CTMLEvents.at(laid, ctx, 10, 10));
        check("the cursor coming onto the button leaves the panel alone",
                lay(root, 854, 480).children().size(), 2);

        CTMLEvents.click(laid, ctx, CTMLEvents.MouseButton.LEFT, 10, 10);
        check("pressing the button hides it", lay(root, 854, 480).children().size(), 1);
        check("and the button says so now", button.getPropertyValue("text"), "show the panel");

        CTMLEvents.click(lay(root, 854, 480), ctx, CTMLEvents.MouseButton.LEFT, 10, 10);
        check("pressing it again brings it back", lay(root, 854, 480).children().size(), 2);
        check("and the button is back to what it said", button.getPropertyValue("text"), "hide the panel");
    }
}
