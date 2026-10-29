package net.tutla.craftytml.client.ctml;

import net.tutla.craftytml.client.ctml.style.Length;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every style an element can carry, named after the css property it comes from.
 * Each one is declared once as a typed Key and the values live in a flat array indexed by that key,
 * so adding a style is a single line and nothing has to be repeated per property.
 * A null slot means "not set", which leaves the fallback up to the renderer.
 */
public final class ElementStyle {
    private static final List<Key<?>> KEYS = new ArrayList<>();
    private static final Map<String, Key<?>> KEYS_BY_NAME = new HashMap<>();

    // layout
    public static final Key<Display> DISPLAY = key("display", Display.class);
    public static final Key<Position> POSITION = key("position", Position.class);
    public static final Key<Length> TOP = key("top", Length.class);
    public static final Key<Length> RIGHT = key("right", Length.class);
    public static final Key<Length> BOTTOM = key("bottom", Length.class);
    public static final Key<Length> LEFT = key("left", Length.class);
    public static final Key<Integer> Z_INDEX = key("z-index", Integer.class);

    // box size
    public static final Key<Length> WIDTH = key("width", Length.class);
    public static final Key<Length> HEIGHT = key("height", Length.class);
    public static final Key<Length> MIN_WIDTH = key("min-width", Length.class);
    public static final Key<Length> MIN_HEIGHT = key("min-height", Length.class);
    public static final Key<Length> MAX_WIDTH = key("max-width", Length.class);
    public static final Key<Length> MAX_HEIGHT = key("max-height", Length.class);
    public static final Key<BoxSizing> BOX_SIZING = key("box-sizing", BoxSizing.class);

    // box margin
    public static final Key<Length> MARGIN_TOP = key("margin-top", Length.class);
    public static final Key<Length> MARGIN_RIGHT = key("margin-right", Length.class);
    public static final Key<Length> MARGIN_BOTTOM = key("margin-bottom", Length.class);
    public static final Key<Length> MARGIN_LEFT = key("margin-left", Length.class);

    // box padding
    public static final Key<Length> PADDING_TOP = key("padding-top", Length.class);
    public static final Key<Length> PADDING_RIGHT = key("padding-right", Length.class);
    public static final Key<Length> PADDING_BOTTOM = key("padding-bottom", Length.class);
    public static final Key<Length> PADDING_LEFT = key("padding-left", Length.class);

    // box border
    public static final Key<Integer> BORDER_TOP_WIDTH = key("border-top-width", Integer.class);
    public static final Key<Integer> BORDER_RIGHT_WIDTH = key("border-right-width", Integer.class);
    public static final Key<Integer> BORDER_BOTTOM_WIDTH = key("border-bottom-width", Integer.class);
    public static final Key<Integer> BORDER_LEFT_WIDTH = key("border-left-width", Integer.class);
    public static final Key<Integer> BORDER_COLOR = key("border-color", Integer.class);
    public static final Key<Integer> BORDER_RADIUS = key("border-radius", Integer.class);

    // background
    public static final Key<Integer> BACKGROUND_COLOR = key("background-color", Integer.class);
    public static final Key<String> BACKGROUND_IMAGE = key("background-image", String.class);

    // text
    public static final Key<Integer> COLOR = key("color", Integer.class);
    public static final Key<Integer> FONT_SIZE = key("font-size", Integer.class);
    public static final Key<Boolean> BOLD = key("bold", Boolean.class);
    public static final Key<Boolean> ITALIC = key("italic", Boolean.class);
    public static final Key<Boolean> UNDERLINE = key("underline", Boolean.class);
    public static final Key<Boolean> STRIKETHROUGH = key("strikethrough", Boolean.class);
    public static final Key<Boolean> TEXT_SHADOW = key("text-shadow", Boolean.class);
    public static final Key<TextAlign> TEXT_ALIGN = key("text-align", TextAlign.class);
    public static final Key<Integer> LINE_HEIGHT = key("line-height", Integer.class);
    public static final Key<Integer> LETTER_SPACING = key("letter-spacing", Integer.class);
    public static final Key<WhiteSpace> WHITE_SPACE = key("white-space", WhiteSpace.class);

