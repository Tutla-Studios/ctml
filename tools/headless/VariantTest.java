import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.style.Media;
import net.tutla.craftytml.client.ctml.style.TailwindParser;
import net.tutla.craftytml.client.ctml.style.Variant;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.CTMLFocus;
import net.tutla.craftytml.client.render.CTMLLayout;
import net.tutla.craftytml.client.render.CTMLStates;

/** The conditions in front of a class: hover:, focus:, active:, dark:, the breakpoints and the places. */
public class VariantTest extends Harness {
    public static void main(String[] args){
        read();
        hover();
        layers();
        keyboard();
        pressing();
        breakpoints();
        dark();
        places();
        laidOut();
        report();
    }

    /** What the parser makes of a class list with variants in it. */
    static void read(){
        section("read apart");
        TailwindParser.Sheet sheet = TailwindParser.read("p-4 hover:p-8 hover:bg-sky-500 md:hover:p-2");

        check("the plain classes are the base", sheet.base().get(ElementStyle.PADDING_TOP).toString(), "16px");
        check("nothing behind a variant is in it", sheet.base().isSet(ElementStyle.BACKGROUND_COLOR), false);
        check("one layer per set of variants", sheet.layers().size(), 2);
        check("the classes sharing a set share a layer",
                sheet.layers().getFirst().style().get(ElementStyle.PADDING_TOP).toString(), "32px");
        check("and they are all in it",
                sheet.layers().getFirst().style().isSet(ElementStyle.BACKGROUND_COLOR), true);
        check("the stacked one keeps both", sheet.layers().get(1).variants().size(), 2);
        check("a colon inside brackets is left alone",
                TailwindParser.read("bg-[url(mod:a.png)]").problems().size(), 0);
        check("and the image still lands",
                TailwindParser.read("bg-[url(mod:a.png)]").base().isSet(ElementStyle.BACKGROUND_IMAGE), true);
    }

    /** The cursor coming onto a box and going away again. */
    static void hover(){
        section("hover");
        DOMTreeElement box = div("w-[40px] h-[20px] bg-sky-500 hover:bg-red-500");
        int plain = box.getStyle().get(ElementStyle.BACKGROUND_COLOR);

        check("nothing is hovered to start with", box.getElement().isHovered(), false);
        check("so the plain colour is the one", box.getStyle().get(ElementStyle.BACKGROUND_COLOR), plain);

        check("hovering it matters", box.getElement().setHovered(true), true);
        check("and swaps the colour", box.getStyle().get(ElementStyle.BACKGROUND_COLOR) != plain, true);
        box.getElement().setHovered(false);
        check("leaving puts it back", box.getStyle().get(ElementStyle.BACKGROUND_COLOR), plain);
    }

    /** A class set by hand still beats a variant, and the last layer written beats the ones before it. */
    static void layers(){
        section("the layers in order");
        DOMTreeElement box = div("w-[40px] hover:w-[80px] hover:w-[60px]");
        box.getElement().setHovered(true);
        check("the later layer has the say", box.getStyle().get(ElementStyle.WIDTH).toString(), "60px");

        DOMTreeElement held = div("w-[40px] hover:w-[80px]");
        held.style(ElementStyle.WIDTH, net.tutla.craftytml.client.ctml.style.Length.px(10));
        held.getElement().setHovered(true);
        check("what a script set beats both", held.getStyle().get(ElementStyle.WIDTH).toString(), "10px");
    }

    /** focus: is the element with the keyboard, focus-within: everything above it. */
    static void keyboard(){
        section("the keyboard");
        DOMTreeElement outer = div("focus-within:bg-red-500 focus:bg-sky-500");
        DOMTreeElement inner = div("focus:bg-red-500");
        outer.append(inner);

        CTMLStates states = new CTMLStates();
        states.keyboard(inner);

        check("the inner one has it", inner.getElement().isFocused(), true);
        check("the outer one does not", outer.getElement().isFocused(), false);
        check("but it is within it", outer.getElement().isFocusWithin(), true);
        check("so the outer one takes its within colour",
                outer.getStyle().isSet(ElementStyle.BACKGROUND_COLOR), true);

        states.keyboard(null);
        check("and letting go clears both", inner.getElement().isFocused() || outer.getElement().isFocusWithin(), false);
    }

