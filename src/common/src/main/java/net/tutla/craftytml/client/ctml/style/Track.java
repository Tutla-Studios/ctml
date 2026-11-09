package net.tutla.craftytml.client.ctml.style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One row or column of a grid. A track is either a size (pixels, a percentage, a slice of the viewport),
 * a share of whatever room is left over (1fr, 2fr), or auto, where the content in it decides.
 */
public record Track(Length length, float fraction) {
    public static final Track AUTO = new Track(null, 0);

    public static Track fraction(float share){ return new Track(null, Math.max(0, share)); }
    public static Track of(Length length){ return new Track(length, 0); }

    public boolean isFraction(){ return fraction > 0; }
    public boolean isAuto(){ return length == null && fraction <= 0; }

    /** True when the track's size can be worked out without looking at what is in it. */
    public boolean isDefinite(){ return Length.isDefinite(length); }

    @Override
    public String toString(){
        if (isFraction()) return (fraction == Math.rint(fraction) ? String.valueOf((int) fraction) : String.valueOf(fraction)) + "fr";
        return length == null ? "auto" : length.toString();
    }

    /** A whole template: the tracks of a grid, in order. */
    public record List_(List<Track> tracks) {}

    /**
     * Reads a template the way css writes one: "1fr 2fr", "100px auto", "repeat(3, 1fr)".
     * Tailwind spells the spaces in an arbitrary value as underscores, so those count as spaces too.
     */
    public static List<Track> parse(String text){
        if (text == null) return List.of();
        List<Track> tracks = new ArrayList<>();
        String value = text.replace('_', ' ').strip();
        if (value.isEmpty()) return tracks;

        for (String part : split(value)) {
            if (part.startsWith("repeat(") && part.endsWith(")")) {
                String[] inside = part.substring(7, part.length() - 1).split(",", 2);
                Integer count = count(inside[0]);
                if (count == null || inside.length < 2) continue;
                for (Track repeated : parse(inside[1])) {
                    for (int i = 0; i < count; i++) tracks.add(repeated);
                }
                continue;
            }
            Track track = track(part);
            if (track != null) tracks.add(track);
        }

        return tracks;
    }

    /** One track: "1fr", "auto", "min-content", "100px", "50%". */
    public static Track track(String text){
        if (text == null) return null;
        String value = text.strip();
        if (value.isEmpty()) return null;
        if (value.equals("auto")) return AUTO;

        if (value.endsWith("fr")) {
            Integer whole = count(value.substring(0, value.length() - 2));
            if (whole != null) return fraction(whole);
            try {
                return fraction(Float.parseFloat(value.substring(0, value.length() - 2)));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        Length length = Length.parse(value);
        return length == null ? null : of(length);
    }

    /** As many of the same track as asked for, which is what grid-cols-3 means. */
    public static List<Track> repeat(int count, Track track){
        if (count <= 0) return List.of();
        return Collections.nCopies(count, track);
    }

    /** Splits on whitespace, but not inside the brackets of a repeat(). */
    private static List<String> split(String value){
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder part = new StringBuilder();

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '(') depth++;
            if (c == ')') depth--;
            if (Character.isWhitespace(c) && depth == 0) {
                if (!part.isEmpty()) parts.add(part.toString());
                part.setLength(0);
                continue;
            }
            part.append(c);
        }
        if (!part.isEmpty()) parts.add(part.toString());
        return parts;
    }

    private static Integer count(String value){
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
