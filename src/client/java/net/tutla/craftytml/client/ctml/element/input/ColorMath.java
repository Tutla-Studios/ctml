package net.tutla.craftytml.client.ctml.element.input;

/**
 * The few conversions a colour picker needs between red, green and blue and hue, saturation and
 * brightness. Hue, saturation and brightness are each a share from nothing to one; a colour is 0xRRGGBB.
 */
public final class ColorMath {
    private ColorMath(){}

    /** The colour for a hue, saturation and brightness. A hue of one is the same as a hue of nothing. */
    public static int hsvToRgb(float hue, float saturation, float brightness){
        float h = (hue - (float) Math.floor(hue)) * 6.0f;
        float s = Math.clamp(saturation, 0.0f, 1.0f);
        float v = Math.clamp(brightness, 0.0f, 1.0f);
        int sector = (int) h;
        float fraction = h - sector;
        float p = v * (1 - s);
        float q = v * (1 - s * fraction);
        float t = v * (1 - s * (1 - fraction));
        float r, g, b;
        switch (sector) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    /** { hue, saturation, brightness } of a colour. A grey has no hue to speak of, so its hue comes back as nothing. */
    public static float[] rgbToHsv(int rgb){
        float r = ((rgb >> 16) & 0xFF) / 255.0f;
        float g = ((rgb >> 8) & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;

        float hue = 0.0f;
        if (delta > 0.0f) {
            if (max == r) hue = ((g - b) / delta) % 6.0f;
            else if (max == g) hue = (b - r) / delta + 2.0f;
            else hue = (r - g) / delta + 4.0f;
            hue /= 6.0f;
            if (hue < 0.0f) hue += 1.0f;
        }
        return new float[]{ hue, max == 0.0f ? 0.0f : delta / max, max };
    }

    /** A colour as the text a colour input holds: a hash and six lower case digits. */
    public static String hex(int rgb){
        return String.format("#%06x", rgb & 0xFFFFFF);
    }

    /** A colour with an opacity as text: a hash and eight lower case digits, the last two the opacity. */
    public static String hex(int rgb, int alpha){
        return String.format("#%06x%02x", rgb & 0xFFFFFF, Math.clamp(alpha, 0, 255));
    }

    /** The digits of a colour written #rgb, #rgba, #rrggbb or #rrggbbaa, widened to eight, or null for anything else. */
    private static String digits(String text){
        if (text == null) return null;
        String digits = text.strip();
        if (digits.startsWith("#")) digits = digits.substring(1);
        if (!digits.matches("[0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8}")) return null;
        if (digits.length() <= 4) {
            StringBuilder wide = new StringBuilder();
            for (char each : digits.toCharArray()) wide.append(each).append(each);
            digits = wide.toString();
        }
        return digits.length() == 6 ? digits + "ff" : digits;
    }

    /** Reads #rgb, #rgba, #rrggbb or #rrggbbaa, with or without the hash, and gives the colour without its opacity, or -1 for anything else. */
    public static int parse(String text){
        String digits = digits(text);
        return digits == null ? -1 : Integer.parseInt(digits.substring(0, 6), 16);
    }

    /** The opacity of a colour written as text, from 0 to 255, which is fully opaque where it was written without one. */
    public static int parseAlpha(String text){
        String digits = digits(text);
        return digits == null ? 255 : Integer.parseInt(digits.substring(6, 8), 16);
    }
}