    // flex
    public static final Key<FlexDirection> FLEX_DIRECTION = key("flex-direction", FlexDirection.class);
    public static final Key<FlexWrap> FLEX_WRAP = key("flex-wrap", FlexWrap.class);
    public static final Key<JustifyContent> JUSTIFY_CONTENT = key("justify-content", JustifyContent.class);
    public static final Key<AlignItems> ALIGN_ITEMS = key("align-items", AlignItems.class);
    public static final Key<Length> GAP = key("gap", Length.class);
    public static final Key<Integer> FLEX_GROW = key("flex-grow", Integer.class);
    public static final Key<Integer> FLEX_SHRINK = key("flex-shrink", Integer.class);

    // misc
    public static final Key<Overflow> OVERFLOW = key("overflow", Overflow.class);
    public static final Key<Visibility> VISIBILITY = key("visibility", Visibility.class);
    public static final Key<Float> OPACITY = key("opacity", Float.class);

    // the shorthands that just hand the same value to all four sides
    public static final Key<?>[] MARGIN = { MARGIN_TOP, MARGIN_RIGHT, MARGIN_BOTTOM, MARGIN_LEFT };
    public static final Key<?>[] PADDING = { PADDING_TOP, PADDING_RIGHT, PADDING_BOTTOM, PADDING_LEFT };
    public static final Key<?>[] BORDER_WIDTH = { BORDER_TOP_WIDTH, BORDER_RIGHT_WIDTH, BORDER_BOTTOM_WIDTH, BORDER_LEFT_WIDTH };
    public static final Key<?>[] INSET = { TOP, RIGHT, BOTTOM, LEFT };

    /** The ones that pass down the tree on their own, like css does it. */
    public static final Key<?>[] INHERITED = {
            COLOR, FONT_SIZE, BOLD, ITALIC, UNDERLINE, STRIKETHROUGH, TEXT_SHADOW,
            TEXT_ALIGN, LINE_HEIGHT, LETTER_SPACING, WHITE_SPACE, VISIBILITY
    };

    /** Nothing set at all; what an element starts out with before any style lands on it. */
    public static final ElementStyle UNSET = new ElementStyle(new Object[KEYS.size()]);

    public enum Display { BLOCK, INLINE, INLINE_BLOCK, FLEX, NONE }
    public enum Position { STATIC, RELATIVE, ABSOLUTE, FIXED }
    public enum BoxSizing { CONTENT_BOX, BORDER_BOX }
    public enum TextAlign { LEFT, CENTER, RIGHT }
    public enum WhiteSpace { NORMAL, NOWRAP, PRE }
    public enum FlexDirection { ROW, ROW_REVERSE, COLUMN, COLUMN_REVERSE }
    public enum FlexWrap { NOWRAP, WRAP, WRAP_REVERSE }
    public enum JustifyContent { START, CENTER, END, SPACE_BETWEEN, SPACE_AROUND, SPACE_EVENLY }
    public enum AlignItems { START, CENTER, END, STRETCH, BASELINE }
    public enum Overflow { VISIBLE, HIDDEN, SCROLL }
    public enum Visibility { VISIBLE, HIDDEN }

    private final Object[] values;

    private ElementStyle(Object[] values){ this.values = values; }

    private static <T> Key<T> key(String cssName, Class<T> type){
        Key<T> key = new Key<>(cssName, type, KEYS.size());
        KEYS.add(key);
        KEYS_BY_NAME.put(cssName, key);
        return key;
    }

    public static List<Key<?>> keys(){
        return Collections.unmodifiableList(KEYS);
    }

    public static Key<?> keyFromName(String cssName){
        return KEYS_BY_NAME.get(cssName);
    }

