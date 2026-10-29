package net.tutla.craftytml.client.ctml.style;

import net.tutla.craftytml.client.ctml.ElementStyle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Turns a tailwind class list into an ElementStyle.
 * Classes that are just a word ("flex", "italic") sit in KEYWORDS, the ones carrying a value
 * ("p-4", "bg-red-500", "w-[120px]") sit in VALUED under their prefix, so adding a utility is one line.
 * Anything unrecognised is handed back by parseInto and otherwise ignored.
 */
public final class TailwindParser {
    /** A tailwind spacing unit is 0.25rem; one unit to four gui pixels keeps the scale usable in game. */
    public static final int SPACING_UNIT = 4;

    private interface Valued { void apply(ElementStyle.Builder style, String value); }

    private static final Map<String, Consumer<ElementStyle.Builder>> KEYWORDS = new HashMap<>();
    private static final Map<String, Valued> VALUED = new LinkedHashMap<>();
    private static final List<String> PREFIXES;

    private static final Map<String, Integer> FONT_SIZES = new HashMap<>();
    private static final Map<String, Integer> RADII = new HashMap<>();
    private static final Map<String, Integer> TRACKING = new HashMap<>();

    static {
        // display
        keyword("block", s -> s.set(ElementStyle.DISPLAY, ElementStyle.Display.BLOCK));
        keyword("inline", s -> s.set(ElementStyle.DISPLAY, ElementStyle.Display.INLINE));
        keyword("inline-block", s -> s.set(ElementStyle.DISPLAY, ElementStyle.Display.INLINE_BLOCK));
        keyword("flex", s -> s.set(ElementStyle.DISPLAY, ElementStyle.Display.FLEX));
        keyword("hidden", s -> s.set(ElementStyle.DISPLAY, ElementStyle.Display.NONE));

        // position
        keyword("static", s -> s.set(ElementStyle.POSITION, ElementStyle.Position.STATIC));
        keyword("relative", s -> s.set(ElementStyle.POSITION, ElementStyle.Position.RELATIVE));
        keyword("absolute", s -> s.set(ElementStyle.POSITION, ElementStyle.Position.ABSOLUTE));
        keyword("fixed", s -> s.set(ElementStyle.POSITION, ElementStyle.Position.FIXED));

        // box sizing
        keyword("box-content", s -> s.set(ElementStyle.BOX_SIZING, ElementStyle.BoxSizing.CONTENT_BOX));
        keyword("box-border", s -> s.set(ElementStyle.BOX_SIZING, ElementStyle.BoxSizing.BORDER_BOX));

        // flex
        keyword("flex-row", s -> s.set(ElementStyle.FLEX_DIRECTION, ElementStyle.FlexDirection.ROW));
        keyword("flex-row-reverse", s -> s.set(ElementStyle.FLEX_DIRECTION, ElementStyle.FlexDirection.ROW_REVERSE));
        keyword("flex-col", s -> s.set(ElementStyle.FLEX_DIRECTION, ElementStyle.FlexDirection.COLUMN));
        keyword("flex-col-reverse", s -> s.set(ElementStyle.FLEX_DIRECTION, ElementStyle.FlexDirection.COLUMN_REVERSE));
        keyword("flex-wrap", s -> s.set(ElementStyle.FLEX_WRAP, ElementStyle.FlexWrap.WRAP));
        keyword("flex-wrap-reverse", s -> s.set(ElementStyle.FLEX_WRAP, ElementStyle.FlexWrap.WRAP_REVERSE));
        keyword("flex-nowrap", s -> s.set(ElementStyle.FLEX_WRAP, ElementStyle.FlexWrap.NOWRAP));
        keyword("flex-1", s -> s.set(ElementStyle.FLEX_GROW, 1).set(ElementStyle.FLEX_SHRINK, 1));
        keyword("flex-auto", s -> s.set(ElementStyle.FLEX_GROW, 1).set(ElementStyle.FLEX_SHRINK, 1));
        keyword("flex-none", s -> s.set(ElementStyle.FLEX_GROW, 0).set(ElementStyle.FLEX_SHRINK, 0));
        keyword("grow", s -> s.set(ElementStyle.FLEX_GROW, 1));
        keyword("shrink", s -> s.set(ElementStyle.FLEX_SHRINK, 1));

        keyword("justify-start", s -> s.set(ElementStyle.JUSTIFY_CONTENT, ElementStyle.JustifyContent.START));
        keyword("justify-center", s -> s.set(ElementStyle.JUSTIFY_CONTENT, ElementStyle.JustifyContent.CENTER));
        keyword("justify-end", s -> s.set(ElementStyle.JUSTIFY_CONTENT, ElementStyle.JustifyContent.END));
        keyword("justify-between", s -> s.set(ElementStyle.JUSTIFY_CONTENT, ElementStyle.JustifyContent.SPACE_BETWEEN));
        keyword("justify-around", s -> s.set(ElementStyle.JUSTIFY_CONTENT, ElementStyle.JustifyContent.SPACE_AROUND));
        keyword("justify-evenly", s -> s.set(ElementStyle.JUSTIFY_CONTENT, ElementStyle.JustifyContent.SPACE_EVENLY));

        keyword("items-start", s -> s.set(ElementStyle.ALIGN_ITEMS, ElementStyle.AlignItems.START));
        keyword("items-center", s -> s.set(ElementStyle.ALIGN_ITEMS, ElementStyle.AlignItems.CENTER));
        keyword("items-end", s -> s.set(ElementStyle.ALIGN_ITEMS, ElementStyle.AlignItems.END));
        keyword("items-stretch", s -> s.set(ElementStyle.ALIGN_ITEMS, ElementStyle.AlignItems.STRETCH));
        keyword("items-baseline", s -> s.set(ElementStyle.ALIGN_ITEMS, ElementStyle.AlignItems.BASELINE));

        // text
        keyword("text-left", s -> s.set(ElementStyle.TEXT_ALIGN, ElementStyle.TextAlign.LEFT));
        keyword("text-center", s -> s.set(ElementStyle.TEXT_ALIGN, ElementStyle.TextAlign.CENTER));
        keyword("text-right", s -> s.set(ElementStyle.TEXT_ALIGN, ElementStyle.TextAlign.RIGHT));
        keyword("font-bold", s -> s.set(ElementStyle.BOLD, true));
        keyword("font-normal", s -> s.set(ElementStyle.BOLD, false));
        keyword("italic", s -> s.set(ElementStyle.ITALIC, true));
        keyword("not-italic", s -> s.set(ElementStyle.ITALIC, false));
        keyword("underline", s -> s.set(ElementStyle.UNDERLINE, true));
        keyword("line-through", s -> s.set(ElementStyle.STRIKETHROUGH, true));
        keyword("no-underline", s -> s.set(ElementStyle.UNDERLINE, false).set(ElementStyle.STRIKETHROUGH, false));
        keyword("text-shadow", s -> s.set(ElementStyle.TEXT_SHADOW, true)); // ctml extra, minecraft draws text with a shadow by default
        keyword("text-shadow-none", s -> s.set(ElementStyle.TEXT_SHADOW, false));
        keyword("whitespace-normal", s -> s.set(ElementStyle.WHITE_SPACE, ElementStyle.WhiteSpace.NORMAL));
        keyword("whitespace-nowrap", s -> s.set(ElementStyle.WHITE_SPACE, ElementStyle.WhiteSpace.NOWRAP));
        keyword("whitespace-pre", s -> s.set(ElementStyle.WHITE_SPACE, ElementStyle.WhiteSpace.PRE));

        // misc
        keyword("overflow-visible", s -> s.set(ElementStyle.OVERFLOW, ElementStyle.Overflow.VISIBLE));
        keyword("overflow-hidden", s -> s.set(ElementStyle.OVERFLOW, ElementStyle.Overflow.HIDDEN));
        keyword("overflow-scroll", s -> s.set(ElementStyle.OVERFLOW, ElementStyle.Overflow.SCROLL));
        keyword("visible", s -> s.set(ElementStyle.VISIBILITY, ElementStyle.Visibility.VISIBLE));
        keyword("invisible", s -> s.set(ElementStyle.VISIBILITY, ElementStyle.Visibility.HIDDEN));
        keyword("rounded", s -> s.set(ElementStyle.BORDER_RADIUS, 4));
        keyword("border", s -> s.set(ElementStyle.BORDER_WIDTH, 1));

        // spacing
        valued("p", spacingAll(ElementStyle.PADDING));
        valued("px", spacing(ElementStyle.PADDING_LEFT, ElementStyle.PADDING_RIGHT));
        valued("py", spacing(ElementStyle.PADDING_TOP, ElementStyle.PADDING_BOTTOM));
        valued("pt", spacing(ElementStyle.PADDING_TOP));
        valued("pr", spacing(ElementStyle.PADDING_RIGHT));
        valued("pb", spacing(ElementStyle.PADDING_BOTTOM));
        valued("pl", spacing(ElementStyle.PADDING_LEFT));
        valued("m", spacingAll(ElementStyle.MARGIN));
        valued("mx", spacing(ElementStyle.MARGIN_LEFT, ElementStyle.MARGIN_RIGHT));
        valued("my", spacing(ElementStyle.MARGIN_TOP, ElementStyle.MARGIN_BOTTOM));
        valued("mt", spacing(ElementStyle.MARGIN_TOP));
        valued("mr", spacing(ElementStyle.MARGIN_RIGHT));
        valued("mb", spacing(ElementStyle.MARGIN_BOTTOM));
        valued("ml", spacing(ElementStyle.MARGIN_LEFT));
        valued("gap", spacing(ElementStyle.GAP));
        valued("gap-x", spacing(ElementStyle.GAP));
        valued("gap-y", spacing(ElementStyle.GAP));

        // size
        valued("w", horizontal(spacing(ElementStyle.WIDTH)));
        valued("h", spacing(ElementStyle.HEIGHT));
        valued("min-w", horizontal(spacing(ElementStyle.MIN_WIDTH)));
        valued("min-h", spacing(ElementStyle.MIN_HEIGHT));
        valued("max-w", horizontal(spacing(ElementStyle.MAX_WIDTH)));
        valued("max-h", spacing(ElementStyle.MAX_HEIGHT));
        valued("size", spacing(ElementStyle.WIDTH, ElementStyle.HEIGHT));

        // inset
        valued("inset", spacingAll(ElementStyle.INSET));
        valued("inset-x", spacing(ElementStyle.LEFT, ElementStyle.RIGHT));
        valued("inset-y", spacing(ElementStyle.TOP, ElementStyle.BOTTOM));
        valued("top", spacing(ElementStyle.TOP));
        valued("right", spacing(ElementStyle.RIGHT));
        valued("bottom", spacing(ElementStyle.BOTTOM));
        valued("left", spacing(ElementStyle.LEFT));

        // the ones that need more than a straight spacing value
        valued("bg", (s, v) -> set(s, ElementStyle.BACKGROUND_COLOR, TailwindColors.parse(v)));
        valued("text", TailwindParser::text);
        valued("border", TailwindParser::border);
        valued("rounded", (s, v) -> set(s, ElementStyle.BORDER_RADIUS, RADII.containsKey(v) ? RADII.get(v) : pixels(v)));
        valued("leading", (s, v) -> set(s, ElementStyle.LINE_HEIGHT, pixels(v)));
        valued("tracking", (s, v) -> set(s, ElementStyle.LETTER_SPACING, TRACKING.containsKey(v) ? TRACKING.get(v) : pixels(v)));
        valued("opacity", (s, v) -> {
            Integer percent = number(v);
            if (percent != null) s.set(ElementStyle.OPACITY, percent / 100.0f);
        });
        valued("z", (s, v) -> set(s, ElementStyle.Z_INDEX, number(v)));
        valued("grow", (s, v) -> set(s, ElementStyle.FLEX_GROW, number(v)));
        valued("shrink", (s, v) -> set(s, ElementStyle.FLEX_SHRINK, number(v)));

        // longest first, so "min-w-4" doesn't get read as the "m" prefix
        PREFIXES = new ArrayList<>(VALUED.keySet());
        PREFIXES.sort(Comparator.comparingInt(String::length).reversed());

        FONT_SIZES.put("xs", 12);
        FONT_SIZES.put("sm", 14);
        FONT_SIZES.put("base", 16);
        FONT_SIZES.put("lg", 18);
        FONT_SIZES.put("xl", 20);
        FONT_SIZES.put("2xl", 24);
        FONT_SIZES.put("3xl", 30);
        FONT_SIZES.put("4xl", 36);

        RADII.put("none", 0);
        RADII.put("sm", 2);
        RADII.put("md", 6);
        RADII.put("lg", 8);
        RADII.put("xl", 12);
        RADII.put("2xl", 16);
        RADII.put("3xl", 24);
        RADII.put("full", 9999);

        TRACKING.put("tighter", -1);
        TRACKING.put("tight", 0);
        TRACKING.put("normal", 0);
        TRACKING.put("wide", 1);
        TRACKING.put("wider", 2);
        TRACKING.put("widest", 3);
    }

