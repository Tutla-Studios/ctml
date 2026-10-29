package net.tutla.craftytml.client.ctml.style;

import java.util.HashMap;
import java.util.Map;

/**
 * The tailwind v3 default palette, as opaque ARGB ints.
 * Keys look the way they do in a class list, so "red-500" or "white".
 */
public final class TailwindColors {
    private static final Map<String, Integer> COLORS = new HashMap<>();

    private static final int[] SHADES = { 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950 };

    static {
        ramp("slate",   "f8fafc","f1f5f9","e2e8f0","cbd5e1","94a3b8","64748b","475569","334155","1e293b","0f172a","020617");
        ramp("gray",    "f9fafb","f3f4f6","e5e7eb","d1d5db","9ca3af","6b7280","4b5563","374151","1f2937","111827","030712");
        ramp("zinc",    "fafafa","f4f4f5","e4e4e7","d4d4d8","a1a1aa","71717a","52525b","3f3f46","27272a","18181b","09090b");
        ramp("neutral", "fafafa","f5f5f5","e5e5e5","d4d4d4","a3a3a3","737373","525252","404040","262626","171717","0a0a0a");
        ramp("stone",   "fafaf9","f5f5f4","e7e5e4","d6d3d1","a8a29e","78716c","57534e","44403c","292524","1c1917","0c0a09");
        ramp("red",     "fef2f2","fee2e2","fecaca","fca5a5","f87171","ef4444","dc2626","b91c1c","991b1b","7f1d1d","450a0a");
        ramp("orange",  "fff7ed","ffedd5","fed7aa","fdba74","fb923c","f97316","ea580c","c2410c","9a3412","7c2d12","431407");
        ramp("amber",   "fffbeb","fef3c7","fde68a","fcd34d","fbbf24","f59e0b","d97706","b45309","92400e","78350f","451a03");
        ramp("yellow",  "fefce8","fef9c3","fef08a","fde047","facc15","eab308","ca8a04","a16207","854d0e","713f12","422006");
        ramp("lime",    "f7fee7","ecfccb","d9f99d","bef264","a3e635","84cc16","65a30d","4d7c0f","3f6212","365314","1a2e05");
        ramp("green",   "f0fdf4","dcfce7","bbf7d0","86efac","4ade80","22c55e","16a34a","15803d","166534","14532d","052e16");
        ramp("emerald", "ecfdf5","d1fae5","a7f3d0","6ee7b7","34d399","10b981","059669","047857","065f46","064e3b","022c22");
        ramp("teal",    "f0fdfa","ccfbf1","99f6e4","5eead4","2dd4bf","14b8a6","0d9488","0f766e","115e59","134e4a","042f2e");
        ramp("cyan",    "ecfeff","cffafe","a5f3fc","67e8f9","22d3ee","06b6d4","0891b2","0e7490","155e75","164e63","083344");
        ramp("sky",     "f0f9ff","e0f2fe","bae6fd","7dd3fc","38bdf8","0ea5e9","0284c7","0369a1","075985","0c4a6e","082f49");
        ramp("blue",    "eff6ff","dbeafe","bfdbfe","93c5fd","60a5fa","3b82f6","2563eb","1d4ed8","1e40af","1e3a8a","172554");
        ramp("indigo",  "eef2ff","e0e7ff","c7d2fe","a5b4fc","818cf8","6366f1","4f46e5","4338ca","3730a3","312e81","1e1b4b");
        ramp("violet",  "f5f3ff","ede9fe","ddd6fe","c4b5fd","a78bfa","8b5cf6","7c3aed","6d28d9","5b21b6","4c1d95","2e1065");
        ramp("purple",  "faf5ff","f3e8ff","e9d5ff","d8b4fe","c084fc","a855f7","9333ea","7e22ce","6b21a8","581c87","3b0764");
        ramp("fuchsia", "fdf4ff","fae8ff","f5d0fe","f0abfc","e879f9","d946ef","c026d3","a21caf","86198f","701a75","4a044e");
        ramp("pink",    "fdf2f8","fce7f3","fbcfe8","f9a8d4","f472b6","ec4899","db2777","be185d","9d174d","831843","500724");
        ramp("rose",    "fff1f2","ffe4e6","fecdd3","fda4af","fb7185","f43f5e","e11d48","be123c","9f1239","881337","4c0519");

        COLORS.put("black", 0xFF000000);
        COLORS.put("white", 0xFFFFFFFF);
        COLORS.put("transparent", 0x00000000);
        COLORS.put("current", 0xFFFFFFFF); // nothing to pick up a current color from, so plain white
    }

    private TailwindColors(){}

    private static void ramp(String family, String... hexes){
        for (int i = 0; i < hexes.length; i++) {
            COLORS.put(family + "-" + SHADES[i], 0xFF000000 | Integer.parseInt(hexes[i], 16));
        }
    }

    /** The palette name ("red-500") or an arbitrary value ("[#ff00aa]"), with an optional "/50" alpha on either. */
    public static Integer parse(String value){
        String name = value;
        Integer alpha = null;

        int slash = name.lastIndexOf('/');
        if (slash > 0) {
            alpha = percent(name.substring(slash + 1));
            if (alpha == null) return null;
            name = name.substring(0, slash);
        }

        Integer color = name.startsWith("[") && name.endsWith("]")
                ? hex(name.substring(1, name.length() - 1))
                : lookup(name);

        if (color == null || alpha == null) return color;
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /** "red-500", or just "red" for the family's middle shade. */
    private static Integer lookup(String name){
        Integer exact = COLORS.get(name);
        return exact != null ? exact : COLORS.get(name + "-500");
    }

    public static Integer get(String name){
        return COLORS.get(name);
    }

    private static Integer percent(String value){
        try {
            return Math.clamp(Math.round(Integer.parseInt(value) * 255 / 100.0f), 0, 255);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer hex(String value){
        if (!value.startsWith("#")) return null;
        String digits = value.substring(1);

        if (digits.length() == 3 || digits.length() == 4) { // short form, every digit doubled up
            StringBuilder expanded = new StringBuilder();
            for (char c : digits.toCharArray()) expanded.append(c).append(c);
            digits = expanded.toString();
        }

        try {
            long parsed = Long.parseLong(digits, 16);
            if (digits.length() == 6) return 0xFF000000 | (int) parsed; // rrggbb
            if (digits.length() == 8) return (int) ((parsed >>> 8) | (parsed << 24)); // rrggbbaa, alpha moves to the front
        } catch (NumberFormatException ignored) {}

        return null;
    }
}
