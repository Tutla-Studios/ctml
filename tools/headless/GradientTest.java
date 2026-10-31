import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.style.Gradient;
import net.tutla.craftytml.client.ctml.style.TailwindParser;

import java.util.List;

/** Gradients: what the classes build, where the stops land and what colour a point gets. */
public class GradientTest extends Harness {
    public static void main(String[] args){
        shapes();
        positions();
        colors();
        axes();
        clipping();
        gaps();
        report();
    }

    static ElementStyle style(String classes){
        ElementStyle.Builder style = ElementStyle.builder();
        TailwindParser.parseInto(style, classes);
        return style.build();
    }

    static Gradient bg(String classes){
        return style(classes).get(ElementStyle.BACKGROUND_GRADIENT);
    }

    /** The shape classes, in both tailwind's old spelling and its new one. */
    static void shapes(){
        section("shapes");
        check("v3 to the right", bg("bg-gradient-to-r").angle(), 90.0f);
        check("v4 to the right", bg("bg-linear-to-r").angle(), 90.0f);
        check("down", bg("bg-linear-to-b").angle(), 180.0f);
        check("up", bg("bg-linear-to-t").angle(), 0.0f);
        check("to the corner", bg("bg-linear-to-tr").angle(), 45.0f);
        check("an angle of its own", bg("bg-linear-[30deg]").angle(), 30.0f);
        check("still a straight ramp", bg("bg-linear-[30deg]").kind(), Gradient.Kind.LINEAR);
        check("out from the middle", bg("bg-radial").kind(), Gradient.Kind.RADIAL);
        check("round the middle", bg("bg-conic").kind(), Gradient.Kind.CONIC);
        check("and from an angle", bg("bg-conic-[90deg]").angle(), 90.0f);

        // the shape class keeps the stops that were named before it, and the other way round
        Gradient both = bg("from-[#ff0000] bg-radial to-[#0000ff]");
        check("stops survive the shape", both.colors().size(), 2);
        check("and the shape survives them", both.kind(), Gradient.Kind.RADIAL);

        check("the text one is its own", style("text-linear-to-b to-[#0000ff]")
                .get(ElementStyle.TEXT_GRADIENT).angle(), 180.0f);
        check("and leaves the background alone", bg("text-linear-to-b"), null);
    }

    /** Where the stops sit: evenly spread unless they were given a place of their own. */
    static void positions(){
        section("stop positions");
        List<Gradient.Stop> even = bg("bg-linear-to-r from-[#ff0000] to-[#0000ff]").stops();
        check("two stops span the lot", at(even), "0.0,1.0");

        List<Gradient.Stop> three = bg("bg-linear-to-r from-[#ff0000] via-[#00ff00] to-[#0000ff]").stops();
        check("three sit evenly", at(three), "0.0,0.5,1.0");

        check("a percentage moves one", at(bg("bg-linear-to-r from-[#ff0000] from-10% to-[#0000ff]").stops()),
                "0.1,1.0");
        check("in brackets too", at(bg("bg-linear-to-r from-[#ff0000] from-[25%] to-[#0000ff]").stops()),
                "0.25,1.0");
        check("the middle one as well",
                at(bg("bg-linear-to-r from-[#ff0000] via-[#00ff00] via-80% to-[#0000ff]").stops()),
                "0.0,0.8,1.0");

        // one named behind the stop in front of it is pulled forward to meet it, the way css does
        check("never backwards",
                at(bg("bg-linear-to-r from-[#ff0000] from-60% via-[#00ff00] via-20% to-[#0000ff]").stops()),
                "0.6,0.6,1.0");

        check("a position with no colour is no stop", bg("from-10%").stops().size(), 0);
        check("so there is nothing to draw", bg("bg-linear-to-r from-10%").isDrawable(), false);
        check("one colour is not a gradient", bg("bg-linear-to-r from-[#ff0000]").isDrawable(), false);
        check("two is", bg("bg-linear-to-r from-[#ff0000] to-[#0000ff]").isDrawable(), true);
    }

    static String at(List<Gradient.Stop> stops){
        StringBuilder sb = new StringBuilder();
        for (Gradient.Stop stop : stops) sb.append(sb.isEmpty() ? "" : ",").append(stop.at());
        return sb.toString();
    }

    /** The colour somewhere along the ramp, and what happens past either end of it. */
    static void colors(){
        section("colours along the ramp");
        Gradient ramp = bg("bg-linear-to-r from-[#ff0000] to-[#0000ff]");
        check("at the start", hex(ramp.colorAt(0)), "FFFF0000");
        check("at the end", hex(ramp.colorAt(1)), "FF0000FF");
        check("half way", hex(ramp.colorAt(0.5f)), "FF800080");
        check("before the start", hex(ramp.colorAt(-1)), "FFFF0000");
        check("after the end", hex(ramp.colorAt(2)), "FF0000FF");

        // a stop moved along holds its colour flat up to where it sits
        Gradient late = bg("bg-linear-to-r from-[#ff0000] from-50% to-[#0000ff]");
        check("flat up to the stop", hex(late.colorAt(0.25f)), "FFFF0000");
        check("and ramps after it", hex(late.colorAt(0.75f)), "FF800080");

        Gradient fading = bg("bg-linear-to-r from-[#ff0000] to-[#ff0000]/0");
        check("alpha ramps too", hex(fading.colorAt(0.5f)), "80FF0000");
    }

