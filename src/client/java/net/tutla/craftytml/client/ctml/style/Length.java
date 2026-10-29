package net.tutla.craftytml.client.ctml.style;

/**
 * Any size a style can hold: straight pixels, a percentage of whatever contains it, auto,
 * a slice of the viewport, or something measured off the content itself.
 * Nothing resolves until layout knows what it is being measured against.
 */
public record Length(float value, Unit unit) {
    public enum Unit {
        PX,
        PERCENT,
        AUTO,
        VIEW_WIDTH,
        VIEW_HEIGHT,
        MIN_CONTENT,
        MAX_CONTENT,
        FIT_CONTENT
    }

    /** Handed to resolve() for anything that cannot be worked out from a basis alone. */
    public static final int UNRESOLVED = Integer.MIN_VALUE;

    public static final Length ZERO = px(0);
    public static final Length AUTO = new Length(0, Unit.AUTO);
    public static final Length FULL = percent(100);
    public static final Length MIN_CONTENT = new Length(0, Unit.MIN_CONTENT);
    public static final Length MAX_CONTENT = new Length(0, Unit.MAX_CONTENT);
    public static final Length FIT_CONTENT = new Length(0, Unit.FIT_CONTENT);

    public static Length px(float value){ return new Length(value, Unit.PX); }
    public static Length percent(float value){ return new Length(value, Unit.PERCENT); }
    public static Length viewWidth(float value){ return new Length(value, Unit.VIEW_WIDTH); }
    public static Length viewHeight(float value){ return new Length(value, Unit.VIEW_HEIGHT); }

    public boolean isAuto(){ return unit == Unit.AUTO; }
    public boolean isPercent(){ return unit == Unit.PERCENT; }

    public boolean isContentBased(){
        return unit == Unit.MIN_CONTENT || unit == Unit.MAX_CONTENT || unit == Unit.FIT_CONTENT;
    }

    /** True when a basis plus the viewport is enough to turn this into a pixel count. */
    public boolean isDefinite(){
        return unit == Unit.PX || unit == Unit.PERCENT || unit == Unit.VIEW_WIDTH || unit == Unit.VIEW_HEIGHT;
    }

    /** Pixels, or UNRESOLVED for auto and the content based units, which only layout can settle. */
    public int resolve(int basis, int viewWidth, int viewHeight){
        return switch (unit) {
            case PX -> Math.round(value);
            case PERCENT -> Math.round(basis * value / 100.0f);
            case VIEW_WIDTH -> Math.round(viewWidth * value / 100.0f);
            case VIEW_HEIGHT -> Math.round(viewHeight * value / 100.0f);
            case AUTO, MIN_CONTENT, MAX_CONTENT, FIT_CONTENT -> UNRESOLVED;
        };
    }

    public int resolve(int basis, int viewWidth, int viewHeight, int fallback){
        int resolved = resolve(basis, viewWidth, viewHeight);
        return resolved == UNRESOLVED ? fallback : resolved;
    }

    /** Null safe, for the plenty of places where a style simply holds nothing. */
    public static int resolve(Length length, int basis, int viewWidth, int viewHeight, int fallback){
        return length == null ? fallback : length.resolve(basis, viewWidth, viewHeight, fallback);
    }

    public static boolean isDefinite(Length length){
        return length != null && length.isDefinite();
    }

    /**
     * Reads the css-ish forms: "12px", "12", "50%", "1/2", "auto", "100vw", "100vh",
     * "min-content", "max-content", "fit-content". Null when it is none of those.
     */
    public static Length parse(String text){
        if (text == null) return null;
        String value = text.strip();
        if (value.isEmpty()) return null;

        switch (value) {
            case "auto" -> { return AUTO; }
            case "full" -> { return FULL; }
            case "min-content", "min" -> { return MIN_CONTENT; }
            case "max-content", "max" -> { return MAX_CONTENT; }
            case "fit-content", "fit" -> { return FIT_CONTENT; }
        }

        if (value.indexOf('/') > 0) { // fractions like 1/2 or 2/3 are just percentages
            String[] parts = value.split("/", 2);
            Float numerator = number(parts[0]);
            Float denominator = number(parts[1]);
            if (numerator == null || denominator == null || denominator == 0) return null;
            return percent(numerator / denominator * 100.0f);
        }

        for (String suffix : new String[]{ "px", "%", "vw", "vh" }) {
            if (!value.endsWith(suffix)) continue;
            Float number = number(value.substring(0, value.length() - suffix.length()));
            if (number == null) return null;
            return switch (suffix) {
                case "px" -> px(number);
                case "%" -> percent(number);
                case "vw" -> viewWidth(number);
                default -> viewHeight(number);
            };
        }

        Float number = number(value);
        return number == null ? null : px(number);
    }

    private static Float number(String value){
        try {
            return Float.parseFloat(value.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String toString(){
        return switch (unit) {
            case PX -> trim(value) + "px";
            case PERCENT -> trim(value) + "%";
            case VIEW_WIDTH -> trim(value) + "vw";
            case VIEW_HEIGHT -> trim(value) + "vh";
            case AUTO -> "auto";
            case MIN_CONTENT -> "min-content";
            case MAX_CONTENT -> "max-content";
            case FIT_CONTENT -> "fit-content";
        };
    }

    private static String trim(float value){
        return value == Math.rint(value) ? String.valueOf((int) value) : String.valueOf(value);
    }
}
