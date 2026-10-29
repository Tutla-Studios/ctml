package net.tutla.craftytml.client.ctml.style;

import java.util.ArrayList;
import java.util.List;

/**
 * A gradient, built up the way tailwind builds one: a shape class ("bg-linear-to-r", "bg-radial",
 * "bg-conic-[30deg]") plus the stops that follow it ("from-red-500", "via-amber-400/60", "to-blue-500"),
 * each of which may carry a position of its own ("from-10%").
 * Each class lands on the same style key, so the value gets replaced rather than added to.
 * Nothing is drawable until there are at least two stops with colours.
 */
public record Gradient(Kind kind, float angle, Stop from, Stop via, Stop to) {
    /** A straight ramp, one running out from the middle, or one running round it. */
    public enum Kind { LINEAR, RADIAL, CONIC }

    /**
     * One stop: its colour, and how far along the gradient it sits as a fraction.
     * A colour of null is a position named before any colour was, and a position below zero
     * is one left to be spaced out with the others.
     */
    public record Stop(Integer color, float at) {
        public static final Stop UNSET = new Stop(null, -1);
        public boolean hasColor(){ return color != null; }
        public boolean hasPosition(){ return at >= 0; }
        public Stop withColor(int color){ return new Stop(color, at); }
        public Stop withPosition(float at){ return new Stop(color, at); }
    }

    /**
     * The eight directions tailwind names, as the css angle each one points: zero is up and it goes
     * round clockwise, so the gradient ends in the direction the class names.
     */
    public enum Direction {
        TO_T(0), TO_TR(45), TO_R(90), TO_BR(135), TO_B(180), TO_BL(225), TO_L(270), TO_TL(315);

        public final float angle;

        Direction(float angle){ this.angle = angle; }

        public String cssName(){
            return "to " + name().substring(3).toLowerCase();
        }
    }

    /** What a bare direction class makes, before any stop has been named. */
    public static Gradient of(Direction direction){
        return linear(direction.angle);
    }

    public static Gradient linear(float angle){
        return new Gradient(Kind.LINEAR, angle, Stop.UNSET, Stop.UNSET, Stop.UNSET);
    }

    /** A radial gradient runs out from the middle, so it has no angle to speak of. */
    public static Gradient radial(){
        return new Gradient(Kind.RADIAL, 0, Stop.UNSET, Stop.UNSET, Stop.UNSET);
    }

    /** A conic one runs round the middle, starting at whatever angle it is given. */
    public static Gradient conic(float angle){
        return new Gradient(Kind.CONIC, angle, Stop.UNSET, Stop.UNSET, Stop.UNSET);
    }

    public Gradient withKind(Kind kind, float angle){ return new Gradient(kind, angle, from, via, to); }
    public Gradient withDirection(Direction direction){ return new Gradient(Kind.LINEAR, direction.angle, from, via, to); }

    public Gradient withFrom(Stop stop){ return new Gradient(kind, angle, stop, via, to); }
    public Gradient withVia(Stop stop){ return new Gradient(kind, angle, from, stop, to); }
    public Gradient withTo(Stop stop){ return new Gradient(kind, angle, from, via, stop); }

    public Gradient withFrom(int color){ return withFrom(from.withColor(color)); }
    public Gradient withVia(int color){ return withVia(via.withColor(color)); }
    public Gradient withTo(int color){ return withTo(to.withColor(color)); }

    /**
     * The stops that have a colour, in order, each with the position it ends up at. The ones that were
     * not given a position are spread evenly over the whole gradient, and a position that would come
     * before the one in front of it is pulled forward to meet it, which is what css does.
     */
    public List<Stop> stops(){
        List<Stop> named = new ArrayList<>(3);
        if (from.hasColor()) named.add(from);
        if (via.hasColor()) named.add(via);
        if (to.hasColor()) named.add(to);

        List<Stop> placed = new ArrayList<>(named.size());
        float last = 0;
        for (int i = 0; i < named.size(); i++) {
            Stop stop = named.get(i);
            float at = stop.hasPosition()
                    ? Math.clamp(stop.at(), 0.0f, 1.0f)
                    : (named.size() == 1 ? 0 : i / (float) (named.size() - 1));
            at = Math.max(at, last);
            placed.add(new Stop(stop.color(), at));
            last = at;
        }
        return placed;
    }