    private TailwindParser(){}

    public static ElementStyle parse(String classList){
        ElementStyle.Builder style = ElementStyle.builder();
        parseInto(style, classList);
        return style.build();
    }

    /** Applies every class it knows onto the builder and hands back the ones it didn't. */
    public static List<String> parseInto(ElementStyle.Builder style, String classList){
        List<String> unknown = new ArrayList<>();
        if (classList == null) return unknown;

        for (String className : classList.strip().split("\\s+")) {
            if (!className.isEmpty() && !apply(style, className)) unknown.add(className);
        }

        return unknown;
    }

    private static boolean apply(ElementStyle.Builder style, String className){
        Consumer<ElementStyle.Builder> keyword = KEYWORDS.get(className);
        if (keyword != null) {
            keyword.accept(style);
            return true;
        }

        for (String prefix : PREFIXES) {
            if (!className.startsWith(prefix + "-")) continue;
            VALUED.get(prefix).apply(style, className.substring(prefix.length() + 1));
            return true;
        }

        return false;
    }

    private static void keyword(String className, Consumer<ElementStyle.Builder> setter){
        KEYWORDS.put(className, setter);
    }

    private static void valued(String prefix, Valued setter){
        VALUED.put(prefix, setter);
    }

    @SafeVarargs
    private static Valued spacing(ElementStyle.Key<Length>... keys){
        return (style, value) -> {
            Length length = spacing(value);
            for (ElementStyle.Key<Length> key : keys) set(style, key, length);
        };
    }

