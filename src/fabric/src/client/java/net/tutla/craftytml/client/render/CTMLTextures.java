package net.tutla.craftytml.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The one place an image name turns into a texture and a size. Both the things that draw a picture go
 * through it: the background of a box and the <img> element, so they agree about what a name means,
 * what happens to one nothing holds, and how often the packs are asked.
 *
 * <p>A background has to know the image's own size before it can cover a box with it or tile it, and
 * nothing on the gui side will say: a texture is handed to the gpu by name and never measured. So the
 * size is read straight off the front of the png, where the first chunk carries it, and kept, since a
 * texture does not change size while the game is up.
 */
public final class CTMLTextures {
    /** A png opens with these eight bytes, and nothing else does. */
    private static final byte[] SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };

    /** The signature, the first chunk's length and its name, then the width and the height. */
    private static final int HEADER = 24;

    /** A texture that was asked for and found, with the size the png says it is. */
    public record Texture(Identifier id, int width, int height) {
        /** Whether the size is known, which it is not for an image no pack holds. */
        public boolean isMeasured(){ return width > 0 && height > 0; }
    }

    private static final Map<String, Texture> KNOWN = new HashMap<>();

    /** How a size is found, so a test or another host can answer without a running game. */
    public interface Sizes {
        /** The { width, height } of an image, or null where nothing holds it. */
        int[] sizeOf(Identifier id);
    }

    private static Sizes sizes = CTMLTextures::fromPacks;

    private CTMLTextures(){}

    /** Swaps out where sizes come from, and forgets the ones found the old way. */
    public static void sizedBy(Sizes lookup){
        sizes = lookup == null ? CTMLTextures::fromPacks : lookup;
        forget();
    }

    /**
     * The texture an image name stands for, or null where the name is not one an identifier can be
     * made of. A name no pack holds still comes back, with no size to it: drawing it shows minecraft's
     * own missing texture, which says more about what went wrong than drawing nothing at all.
     */
    public static Texture of(String source){
        if (source == null || source.isBlank()) return null;
        Texture known = KNOWN.get(source);
        if (known != null) return known;

        Identifier id = Identifier.tryParse(source.strip());
        if (id == null) return null;

        int[] size = sizes.sizeOf(id);
        Texture texture = new Texture(id, size == null ? 0 : size[0], size == null ? 0 : size[1]);
        KNOWN.put(source, texture);
        return texture;
    }

    /** Forgets every size, for a resource reload, where an image may well have come out a new size. */
    public static void forget(){
        KNOWN.clear();
    }

    /** The size off the png in whatever pack holds it, which is where it comes from in a running game. */
    private static int[] fromPacks(Identifier id){
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getResourceManager() == null) return null;

        Optional<Resource> resource = minecraft.getResourceManager().getResource(id);
        if (resource.isEmpty()) return null;

        try (InputStream stream = resource.get().open()) {
            return size(stream.readNBytes(HEADER));
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * The size a png says it is, off the front of the file: its header chunk comes first by the spec,
     * and carries the width and the height as the two big endian ints at its front.
     * Null for anything that is not a png, since nothing else can be read this cheaply.
     */
    public static int[] size(byte[] header){
        if (header == null || header.length < HEADER) return null;
        for (int i = 0; i < SIGNATURE.length; i++) if (header[i] != SIGNATURE[i]) return null;
        if (header[12] != 'I' || header[13] != 'H' || header[14] != 'D' || header[15] != 'R') return null;

        int width = int32(header, 16);
        int height = int32(header, 20);
        return width > 0 && height > 0 ? new int[]{ width, height } : null;
    }

    private static int int32(byte[] bytes, int at){
        return ((bytes[at] & 0xFF) << 24) | ((bytes[at + 1] & 0xFF) << 16)
                | ((bytes[at + 2] & 0xFF) << 8) | (bytes[at + 3] & 0xFF);
    }
}
