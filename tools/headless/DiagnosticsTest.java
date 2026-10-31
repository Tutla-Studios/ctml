import net.tutla.craftytml.client.ctml.CTMLDiagnostics;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.style.TailwindParser;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.CTMLDrag;
import net.tutla.craftytml.client.tusan.CTMLTusan;
import net.tutla.tums.tusan.MinecraftEnvironment;
import net.tutla.tums.tusan.interpreter.Interpreter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** What a script gets told when it is wrong, and what is held on to when the mouse goes quiet. */
public class DiagnosticsTest extends Harness {
    public static void main(String[] args) throws Exception {
        CTMLDiagnostics.printing(false); // the notes are read back here rather than watched go by
        unknown();
        badValues();
        stillApplies();
        reported();
        parsing();
        lostRelease();
        report();
    }

    static List<TailwindParser.Problem> problems(String classes){
        ElementStyle.Builder style = ElementStyle.builder();
        return TailwindParser.parseInto(style, classes);
    }

    static String first(String classes){
        List<TailwindParser.Problem> problems = problems(classes);
        return problems.isEmpty() ? "none" : problems.getFirst().toString();
    }

    /** A class nothing knows at all: a typo, or a variant there is nothing here to test. */
    static void unknown(){
        section("classes nothing knows");
        check("a variant nothing can answer", first("print:bg-red-500"), "print:bg-red-500");
        check("one that would need a sibling watched", first("group-hover:flex"), "group-hover:flex");
        check("a variant with no class behind it", first("hover:"), "hover:");
        check("a typo", first("overscroll-sideways"), "overscroll-sideways");
        check("and the reason", problems("print:flex").getFirst().reason(),
                TailwindParser.Problem.Reason.UNKNOWN);

        check("a class that is known is no problem", problems("flex").size(), 0);
        check("nor is one with a value", problems("p-4 w-[12px] gap-2 bg-sky-500/50").size(), 0);
        check("nor is one behind a variant", problems("hover:bg-red-500 md:flex 2xl:hover:p-4").size(), 0);
        check("a bad value behind a variant still says so", first("hover:p-wide"),
                "hover:p-wide (no value padding-top can take)");
    }

    /**
     * The one that used to go by in silence: a class whose prefix is known but whose value is not
     * something that style can take.
     */
    static void badValues(){
        section("values a style cannot take");
        check("a colour nothing knows", first("bg-cornflowerblue"),
                "bg-cornflowerblue (no value background-color can take)");
        check("a length that is not one", first("p-wide"), "p-wide (no value padding-top can take)");
        check("auto on a gap", first("gap-auto"), "gap-auto (no value row-gap can take)");
        check("a percent on a radius", first("rounded-50%"), "rounded-50% (no value border-radius can take)");
        check("a number that is not one", first("z-high"), "z-high (no value z-index can take)");
        check("an opacity that is not one", first("opacity-loud"), "opacity-loud (no value opacity can take)");
        check("an angle that is not one", first("bg-linear-[sideways]"),
                "bg-linear-[sideways] (no value background-gradient can take)");
        check("a track list that is not one", first("grid-cols-[weird]"),
                "grid-cols-[weird] (no value grid-template-columns can take)");
        check("a stop nothing knows", first("from-chartreuse"),
                "from-chartreuse (no value background-gradient can take)");
        check("and the reason", problems("p-wide").getFirst().reason(),
                TailwindParser.Problem.Reason.BAD_VALUE);

        // the good values of the same classes stay good
        check("a gap of four is fine", problems("gap-4 gap-x-2 gap-y-px").size(), 0);
        check("so are these", problems("rounded-lg z-10 opacity-50 bg-linear-[30deg] grid-cols-[1fr_2fr]").size(), 0);
    }