    private static Valued spacingAll(ElementStyle.Key<?>[] shorthand){
        return (style, value) -> {
            Length length = spacing(value);
            if (length != null) style.set(shorthand, length);
        };
    }

    /** Along the x axis "screen" means the viewport's width rather than its height. */
    private static Valued horizontal(Valued setter){
        return (style, value) -> setter.apply(style, value.equals("screen") ? "svw" : value);
    }

    private static void text(ElementStyle.Builder style, String value){
        Integer fontSize = FONT_SIZES.get(value);
        if (fontSize != null) {
            style.set(ElementStyle.FONT_SIZE, fontSize);
            return;
        }
        set(style, ElementStyle.COLOR, TailwindColors.parse(value));
    }

    private static void border(ElementStyle.Builder style, String value){
        ElementStyle.Key<Integer> side = switch (value.length() > 1 ? value.substring(0, 2) : value) {
            case "t", "t-" -> ElementStyle.BORDER_TOP_WIDTH;
            case "r", "r-" -> ElementStyle.BORDER_RIGHT_WIDTH;
            case "b", "b-" -> ElementStyle.BORDER_BOTTOM_WIDTH;
            case "l", "l-" -> ElementStyle.BORDER_LEFT_WIDTH;
            default -> null;
        };

        if (side != null) { // border-t, or border-t-2 for a width with it
            String width = value.length() > 2 ? value.substring(2) : "";
            style.set(side, width.isEmpty() ? 1 : number(width)); // border widths are straight pixels, not spacing units
            return;
        }

        Integer width = number(value);
        if (width != null) {
            style.set(ElementStyle.BORDER_WIDTH, width);
            return;
        }

        set(style, ElementStyle.BORDER_COLOR, TailwindColors.parse(value));
    }