    /** active: reaches the whole chain the press landed in, the way css has it. */
    static void pressing(){
        section("a press");
        DOMTreeElement outer = div("w-[80px] h-[40px] active:bg-red-500");
        DOMTreeElement inner = div("w-[40px] h-[20px] active:bg-sky-500");
        outer.append(inner);
        CTMLLayout.Box root = only(outer);

        CTMLStates states = new CTMLStates();
        states.press(CTMLFocus.path(root, root.x() + 2, root.y() + 2));

        check("the box pressed is active", inner.getElement().isActive(), true);
        check("and so is the one around it", outer.getElement().isActive(), true);

        states.release();
        check("letting go ends it", inner.getElement().isActive(), false);
        check("for both of them", outer.getElement().isActive(), false);
    }

    /** The breakpoints, which read the viewport whatever is laying out last measured. */
    static void breakpoints(){
        section("breakpoints");
        DOMTreeElement box = div("hidden md:block");

        Media.measured(320, 240);
        check("under the breakpoint it stays as written",
                box.getStyle().get(ElementStyle.DISPLAY), ElementStyle.Display.NONE);

        Media.measured(640, 360);
        check("over it the variant lands",
                box.getStyle().get(ElementStyle.DISPLAY), ElementStyle.Display.BLOCK);

        DOMTreeElement capped = div("block max-md:hidden");
        Media.measured(320, 240);
        check("a max- variant is the other way round",
                capped.getStyle().get(ElementStyle.DISPLAY), ElementStyle.Display.NONE);
        Media.measured(640, 360);
        check("and lets go once it is wide enough",
                capped.getStyle().get(ElementStyle.DISPLAY), ElementStyle.Display.BLOCK);
    }

    /** dark:, which is the nearest colour scheme up the tree, or the host's where nothing says. */
    static void dark(){
        section("dark");
        DOMTreeElement page = div("scheme-dark");
        DOMTreeElement box = div("bg-sky-500 dark:bg-red-500");
        page.append(box);

        int dark = box.getStyle().get(ElementStyle.BACKGROUND_COLOR);
        check("the scheme above it counts", box.getElement().isDark(), true);

        page.getElement().applyClassStyles("scheme-light");
        check("and so does changing it", box.getElement().isDark(), false);
        check("which puts the colour back", box.getStyle().get(ElementStyle.BACKGROUND_COLOR) != dark, true);

        DOMTreeElement loose = div("bg-sky-500 dark:bg-red-500");
        check("with nothing saying, the host does", loose.getElement().isDark(), false);
        Media.dark(true);
        check("so turning it on reaches it", loose.getElement().isDark(), true);
        Media.dark(false);
    }

    /** first:, last:, only:, odd: and even:, which count the elements beside one. */
    static void places(){
        section("where it comes in the list");
        DOMTreeElement list = div(null);
        DOMTreeElement[] items = new DOMTreeElement[3];
        for (int i = 0; i < items.length; i++) {
            items[i] = div("first:bg-red-500 last:bg-sky-500 odd:underline even:italic");
            list.append(items[i]);
        }

        check("the first is first", items[0].getElement().siblingIndex(), 0);
        check("and css counts it odd", items[0].getStyle().get(ElementStyle.UNDERLINE), true);
        check("the second is even", items[1].getStyle().get(ElementStyle.ITALIC), true);
        check("and not underlined", items[1].getStyle().isSet(ElementStyle.UNDERLINE), false);
        check("the last one takes its own colour", items[2].getStyle().get(ElementStyle.BACKGROUND_COLOR)
                != (int) items[0].getStyle().get(ElementStyle.BACKGROUND_COLOR), true);
        check("three of them, so none is the only one",
                Variant.ONLY.holds(items[0].getElement()), false);

        DOMTreeElement alone = div("only:bg-red-500");
        div(null).append(alone);
        check("one on its own is", Variant.ONLY.holds(alone.getElement()), true);
        check("and nothing with no parent at all is", Variant.FIRST.holds(div("first:flex").getElement()), false);
    }

    /** The whole way through: a hovered box is laid out at the size its hover class gives it. */
    static void laidOut(){
        section("laid out");
        DOMTreeElement box = div("w-[40px] h-[20px] hover:w-[80px]");
        check("as written", only(box).width(), 40);

        box.getElement().setHovered(true);
        check("and wider while the cursor is on it", only(box).width(), 80);

        box.getElement().setHovered(false);
        check("back again after", only(box).width(), 40);
    }
}