    /** The point of not stopping: one bad class costs that class and nothing else. */
    static void stillApplies(){
        section("the rest of the list still lands");
        DOMTreeElement box = div("w-[40px] print:bg-red-500 h-[20px] p-wide bg-sky-500");
        ElementStyle style = box.getStyle();
        check("the width before it", style.get(ElementStyle.WIDTH).toString(), "40px");
        check("the height after it", style.get(ElementStyle.HEIGHT).toString(), "20px");
        check("and the colour after the bad value", style.get(ElementStyle.BACKGROUND_COLOR) != null, true);
        check("the bad padding is simply not set", style.isSet(ElementStyle.PADDING_TOP), false);
        check("and it still lays out", only(box).width(), 40);
    }

    /** Every problem is said once, kept, and tagged by which kind it was. */
    static void reported(){
        section("what gets said");
        CTMLDiagnostics.clear();
        div("flex print:underline p-wide gap-4");

        List<CTMLDiagnostics.Note> notes = CTMLDiagnostics.notes();
        check("one note each", notes.size(), 2);
        check("the unknown one is tagged", CTMLDiagnostics.notes("UnknownClass").size(), 1);
        check("the unusable one too", CTMLDiagnostics.notes("UnusableValue").size(), 1);
        check("and names the element and the class",
                notes.getFirst().message(), "<div> has the class \"print:underline\", which nothing knows how to style");
        check("the other names the style", notes.get(1).message(),
                "<div> has the class \"p-wide\", whose value is not one padding-top can take");
        check("nothing points anywhere without a script", notes.getFirst().where(), null);

        CTMLDiagnostics.clear();
        check("and they can be cleared", CTMLDiagnostics.notes().size(), 0);
    }

    /** From a file, where a note can point at the line it came from, and the parse carries on. */
    static void parsing() throws Exception {
        section("from a script");
        Path file = Path.of(System.getProperty("java.io.tmpdir"), "ctml-diagnostics-test.ctm");
        Files.writeString(file, """
                <ctml class="w-full h-full">
                  <div class="w-[30px] h-[10px]"></div>
                  <div class="w-[30px] print:bg-red-500 h-[10px]"></div>
                  <div class="w-[30px] h-[10px]"></div>
                </ctml>
                """);

        CTMLDiagnostics.clear();
        Interpreter interpreter = new Interpreter(CTMLTusan.tusan());
        interpreter.setup(null, null, null, file, MinecraftEnvironment.CLIENT);
        interpreter.compile();
        DOMTreeElement root = (DOMTreeElement) interpreter.returned;

        check("the script was not given up on", interpreter.caughtError, false);
        check("every element is still there", root.getChildElements().size(), 3);
        check("the one after the bad class too", root.getChildElements().get(2).getStyle()
                .get(ElementStyle.WIDTH).toString(), "30px");

        List<CTMLDiagnostics.Note> notes = CTMLDiagnostics.notes("UnknownClass");
        check("and it was still reported", notes.size(), 1);
        check("pointing at the file", notes.getFirst().where() != null, true);
        check("under the class itself", notes.getFirst().where().contains("^"), true);
    }

    /** A press whose release never arrives, which used to leave the box following the cursor for good. */
    static void lostRelease(){
        section("a release that never comes");
        CTMLDrag.Watch watch = new CTMLDrag.Watch();
        watch.saw(100, 100);

        check("still held where the drag left it", watch.lost(100, 100), false);
        check("and a pixel or two out is the same frame", watch.lost(101, 102), false);

        // the cursor moves and no drag is reported, so the button is up: let go, once it is sure
        boolean let = false;
        for (int frame = 0; frame < CTMLDrag.Watch.PATIENCE; frame++) let = watch.lost(140, 100);
        check("let go after a few frames away", let, true);

        // a late frame on its own is forgiven, since a real drag reports every movement it makes
        CTMLDrag.Watch patient = new CTMLDrag.Watch();
        patient.saw(100, 100);
        check("one frame out is not enough", patient.lost(140, 100), false);
        patient.saw(140, 100); // the drag catches up, which is what a real one does
        check("so it holds on", patient.lost(140, 100), false);
        check("and keeps holding", patient.lost(141, 101), false);
    }
}