    static String hex(int color){
        return String.format("%08X", color);
    }

    /** Where in a box a point falls along the gradient, which is what every drawn pixel asks. */
    static void axes(){
        section("across a box");
        Gradient right = bg("bg-linear-to-r from-[#ff0000] to-[#0000ff]");
        check("the axis is the width", right.axisLength(100, 50), 100.0f);
        check("the left edge starts it", right.positionOf(0, 25, 100, 50), 0.0f);
        check("the right edge ends it", right.positionOf(100, 25, 100, 50), 1.0f);
        check("the middle is half way", right.positionOf(50, 25, 100, 50), 0.5f);
        check("and the height makes no odds", right.positionOf(50, 0, 100, 50), 0.5f);

        Gradient down = bg("bg-linear-to-b from-[#ff0000] to-[#0000ff]");
        check("down the height", down.axisLength(100, 50), 50.0f);
        check("the top starts it", down.positionOf(50, 0, 100, 50), 0.0f);
        check("the bottom ends it", down.positionOf(50, 50, 100, 50), 1.0f);

        Gradient up = bg("bg-linear-to-t from-[#ff0000] to-[#0000ff]");
        check("up is the other way round", up.positionOf(50, 0, 100, 50), 1.0f);

        // a corner to corner ramp reaches across both, which is why a square one is not the width
        Gradient corner = bg("bg-linear-to-br from-[#ff0000] to-[#0000ff]");
        check("the diagonal is longer", Math.round(corner.axisLength(100, 100)), 141);
        check("one corner starts it", corner.positionOf(0, 0, 100, 100), 0.0f);
        check("the other ends it", corner.positionOf(100, 100, 100, 100), 1.0f);
        check("and the corners between are half way", corner.positionOf(100, 0, 100, 100), 0.5f);

        Gradient out = bg("bg-radial from-[#ff0000] to-[#0000ff]");
        check("radial starts in the middle", out.positionOf(50, 50, 100, 100), 0.0f);
        check("and ends at the corner", out.positionOf(100, 100, 100, 100), 1.0f);
        check("the edge is part way", Math.round(out.positionOf(100, 50, 100, 100) * 100), 71);

        Gradient round = bg("bg-conic from-[#ff0000] to-[#0000ff]");
        check("conic starts straight up", round.positionOf(50, 0, 100, 100), 0.0f);
        check("a quarter turn to the right", round.positionOf(100, 50, 100, 100), 0.25f);
        check("half way round is down", round.positionOf(50, 100, 100, 100), 0.5f);
        check("and three quarters to the left", round.positionOf(0, 50, 100, 100), 0.75f);

        Gradient turned = bg("bg-conic-[90deg] from-[#ff0000] to-[#0000ff]");
        check("a turned one starts to the right", turned.positionOf(100, 50, 100, 100), 0.0f);
    }

    /** background-clip, which is also how a gradient gets clipped to the text. */
    static void clipping(){
        section("background-clip");
        check("to the text", style("bg-clip-text").get(ElementStyle.BACKGROUND_CLIP),
                ElementStyle.BackgroundClip.TEXT);
        check("to the padding", style("bg-clip-padding").get(ElementStyle.BACKGROUND_CLIP),
                ElementStyle.BackgroundClip.PADDING_BOX);
        check("to the content", style("bg-clip-content").get(ElementStyle.BACKGROUND_CLIP),
                ElementStyle.BackgroundClip.CONTENT_BOX);
        check("the border box by default", style("bg-[#ff0000]").get(ElementStyle.BACKGROUND_CLIP), null);
        check("and the colour is still there", hex(style("bg-clip-text bg-[#ff0000]")
                .get(ElementStyle.BACKGROUND_COLOR)), "FFFF0000");
    }

    /** What is still not understood, so the gap is written down rather than guessed at. */
    static void gaps(){
        section("what is still not read");

        // an interpolation space is not a thing here: the ramp is mixed in plain srgb either way
        Gradient space = bg("bg-linear-to-r/oklch from-[#ff0000] to-[#0000ff]");
        check("the class leaves no angle of its own", space.angle(), 90.0f);
        check("and the ramp is still srgb", hex(space.colorAt(0.5f)), "FF800080");

        // a colour nothing knows is left out, so the gradient comes up short rather than half built
        Gradient unknown = bg("bg-linear-to-r from-cornflowerblue to-[#0000ff]");
        check("an unknown colour is no stop", unknown.colors().size(), 1);
        check("so there is nothing to draw", unknown.isDrawable(), false);
    }
}
