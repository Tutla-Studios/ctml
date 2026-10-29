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

    private enum Stop { FROM, VIA, TO }

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
        keyword("grid", s -> s.set(ElementStyle.DISPLAY, ElementStyle.Display.GRID));
        keyword("table", s -> s.set(ElementStyle.DISPLAY, ElementStyle.Display.TABLE));
        keyword("table-row", s -> s.set(ElementStyle.DISPLAY, ElementStyle.Display.TABLE_ROW));
        keyword("table-cell", s -> s.set(ElementStyle.DISPLAY, ElementStyle.Display.TABLE_CELL));
        keyword("table-row-group", s -> s.set(ElementStyle.DISPLAY, ElementStyle.Display.TABLE_ROW_GROUP));
        keyword("table-header-group", s -> s.set(ElementStyle.DISPLAY, ElementStyle.Display.TABLE_HEADER_GROUP));
        keyword("table-footer-group", s -> s.set(ElementStyle.DISPLAY, ElementStyle.Display.TABLE_FOOTER_GROUP));
        keyword("table-caption", s -> s.set(ElementStyle.DISPLAY, ElementStyle.Display.TABLE_CAPTION));
        keyword("border-collapse", s -> s.set(ElementStyle.BORDER_COLLAPSE, ElementStyle.BorderCollapse.COLLAPSE));
        keyword("border-separate", s -> s.set(ElementStyle.BORDER_COLLAPSE, ElementStyle.BorderCollapse.SEPARATE));
        keyword("caption-top", s -> s.set(ElementStyle.CAPTION_SIDE, ElementStyle.CaptionSide.TOP));
        keyword("caption-bottom", s -> s.set(ElementStyle.CAPTION_SIDE, ElementStyle.CaptionSide.BOTTOM));
        keyword("table-auto", s -> s.set(ElementStyle.TABLE_LAYOUT, ElementStyle.TableLayout.AUTO));
        keyword("table-fixed", s -> s.set(ElementStyle.TABLE_LAYOUT, ElementStyle.TableLayout.FIXED));
        keyword("hidden", s -> s.set(ElementStyle.DISPLAY, ElementStyle.Display.NONE));

        // position
        keyword("static", s -> s.set(ElementStyle.POSITION, ElementStyle.Position.STATIC));
        keyword("relative", s -> s.set(ElementStyle.POSITION, ElementStyle.Position.RELATIVE));
        keyword("absolute", s -> s.set(ElementStyle.POSITION, ElementStyle.Position.ABSOLUTE));
        keyword("fixed", s -> s.set(ElementStyle.POSITION, ElementStyle.Position.FIXED));
        keyword("sticky", s -> s.set(ElementStyle.POSITION, ElementStyle.Position.STICKY));

        keyword("float-left", s -> s.set(ElementStyle.FLOAT, ElementStyle.Floating.LEFT));
        keyword("float-right", s -> s.set(ElementStyle.FLOAT, ElementStyle.Floating.RIGHT));
        keyword("float-none", s -> s.set(ElementStyle.FLOAT, ElementStyle.Floating.NONE));
        keyword("clear-left", s -> s.set(ElementStyle.CLEAR, ElementStyle.Clear.LEFT));
        keyword("clear-right", s -> s.set(ElementStyle.CLEAR, ElementStyle.Clear.RIGHT));
        keyword("clear-both", s -> s.set(ElementStyle.CLEAR, ElementStyle.Clear.BOTH));
        keyword("clear-none", s -> s.set(ElementStyle.CLEAR, ElementStyle.Clear.NONE));

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
        // the flex shorthands, which are grow / shrink / basis in one
        keyword("flex-1", s -> s.set(ElementStyle.FLEX_GROW, 1).set(ElementStyle.FLEX_SHRINK, 1).set(ElementStyle.FLEX_BASIS, Length.ZERO));
        keyword("flex-auto", s -> s.set(ElementStyle.FLEX_GROW, 1).set(ElementStyle.FLEX_SHRINK, 1).set(ElementStyle.FLEX_BASIS, Length.AUTO));
        keyword("flex-initial", s -> s.set(ElementStyle.FLEX_GROW, 0).set(ElementStyle.FLEX_SHRINK, 1).set(ElementStyle.FLEX_BASIS, Length.AUTO));
        keyword("flex-none", s -> s.set(ElementStyle.FLEX_GROW, 0).set(ElementStyle.FLEX_SHRINK, 0).set(ElementStyle.FLEX_BASIS, Length.AUTO));
        keyword("grow", s -> s.set(ElementStyle.FLEX_GROW, 1));
        keyword("shrink", s -> s.set(ElementStyle.FLEX_SHRINK, 1));

        keyword("self-auto", s -> s.set(ElementStyle.ALIGN_SELF, null)); // back to whatever the container says
        keyword("self-start", s -> s.set(ElementStyle.ALIGN_SELF, ElementStyle.AlignItems.START));
        keyword("self-center", s -> s.set(ElementStyle.ALIGN_SELF, ElementStyle.AlignItems.CENTER));
        keyword("self-end", s -> s.set(ElementStyle.ALIGN_SELF, ElementStyle.AlignItems.END));
        keyword("self-stretch", s -> s.set(ElementStyle.ALIGN_SELF, ElementStyle.AlignItems.STRETCH));
        keyword("self-baseline", s -> s.set(ElementStyle.ALIGN_SELF, ElementStyle.AlignItems.BASELINE));

        keyword("content-normal", s -> s.set(ElementStyle.ALIGN_CONTENT, ElementStyle.AlignContent.START));
        keyword("content-start", s -> s.set(ElementStyle.ALIGN_CONTENT, ElementStyle.AlignContent.START));
        keyword("content-center", s -> s.set(ElementStyle.ALIGN_CONTENT, ElementStyle.AlignContent.CENTER));
        keyword("content-end", s -> s.set(ElementStyle.ALIGN_CONTENT, ElementStyle.AlignContent.END));
        keyword("content-between", s -> s.set(ElementStyle.ALIGN_CONTENT, ElementStyle.AlignContent.SPACE_BETWEEN));
        keyword("content-around", s -> s.set(ElementStyle.ALIGN_CONTENT, ElementStyle.AlignContent.SPACE_AROUND));
        keyword("content-evenly", s -> s.set(ElementStyle.ALIGN_CONTENT, ElementStyle.AlignContent.SPACE_EVENLY));
        keyword("content-stretch", s -> s.set(ElementStyle.ALIGN_CONTENT, ElementStyle.AlignContent.STRETCH));

        keyword("order-first", s -> s.set(ElementStyle.ORDER, -9999));
        keyword("order-last", s -> s.set(ElementStyle.ORDER, 9999));
        keyword("order-none", s -> s.set(ElementStyle.ORDER, 0));

        keyword("justify-start", s -> s.set(ElementStyle.JUSTIFY_CONTENT, ElementStyle.JustifyContent.START));
        keyword("justify-center", s -> s.set(ElementStyle.JUSTIFY_CONTENT, ElementStyle.JustifyContent.CENTER));
        keyword("justify-end", s -> s.set(ElementStyle.JUSTIFY_CONTENT, ElementStyle.JustifyContent.END));
        keyword("justify-between", s -> s.set(ElementStyle.JUSTIFY_CONTENT, ElementStyle.JustifyContent.SPACE_BETWEEN));
        keyword("justify-around", s -> s.set(ElementStyle.JUSTIFY_CONTENT, ElementStyle.JustifyContent.SPACE_AROUND));
        keyword("justify-evenly", s -> s.set(ElementStyle.JUSTIFY_CONTENT, ElementStyle.JustifyContent.SPACE_EVENLY));

        // grid
        keyword("grid-flow-row", s -> s.set(ElementStyle.GRID_AUTO_FLOW, ElementStyle.GridFlow.ROW));
        keyword("grid-flow-col", s -> s.set(ElementStyle.GRID_AUTO_FLOW, ElementStyle.GridFlow.COLUMN));
        keyword("grid-cols-none", s -> s.set(ElementStyle.GRID_TEMPLATE_COLUMNS, ElementStyle.Tracks.NONE));
        keyword("grid-rows-none", s -> s.set(ElementStyle.GRID_TEMPLATE_ROWS, ElementStyle.Tracks.NONE));
        keyword("col-span-full", s -> s.set(ElementStyle.GRID_COLUMN_SPAN, Integer.MAX_VALUE));
        keyword("row-span-full", s -> s.set(ElementStyle.GRID_ROW_SPAN, Integer.MAX_VALUE));
        keyword("col-auto", s -> s.set(ElementStyle.GRID_COLUMN_START, null).set(ElementStyle.GRID_COLUMN_SPAN, null));
        keyword("row-auto", s -> s.set(ElementStyle.GRID_ROW_START, null).set(ElementStyle.GRID_ROW_SPAN, null));

        keyword("justify-items-start", s -> s.set(ElementStyle.JUSTIFY_ITEMS, ElementStyle.AlignItems.START));
        keyword("justify-items-center", s -> s.set(ElementStyle.JUSTIFY_ITEMS, ElementStyle.AlignItems.CENTER));
        keyword("justify-items-end", s -> s.set(ElementStyle.JUSTIFY_ITEMS, ElementStyle.AlignItems.END));
        keyword("justify-items-stretch", s -> s.set(ElementStyle.JUSTIFY_ITEMS, ElementStyle.AlignItems.STRETCH));
        keyword("justify-self-auto", s -> s.set(ElementStyle.JUSTIFY_SELF, null));
        keyword("justify-self-start", s -> s.set(ElementStyle.JUSTIFY_SELF, ElementStyle.AlignItems.START));
        keyword("justify-self-center", s -> s.set(ElementStyle.JUSTIFY_SELF, ElementStyle.AlignItems.CENTER));
        keyword("justify-self-end", s -> s.set(ElementStyle.JUSTIFY_SELF, ElementStyle.AlignItems.END));
        keyword("justify-self-stretch", s -> s.set(ElementStyle.JUSTIFY_SELF, ElementStyle.AlignItems.STRETCH));

        keyword("items-start", s -> s.set(ElementStyle.ALIGN_ITEMS, ElementStyle.AlignItems.START));
        keyword("items-center", s -> s.set(ElementStyle.ALIGN_ITEMS, ElementStyle.AlignItems.CENTER));
        keyword("items-end", s -> s.set(ElementStyle.ALIGN_ITEMS, ElementStyle.AlignItems.END));
        keyword("items-stretch", s -> s.set(ElementStyle.ALIGN_ITEMS, ElementStyle.AlignItems.STRETCH));
        keyword("items-baseline", s -> s.set(ElementStyle.ALIGN_ITEMS, ElementStyle.AlignItems.BASELINE));

        // text
        // vertical-align, which is what an inline item sits against on its line
        keyword("align-baseline", s -> s.set(ElementStyle.VERTICAL_ALIGN, ElementStyle.VerticalAlign.BASELINE));
        keyword("align-top", s -> s.set(ElementStyle.VERTICAL_ALIGN, ElementStyle.VerticalAlign.TOP));
        keyword("align-middle", s -> s.set(ElementStyle.VERTICAL_ALIGN, ElementStyle.VerticalAlign.MIDDLE));
        keyword("align-bottom", s -> s.set(ElementStyle.VERTICAL_ALIGN, ElementStyle.VerticalAlign.BOTTOM));
        keyword("align-text-top", s -> s.set(ElementStyle.VERTICAL_ALIGN, ElementStyle.VerticalAlign.TEXT_TOP));
        keyword("align-text-bottom", s -> s.set(ElementStyle.VERTICAL_ALIGN, ElementStyle.VerticalAlign.TEXT_BOTTOM));
        keyword("align-sub", s -> s.set(ElementStyle.VERTICAL_ALIGN, ElementStyle.VerticalAlign.SUB));
        keyword("align-super", s -> s.set(ElementStyle.VERTICAL_ALIGN, ElementStyle.VerticalAlign.SUPER));

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
        keyword("text-clip", s -> s.set(ElementStyle.TEXT_OVERFLOW, ElementStyle.TextOverflow.CLIP));
        keyword("text-ellipsis", s -> s.set(ElementStyle.TEXT_OVERFLOW, ElementStyle.TextOverflow.ELLIPSIS));
        keyword("truncate", s -> s.set(ElementStyle.TEXT_OVERFLOW, ElementStyle.TextOverflow.ELLIPSIS)
                .set(ElementStyle.WHITE_SPACE, ElementStyle.WhiteSpace.NOWRAP)
                .set(ElementStyle.OVERFLOW, ElementStyle.Overflow.HIDDEN));
        keyword("break-normal", s -> s.set(ElementStyle.WORD_BREAK, ElementStyle.WordBreak.NORMAL));
        keyword("break-words", s -> s.set(ElementStyle.WORD_BREAK, ElementStyle.WordBreak.BREAK_WORD));
        keyword("break-all", s -> s.set(ElementStyle.WORD_BREAK, ElementStyle.WordBreak.BREAK_ALL));
        keyword("break-keep", s -> s.set(ElementStyle.WORD_BREAK, ElementStyle.WordBreak.KEEP_ALL));
        keyword("line-clamp-none", s -> s.set(ElementStyle.LINE_CLAMP, 0));
        keyword("whitespace-normal", s -> s.set(ElementStyle.WHITE_SPACE, ElementStyle.WhiteSpace.NORMAL));
        keyword("whitespace-nowrap", s -> s.set(ElementStyle.WHITE_SPACE, ElementStyle.WhiteSpace.NOWRAP));
        keyword("whitespace-pre", s -> s.set(ElementStyle.WHITE_SPACE, ElementStyle.WhiteSpace.PRE));

        // overflow, both axes at once and one at a time
        for (ElementStyle.Overflow overflow : ElementStyle.Overflow.values()) {
            String suffix = overflow.name().toLowerCase();
            keyword("overflow-" + suffix, s -> s.set(ElementStyle.OVERFLOW, overflow));
            keyword("overflow-x-" + suffix, s -> s.set(ElementStyle.OVERFLOW_X, overflow));
            keyword("overflow-y-" + suffix, s -> s.set(ElementStyle.OVERFLOW_Y, overflow));
        }

        // scrollbars, which css spells as scrollbar-width / scrollbar-color / scrollbar-gutter
        keyword("scrollbar", s -> s.set(ElementStyle.SCROLLBAR_HIDDEN, false));
        keyword("scrollbar-hidden", s -> s.set(ElementStyle.SCROLLBAR_HIDDEN, true)); // still scrolls, just nothing to see
        keyword("scrollbar-none", s -> s.set(ElementStyle.SCROLLBAR_HIDDEN, true));
        keyword("scrollbar-auto", s -> s.set(ElementStyle.SCROLLBAR_HIDDEN, false));
        keyword("scrollbar-thin", s -> s.set(ElementStyle.SCROLLBAR_WIDTH, 2));
        keyword("scrollbar-thick", s -> s.set(ElementStyle.SCROLLBAR_WIDTH, 6));
        // room kept for the bar, the way css spells scrollbar-gutter
        keyword("scrollbar-gutter", s -> s.set(ElementStyle.SCROLLBAR_GUTTER, ElementStyle.ScrollbarGutter.STABLE));
        keyword("scrollbar-gutter-auto", s -> s.set(ElementStyle.SCROLLBAR_GUTTER, ElementStyle.ScrollbarGutter.AUTO));
        keyword("scrollbar-gutter-stable", s -> s.set(ElementStyle.SCROLLBAR_GUTTER, ElementStyle.ScrollbarGutter.STABLE));
        keyword("scrollbar-gutter-both-edges", s -> s.set(ElementStyle.SCROLLBAR_GUTTER, ElementStyle.ScrollbarGutter.BOTH_EDGES));
        keyword("scrollbar-stable", s -> s.set(ElementStyle.SCROLLBAR_GUTTER, ElementStyle.ScrollbarGutter.STABLE));
        keyword("scrollbar-both-edges", s -> s.set(ElementStyle.SCROLLBAR_GUTTER, ElementStyle.ScrollbarGutter.BOTH_EDGES));
        // bar floats over the content, which is what ctml does unless it is told otherwise
        keyword("scrollbar-overlay", s -> s.set(ElementStyle.SCROLLBAR_GUTTER, ElementStyle.ScrollbarGutter.AUTO));

        // what happens to a wheel the box cannot use, both axes at once and one at a time
        for (ElementStyle.Overscroll overscroll : ElementStyle.Overscroll.values()) {
            String suffix = overscroll.name().toLowerCase();
            keyword("overscroll-" + suffix, s -> s.set(ElementStyle.OVERSCROLL_BEHAVIOR, overscroll));
            keyword("overscroll-x-" + suffix, s -> s.set(ElementStyle.OVERSCROLL_BEHAVIOR_X, overscroll));
            keyword("overscroll-y-" + suffix, s -> s.set(ElementStyle.OVERSCROLL_BEHAVIOR_Y, overscroll));
        }

        // whether a scroll lands at once or slides there
        keyword("scroll-auto", s -> s.set(ElementStyle.SCROLL_BEHAVIOR, ElementStyle.ScrollBehavior.AUTO));
        keyword("scroll-smooth", s -> s.set(ElementStyle.SCROLL_BEHAVIOR, ElementStyle.ScrollBehavior.SMOOTH));

        // whether what is on show is held still when something above it resizes
        keyword("overflow-anchor-auto", s -> s.set(ElementStyle.OVERFLOW_ANCHOR, ElementStyle.OverflowAnchor.AUTO));
        keyword("overflow-anchor-none", s -> s.set(ElementStyle.OVERFLOW_ANCHOR, ElementStyle.OverflowAnchor.NONE));
        keyword("scrollbar-square", s -> s.set(ElementStyle.SCROLLBAR_RADIUS, 0));
        keyword("scrollbar-rounded", s -> s.set(ElementStyle.SCROLLBAR_RADIUS, 2));

        // misc
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
        valued("gap", spacingAll(ElementStyle.GAP));
        valued("gap-x", spacing(ElementStyle.COLUMN_GAP)); // x is along a row, so it is the column gap
        valued("gap-y", spacing(ElementStyle.ROW_GAP));

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

        // scrollbar-w-3, scrollbar-rounded-2, scrollbar-thumb-sky-500, scrollbar-track-slate-800/50
        valued("scrollbar-w", (s, v) -> set(s, ElementStyle.SCROLLBAR_WIDTH, pixels(v)));
        valued("scrollbar-rounded", (s, v) -> set(s, ElementStyle.SCROLLBAR_RADIUS, pixels(v)));
        valued("scrollbar-thumb", (s, v) -> set(s, ElementStyle.SCROLLBAR_THUMB_COLOR, TailwindColors.parse(v)));
        valued("scrollbar-track", (s, v) -> set(s, ElementStyle.SCROLLBAR_TRACK_COLOR, TailwindColors.parse(v)));
        valued("grow", (s, v) -> set(s, ElementStyle.FLEX_GROW, number(v)));
        valued("shrink", (s, v) -> set(s, ElementStyle.FLEX_SHRINK, number(v)));
        valued("basis", spacing(ElementStyle.FLEX_BASIS));
        valued("order", (s, v) -> set(s, ElementStyle.ORDER, number(v)));
        // grid templates: a plain count means that many equal shares, anything else is a track list
        valued("grid-cols", tracks(ElementStyle.GRID_TEMPLATE_COLUMNS));
        valued("grid-rows", tracks(ElementStyle.GRID_TEMPLATE_ROWS));
        valued("auto-cols", (s, v) -> set(s, ElementStyle.GRID_AUTO_COLUMNS, single(v)));
        valued("auto-rows", (s, v) -> set(s, ElementStyle.GRID_AUTO_ROWS, single(v)));
        valued("col-span", (s, v) -> set(s, ElementStyle.GRID_COLUMN_SPAN, number(v)));
        valued("col-start", (s, v) -> set(s, ElementStyle.GRID_COLUMN_START, number(v)));
        valued("col-end", (s, v) -> set(s, ElementStyle.GRID_COLUMN_END, number(v)));
        valued("row-span", (s, v) -> set(s, ElementStyle.GRID_ROW_SPAN, number(v)));
        valued("row-start", (s, v) -> set(s, ElementStyle.GRID_ROW_START, number(v)));
        valued("row-end", (s, v) -> set(s, ElementStyle.GRID_ROW_END, number(v)));

        // columns-3 spreads the content over three, columns-[80px] over as many as fit that wide
        valued("columns", (s, v) -> {
            Integer count = number(v);
            if (count != null) {
                s.set(ElementStyle.COLUMN_COUNT, count);
                return;
            }
            Length wide = spacing(v);
            if (wide != null) s.set(ElementStyle.COLUMN_WIDTH, wide);
        });

        valued("line-clamp", (s, v) -> {
            Integer lines = number(v);
            if (lines == null) return;
            s.set(ElementStyle.LINE_CLAMP, lines);
            if (lines > 0) s.set(ElementStyle.TEXT_OVERFLOW, ElementStyle.TextOverflow.ELLIPSIS);
        });

        // gradients: the shape class picks the axis, the stop classes fill in the colours.
        // bg-gradient-to-r is tailwind v3's spelling and bg-linear-to-r v4's; both are taken.
        for (Gradient.Direction direction : Gradient.Direction.values()) {
            String suffix = direction.name().toLowerCase().replace('_', '-');
            keyword("bg-gradient-" + suffix, s -> direction(s, ElementStyle.BACKGROUND_GRADIENT, direction));
            keyword("bg-linear-" + suffix, s -> direction(s, ElementStyle.BACKGROUND_GRADIENT, direction));
            keyword("text-gradient-" + suffix, s -> direction(s, ElementStyle.TEXT_GRADIENT, direction));
            keyword("text-linear-" + suffix, s -> direction(s, ElementStyle.TEXT_GRADIENT, direction));
        }

        keyword("bg-radial", s -> kind(s, ElementStyle.BACKGROUND_GRADIENT, Gradient.Kind.RADIAL, 0));
        keyword("bg-conic", s -> kind(s, ElementStyle.BACKGROUND_GRADIENT, Gradient.Kind.CONIC, 0));
        keyword("text-radial", s -> kind(s, ElementStyle.TEXT_GRADIENT, Gradient.Kind.RADIAL, 0));
        keyword("text-conic", s -> kind(s, ElementStyle.TEXT_GRADIENT, Gradient.Kind.CONIC, 0));

        // and an angle spelled out: bg-linear-[30deg] ramps that way, bg-conic-[90deg] starts there
        valued("bg-linear", angle(ElementStyle.BACKGROUND_GRADIENT, Gradient.Kind.LINEAR));
        valued("bg-conic", angle(ElementStyle.BACKGROUND_GRADIENT, Gradient.Kind.CONIC));
        valued("text-linear", angle(ElementStyle.TEXT_GRADIENT, Gradient.Kind.LINEAR));
        valued("text-conic", angle(ElementStyle.TEXT_GRADIENT, Gradient.Kind.CONIC));

        // where the background stops, and bg-clip-text for one clipped to the glyphs of the text
        keyword("bg-clip-border", s -> s.set(ElementStyle.BACKGROUND_CLIP, ElementStyle.BackgroundClip.BORDER_BOX));
        keyword("bg-clip-padding", s -> s.set(ElementStyle.BACKGROUND_CLIP, ElementStyle.BackgroundClip.PADDING_BOX));
        keyword("bg-clip-content", s -> s.set(ElementStyle.BACKGROUND_CLIP, ElementStyle.BackgroundClip.CONTENT_BOX));
        keyword("bg-clip-text", s -> s.set(ElementStyle.BACKGROUND_CLIP, ElementStyle.BackgroundClip.TEXT));

        valued("from", stop(ElementStyle.BACKGROUND_GRADIENT, Stop.FROM));
        valued("via", stop(ElementStyle.BACKGROUND_GRADIENT, Stop.VIA));
        valued("to", stop(ElementStyle.BACKGROUND_GRADIENT, Stop.TO));
        valued("text-from", stop(ElementStyle.TEXT_GRADIENT, Stop.FROM));
        valued("text-via", stop(ElementStyle.TEXT_GRADIENT, Stop.VIA));
        valued("text-to", stop(ElementStyle.TEXT_GRADIENT, Stop.TO));

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

    /** A direction class on its own, which keeps whatever stops are already there. */
    private static void direction(ElementStyle.Builder style, ElementStyle.Key<Gradient> key, Gradient.Direction direction){
        style.set(key, style.get(key, Gradient.of(direction)).withDirection(direction));
    }

    /** The same for a shape with no direction to it, and for one whose angle was spelled out. */
    private static void kind(ElementStyle.Builder style, ElementStyle.Key<Gradient> key, Gradient.Kind kind, float angle){
        style.set(key, style.get(key, Gradient.linear(angle)).withKind(kind, angle));
    }

    /** bg-linear-[30deg] and bg-conic-[90deg], where the value is an angle rather than a named side. */
    private static Valued angle(ElementStyle.Key<Gradient> key, Gradient.Kind kind){
        return (style, value) -> {
            String raw = unwrap(value);
            if (raw.endsWith("deg")) raw = raw.substring(0, raw.length() - 3);
            Float degrees = decimal(raw);
            if (degrees != null) kind(style, key, kind, degrees);
        };
    }

    /**
     * from- / via- / to-, each one merged into the gradient the other classes are building.
     * The value is a colour, or a percentage ("from-20%") which moves that stop along the gradient
     * without saying anything about its colour.
     */
    private static Valued stop(ElementStyle.Key<Gradient> key, Stop stop){
        return (style, value) -> {
            Float position = position(value);
            Integer color = position == null ? TailwindColors.parse(value) : null;
            if (color == null && position == null) return;

            Gradient gradient = style.get(key, Gradient.of(Gradient.Direction.TO_R));
            Gradient.Stop held = switch (stop) {
                case FROM -> gradient.from();
                case VIA -> gradient.via();
                case TO -> gradient.to();
            };
            Gradient.Stop merged = color != null ? held.withColor(color) : held.withPosition(position);

            style.set(key, switch (stop) {
                case FROM -> gradient.withFrom(merged);
                case VIA -> gradient.withVia(merged);
                case TO -> gradient.withTo(merged);
            });
        };
    }

    /** A stop position: "20%" or "[20%]", as a fraction of the way along. Anything else is not one. */
    private static Float position(String value){
        String raw = unwrap(value);
        if (!raw.endsWith("%")) return null;
        Float percent = decimal(raw.substring(0, raw.length() - 1));
        return percent == null ? null : percent / 100.0f;
    }

    /** grid-cols-3 is three equal shares; grid-cols-[100px_1fr] is the track list spelled out. */
    private static Valued tracks(ElementStyle.Key<ElementStyle.Tracks> key){
        return (style, value) -> {
            Integer count = number(value);
            if (count != null) {
                set(style, key, new ElementStyle.Tracks(Track.repeat(count, Track.fraction(1))));
                return;
            }
            List<Track> parsed = Track.parse(unwrap(value));
            if (!parsed.isEmpty()) style.set(key, new ElementStyle.Tracks(parsed));
        };
    }

    /** One track, for the implicit rows and columns: auto-rows-[20px], auto-rows-min, auto-rows-fr. */
    private static ElementStyle.Tracks single(String value){
        Track track = Track.track(unwrap(value).equals("fr") ? "1fr" : unwrap(value));
        return track == null ? null : new ElementStyle.Tracks(List.of(track));
    }

    private static String unwrap(String value){
        return value.startsWith("[") && value.endsWith("]") ? value.substring(1, value.length() - 1) : value;
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