    public static Builder builder(){
        return new Builder(new Object[KEYS.size()]);
    }

    public Builder toBuilder(){
        return new Builder(values.clone());
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Key<T> key){
        return (T) values[key.index];
    }

    public <T> T get(Key<T> key, T fallback){
        T value = get(key);
        return value == null ? fallback : value;
    }

    public int get(Key<Integer> key, int fallback){
        Integer value = get(key);
        return value == null ? fallback : value;
    }

    public boolean get(Key<Boolean> key, boolean fallback){
        Boolean value = get(key);
        return value == null ? fallback : value;
    }

    public float get(Key<Float> key, float fallback){
        Float value = get(key);
        return value == null ? fallback : value;
    }

    public boolean isSet(Key<?> key){
        return values[key.index] != null;
    }

    public <T> ElementStyle with(Key<T> key, T value){
        return toBuilder().set(key, value).build();
    }

    @SafeVarargs
    public final int sum(Key<Integer>... keys){
        int total = 0;
        for (Key<Integer> key : keys) total += get(key, 0);
        return total;
    }

    public int horizontalBorder(){ return sum(BORDER_LEFT_WIDTH, BORDER_RIGHT_WIDTH); }
    public int verticalBorder(){ return sum(BORDER_TOP_WIDTH, BORDER_BOTTOM_WIDTH); }

    /** Pixels for a length key, measured against the basis layout hands in. */
    public int resolve(Key<Length> key, int basis, int viewWidth, int viewHeight, int fallback){
        return Length.resolve(get(key), basis, viewWidth, viewHeight, fallback);
    }

    /** Falls back to whatever the other style holds for anything this one leaves unset, like cascading does. */
    public ElementStyle inherit(ElementStyle parent){
        if (parent == null) return this;
        Object[] merged = values.clone();
        for (int i = 0; i < merged.length; i++) if (merged[i] == null) merged[i] = parent.values[i];
        return new ElementStyle(merged);
    }

    /** What this element ends up with under a parent: only the inherited keys come down. */
    public ElementStyle inheritFrom(ElementStyle parent){
        if (parent == null) return this;
        Object[] merged = values.clone();
        for (Key<?> key : INHERITED) if (merged[key.index] == null) merged[key.index] = parent.values[key.index];
        return new ElementStyle(merged);
    }

    public Map<String, Object> getAssignedStyles(){
        Map<String, Object> assigned = new LinkedHashMap<>();
        for (Key<?> key : KEYS) if (values[key.index] != null) assigned.put(key.cssName, values[key.index]);
        return assigned;
    }

    @Override
    public boolean equals(Object other){
        return other instanceof ElementStyle style && Arrays.equals(values, style.values);
    }

    @Override
    public int hashCode(){
        return Arrays.hashCode(values);
    }

    @Override
    public String toString(){
        return "ElementStyle" + getAssignedStyles();
    }

    public static final class Key<T> {
        public final String cssName;
        public final Class<T> type;
        final int index;

        private Key(String cssName, Class<T> type, int index){
            this.cssName = cssName;
            this.type = type;
            this.index = index;
        }

        @Override
        public String toString(){ return cssName; }
    }

    public static final class Builder {
        private final Object[] values;

        private Builder(Object[] values){ this.values = values; }

        public <T> Builder set(Key<T> key, T value){
            values[key.index] = value;
            return this;
        }

        /** For the shorthands: one value onto every side the shorthand covers. */
        public <T> Builder set(Key<?>[] shorthand, T value){
            for (Key<?> key : shorthand) values[key.index] = key.type.cast(value);
            return this;
        }

        public Builder set(String cssName, Object value){
            Key<?> key = KEYS_BY_NAME.get(cssName);
            if (key == null) throw new IllegalArgumentException("Unknown style: " + cssName);
            values[key.index] = key.type.cast(value);
            return this;
        }

        public ElementStyle build(){
            return new ElementStyle(values.clone());
        }
    }
}
