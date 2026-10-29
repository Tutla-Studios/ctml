package net.tutla.craftytml.client.ctml.style;

import java.util.ArrayList;
import java.util.List;

/**
 * A linear gradient, built up the way tailwind builds one: a direction class
 * ("bg-gradient-to-r") plus the stops that follow it ("from-red-500", "via-amber-400", "to-blue-500").
 * Each class lands on the same style key, so the value gets replaced rather than added to.
 * Nothing is drawable until there are at least two stops.
 */
public record Gradient(Direction direction, Integer from, Integer via, Integer to) {
    public enum Direction {
        TO_R, TO_L, TO_T, TO_B, TO_TR, TO_TL, TO_BR, TO_BL;

        /** Only the straight up and down ones run along y; the diagonals are drawn as horizontal. */
        public boolean isVertical(){
            return this == TO_T || this == TO_B;
        }

        /** True when the gradient starts at the far edge, so the ramp has to be read backwards. */
        public boolean isReversed(){
            return this == TO_L || this == TO_T || this == TO_TL || this == TO_BL;
        }

        public String cssName(){
            return "to " + name().substring(3).toLowerCase();
        }
    }

    /** What a bare direction class makes, before any stop has been named. */
    public static Gradient of(Direction direction){
        return new Gradient(direction, null, null, null);
    }

    public Gradient withDirection(Direction direction){ return new Gradient(direction, from, via, to); }
    public Gradient withFrom(int color){ return new Gradient(direction, color, via, to); }
    public Gradient withVia(int color){ return new Gradient(direction, from, color, to); }
    public Gradient withTo(int color){ return new Gradient(direction, from, via, color); }

    /** The named stops in order, which is what the ramp gets built out of. */
    public List<Integer> stops(){
        List<Integer> stops = new ArrayList<>(3);
        if (from != null) stops.add(from);
        if (via != null) stops.add(via);
        if (to != null) stops.add(to);
        return stops;
    }

    public boolean isDrawable(){
        return stops().size() >= 2;
    }

    /**
     * The colour somewhere along the gradient, 0 at the start edge and 1 at the end one.
     * The stops sit evenly spaced, which is what tailwind's from / via / to does.
     */
    public int colorAt(float t){
        List<Integer> stops = stops();
        if (stops.isEmpty()) return 0;
        if (stops.size() == 1) return stops.getFirst();

        float clamped = Math.max(0.0f, Math.min(1.0f, direction.isReversed() ? 1.0f - t : t));
        float scaled = clamped * (stops.size() - 1);
        int index = Math.min((int) scaled, stops.size() - 2);
        return lerp(stops.get(index), stops.get(index + 1), scaled - index);
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
        StringBuilder sb = new StringBuilder("linear-gradient(").append(direction.cssName());
        for (Integer stop : stops()) sb.append(", #").append(String.format("%08X", stop));
        return sb.append(")").toString();
    }
}