    /** The colours alone, for whatever only wants to know what it is made of. */
    public List<Integer> colors(){
        List<Integer> colors = new ArrayList<>(3);
        for (Stop stop : stops()) colors.add(stop.color());
        return colors;
    }

    public boolean isDrawable(){
        return stops().size() >= 2;
    }

    /**
     * The colour somewhere along the gradient, 0 at the start and 1 at the end.
     * Before the first stop and after the last one the gradient holds that stop's colour, like css.
     */
    public int colorAt(float t){
        List<Stop> stops = stops();
        if (stops.isEmpty()) return 0;

        float clamped = Math.clamp(t, 0.0f, 1.0f);
        Stop first = stops.getFirst();
        Stop last = stops.getLast();
        if (stops.size() == 1 || clamped <= first.at()) return first.color();
        if (clamped >= last.at()) return last.color();

        for (int i = 0; i < stops.size() - 1; i++) {
            Stop start = stops.get(i);
            Stop end = stops.get(i + 1);
            if (clamped > end.at()) continue;
            float span = end.at() - start.at();
            return span <= 0 ? end.color() : lerp(start.color(), end.color(), (clamped - start.at()) / span);
        }
        return last.color();
    }

    /**
     * How far along the gradient a point inside a box of this size sits, 0 at the start and 1 at the end.
     * A linear gradient measures the point against its axis, a radial one against the distance from the
     * middle out to the nearest corner, and a conic one against the way round from its start angle.
     */
    public float positionOf(float x, float y, int width, int height){
        float halfWidth = width / 2.0f;
        float halfHeight = height / 2.0f;
        float dx = x - halfWidth;
        float dy = y - halfHeight;

        return switch (kind) {
            case LINEAR -> {
                double radians = Math.toRadians(angle);
                float length = axisLength(width, height);
                // the axis points the way the angle names, with y counted downwards
                float along = (float) (dx * Math.sin(radians) - dy * Math.cos(radians));
                yield length <= 0 ? 0 : Math.clamp(along / length + 0.5f, 0.0f, 1.0f);
            }
            case RADIAL -> {
                float reach = radius(width, height);
                yield reach <= 0 ? 0 : Math.clamp((float) Math.sqrt(dx * dx + dy * dy) / reach, 0.0f, 1.0f);
            }
            case CONIC -> {
                // zero is straight up and it goes round clockwise, the same way the angles do
                float turn = (float) Math.toDegrees(Math.atan2(dx, -dy)) - angle;
                yield ((turn % 360) + 360) % 360 / 360.0f;
            }
        };
    }

    /** How long the gradient's axis is across a box of this size, which is what the stops are spread over. */
    public float axisLength(int width, int height){
        double radians = Math.toRadians(angle);
        return (float) (Math.abs(width * Math.sin(radians)) + Math.abs(height * Math.cos(radians)));
    }

    /** How far a radial gradient reaches: out to the corners, which is css's farthest-corner. */
    public float radius(int width, int height){
        return (float) Math.sqrt(width * width + height * height) / 2.0f;
    }

    /** Channel by channel, alpha included, so a stop can fade out. */
    private static int lerp(int a, int b, float t){
        int alpha = channel(a, 24, b, t);
        int red = channel(a, 16, b, t);
        int green = channel(a, 8, b, t);
        int blue = channel(a, 0, b, t);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int channel(int a, int shift, int b, float t){
        int from = (a >>> shift) & 0xFF;
        int to = (b >>> shift) & 0xFF;
        return Math.round(from + (to - from) * t);
    }

    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder(switch (kind) {
            case LINEAR -> "linear-gradient(" + trim(angle) + "deg";
            case RADIAL -> "radial-gradient(circle";
            case CONIC -> "conic-gradient(from " + trim(angle) + "deg";
        });
        for (Stop stop : stops()) {
            sb.append(", #").append(String.format("%08X", stop.color()));
            sb.append(" ").append(Math.round(stop.at() * 100)).append("%");
        }
        return sb.append(")").toString();
    }

    private static String trim(float value){
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
