import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.element.markdown.Img;
import net.tutla.craftytml.client.ctml.style.BackgroundImage;
import net.tutla.craftytml.client.ctml.style.Length;
import net.tutla.craftytml.client.ctml.style.TailwindParser;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.CTMLLayout;
import net.tutla.craftytml.client.render.CTMLTextures;
import net.tutla.craftytml.client.tusan.register.PropertyRegistry;

import java.util.Map;

/**
 * The img element: the size it comes out, what a width or a height does to that, and that it and a
 * background image are looked up the same way.
 */
public class ImgTest extends Harness {
    /** Stands in for the packs, so a size can be had without a game to read one from. */
    private static final Map<String, int[]> HELD = Map.of(
            "mod:wide.png", new int[]{ 64, 16 },
            "mod:square.png", new int[]{ 32, 32 },
            "mod:tall.png", new int[]{ 10, 40 });

    public static void main(String[] args){
        PropertyRegistry.init();
        CTMLTextures.sizedBy(id -> HELD.get(id.toString()));

        looking();
        sizing();
        inFlow();
        fitting();
        report();
    }

    static DOMTreeElement img(String source, String classes){
        DOMTreeElement element = tag("img", classes);
        if (source != null) element.property("src", source, null);
        return element;
    }

    static void looking(){
        section("one place a name turns into a texture");
        CTMLTextures.Texture wide = CTMLTextures.of("mod:wide.png");
        check("a name that is held comes back measured", wide.width() + "x" + wide.height(), "64x16");
        check("and knows it is", wide.isMeasured(), true);

        CTMLTextures.Texture missing = CTMLTextures.of("mod:nothing.png");
        check("one nothing holds still comes back", missing != null, true);
        check("so it can be drawn as the missing texture", missing.isMeasured(), false);
        check("but with an identifier to draw", missing.id() != null, true);
        check("a name no identifier can be made of comes back as nothing",
                CTMLTextures.of("not a name at all"), null);
        check("and so does none at all", CTMLTextures.of(null), null);

        // the background and the element are the same lookup, so they agree about every one of those
        check("the element reads the same name", CTMLTextures.of("mod:wide.png").id().toString(),
                CTMLTextures.of(TailwindParser.parse("bg-[url(mod:wide.png)]")
                        .get(ElementStyle.BACKGROUND_IMAGE).source()).id().toString());
    }

    static void sizing(){
        section("the size it comes out");
        check("its own, with nothing said", rect(only(img("mod:wide.png", null))), "0,0 64x16");
        check("a width of its own keeps its shape",
                rect(only(img("mod:wide.png", "w-[32px]"))), "0,0 32x8");
        check("and so does a height",
                rect(only(img("mod:wide.png", "h-[32px]"))), "0,0 128x32");
        check("both of them is both of them",
                rect(only(img("mod:wide.png", "w-[20px] h-[20px]"))), "0,0 20x20");
        check("a tall one comes out tall", rect(only(img("mod:tall.png", null))), "0,0 10x40");
        check("a max width brings it in and keeps the shape",
                rect(only(img("mod:square.png", "max-w-[16px]"))), "0,0 16x16");

        CTMLLayout.Box unheld = only(img("mod:nothing.png", null));
        check("an image nothing holds has no size to be",
                unheld.width() + "x" + unheld.height(), "0x0");
        check("but a size of its own is still a size",
                rect(only(img("mod:nothing.png", "w-[20px] h-[10px]"))), "0,0 20x10");
        check("and so is no src at all", rect(only(img(null, "w-[20px] h-[10px]"))), "0,0 20x10");

        // the border box is what a size names, since that is what every other element here does
        check("padding comes out of the picture, not off the box",
                rect(only(img("mod:square.png", "w-[20px] h-[20px] p-[2px]"))), "0,0 20x20");
        check("so the picture is drawn in what is left",
                only(img("mod:square.png", "w-[20px] h-[20px] p-[2px]")).contentWidth(), 16);
    }

    static void inFlow(){
        section("in among everything else");
        // inline-block, so it sits in a line of text rather than breaking it
        DOMTreeElement line = text(div("w-[200px]"), "before");
        line.append(img("mod:wide.png", null));
        CTMLLayout.Box laid = only(line);
        check("it goes on the line with the text", laid.children().getFirst().y() < 16, true);
        check("as wide as the picture", laid.children().getFirst().width(), 64);

        DOMTreeElement column = div("w-[100px] flex flex-col");
        column.append(img("mod:wide.png", "w-full"));
        column.append(div("w-full h-[10px]"));
        CTMLLayout.Box stacked = only(column);
        check("a full width picture fills the box", stacked.children().getFirst().width(), 100);
        check("and keeps its shape doing it", stacked.children().getFirst().height(), 25);
        check("so what comes after starts under it", stacked.children().get(1).y(), 25);

        DOMTreeElement held = div("w-[100px] h-[40px] overflow-y-auto");
        held.append(img("mod:tall.png", "w-full"));
        CTMLLayout.Box scroller = only(held);
        check("a picture taller than its box is something to scroll",
                scroller.port().maxScrollY() > 0, true);
        check("and the bar takes its room off the width", scroller.children().getFirst().width(), 96);
    }

    static void fitting(){
        section("object-fit, which says the same things background-size does");
        ElementStyle contain = TailwindParser.parse("object-contain object-top");
        check("object-contain", contain.get(ElementStyle.OBJECT_FIT), ElementStyle.ObjectFit.CONTAIN);
        check("object-top across", contain.get(ElementStyle.OBJECT_POSITION_X), Length.percent(50));
        check("object-top down", contain.get(ElementStyle.OBJECT_POSITION_Y), Length.percent(0));
        check("object-cover", TailwindParser.parse("object-cover").get(ElementStyle.OBJECT_FIT),
                ElementStyle.ObjectFit.COVER);
        check("object-scale-down", TailwindParser.parse("object-scale-down").get(ElementStyle.OBJECT_FIT),
                ElementStyle.ObjectFit.SCALE_DOWN);
        check("object-position spelled out",
                TailwindParser.parse("object-position-[4px_6px]").get(ElementStyle.OBJECT_POSITION_Y),
                Length.px(6));
        check("and one that is not a position at all",
                TailwindParser.parseInto(ElementStyle.builder(), "object-position-[sideways]").size(), 1);

        // the fit is worked out by the same geometry a background uses, so it comes out the same sizes
        BackgroundImage image = BackgroundImage.of("mod:wide.png");
        check("contain fits the whole picture in",
                java.util.Arrays.toString(image.withFit(BackgroundImage.Fit.CONTAIN)
                        .size(32, 32, 64, 16, 854, 480)), "[32, 8]");
        check("cover fills the box with it",
                java.util.Arrays.toString(image.withFit(BackgroundImage.Fit.COVER)
                        .size(32, 32, 64, 16, 854, 480)), "[128, 32]");
        check("fill stretches it to the box",
                java.util.Arrays.toString(image.withSize(Length.FULL, Length.FULL)
                        .size(32, 32, 64, 16, 854, 480)), "[32, 32]");
        check("none leaves it alone",
                java.util.Arrays.toString(image.withFit(BackgroundImage.Fit.AUTO)
                        .size(32, 32, 64, 16, 854, 480)), "[64, 16]");
    }
}