    private static <T> void set(ElementStyle.Builder style, ElementStyle.Key<T> key, T value){
        if (value != null) style.set(key, value);
    }

    /**
     * Everything a size class can say: the spacing scale ("4"), the odd keywords ("px", "auto", "full",
     * "screen", "min", "max", "fit"), fractions ("1/2"), and arbitrary values ("[12px]", "[50%]", "[3vh]").
     */
    private static Length spacing(String value){
        switch (value) {
            case "px" -> { return Length.px(1); }
            case "auto" -> { return Length.AUTO; }
            case "full" -> { return Length.FULL; }
            case "screen" -> { return Length.viewHeight(100); } // w-screen gets swapped to vw further down
            case "svw", "lvw", "dvw" -> { return Length.viewWidth(100); }
            case "svh", "lvh", "dvh" -> { return Length.viewHeight(100); }
        }

        if (value.startsWith("[") && value.endsWith("]")) return Length.parse(value.substring(1, value.length() - 1));
        if (value.indexOf('/') > 0 || value.equals("min") || value.equals("max") || value.equals("fit")) return Length.parse(value);

        Float units = decimal(value);
        return units == null ? null : Length.px(units * SPACING_UNIT); // the scale is in units, not pixels
    }

    /** For the ones that are plain pixel counts whatever happens, like radii and line heights. */
    private static Integer pixels(String value){
        Length length = spacing(value);
        return length == null || length.unit() != Length.Unit.PX ? null : Math.round(length.value());
    }

    private static Integer number(String value){
        Float parsed = decimal(value.startsWith("[") && value.endsWith("]")
                ? value.substring(1, value.length() - 1)
                : value);
        return parsed == null ? null : Math.round(parsed);
    }

    private static Float decimal(String value){
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
