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

    /** Reads #rrggbb or #rgb, with or without the hash, or returns -1 for anything else. */
    public static int parse(String text){
        if (text == null) return -1;
        String digits = text.strip();
        if (digits.startsWith("#")) digits = digits.substring(1);
        if (!digits.matches("[0-9a-fA-F]{3}|[0-9a-fA-F]{6}")) return -1;
        if (digits.length() == 3) {
            digits = "" + digits.charAt(0) + digits.charAt(0) + digits.charAt(1) + digits.charAt(1)
                    + digits.charAt(2) + digits.charAt(2);
        }
        return Integer.parseInt(digits, 16);
    }
}
