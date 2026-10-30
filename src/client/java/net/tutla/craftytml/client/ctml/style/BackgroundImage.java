package net.tutla.craftytml.client.ctml.style;

import java.util.ArrayList;
import java.util.List;

/**
 * A background image: which image, how big it is drawn, where it sits in the box and whether it
 * repeats. Where the tiles land is worked out here, away from anything that draws, so it can be
 * checked on its own.
 *
 * <p>source is an identifier a texture is found under ("craftytml:textures/gui/panel.png").
 * A width or a height spelled out wins over fit, which is what css does with background-size:
 * naming one is naming the size, and fit is only for the keywords.
 */
public record BackgroundImage(String source, Fit fit, Length width, Length height,
                              Length x, Length y, Repeat repeat) {

    /** How big the image comes out: as it is, as small as covers the box, or as big as fits inside it. */
    public enum Fit { AUTO, COVER, CONTAIN }

    public enum Repeat { REPEAT, REPEAT_X, REPEAT_Y, NO_REPEAT }

    /** A tile cannot be smaller than this, so a size of nothing cannot ask for endless tiles. */
    private static final int LEAST = 1;

    /** And however small it is, no more tiles are drawn than this. */
    public static final int MOST_TILES = 4096;

    /** Nothing said about any of it, which is where every background-* class starts from. */
    public static final BackgroundImage NONE =
            new BackgroundImage(null, Fit.AUTO, null, null, null, null, Repeat.REPEAT);

    public static BackgroundImage of(String source){
        return NONE.withSource(source);
    }

    public BackgroundImage withSource(String source){
        return new BackgroundImage(source, fit, width, height, x, y, repeat);
    }

    public BackgroundImage withFit(Fit fit){
        return new BackgroundImage(source, fit, null, null, x, y, repeat);
    }

    public BackgroundImage withSize(Length width, Length height){
        return new BackgroundImage(source, Fit.AUTO, width, height, x, y, repeat);
    }

    public BackgroundImage withPosition(Length x, Length y){
        return new BackgroundImage(source, fit, width, height, x, y, repeat);
    }

    public BackgroundImage withRepeat(Repeat repeat){
        return new BackgroundImage(source, fit, width, height, x, y, repeat);
    }

    /** Whether there is an image to draw at all; every other class can be set without one. */
    public boolean isDrawable(){
        return source != null && !source.isBlank();
    }

    /** One copy of the image on the screen, with the whole of the image drawn into it. */
    public record Tile(int x, int y, int width, int height) {
        public int right(){ return x + width; }
        public int bottom(){ return y + height; }
    }

    /**
     * How big one copy of the image comes out in the box, as { width, height }.
     * A size given for one axis alone keeps the image's own shape on the other, which is what
     * background-size: 100px auto does.
     */
    public int[] size(int areaWidth, int areaHeight, int imageWidth, int imageHeight,
                      int viewWidth, int viewHeight){
        if (imageWidth <= 0 || imageHeight <= 0) return new int[]{ 0, 0 };

        if (width != null || height != null) {
            int sized = width == null ? Length.UNRESOLVED
                    : width.resolve(areaWidth, viewWidth, viewHeight);
            int tall = height == null ? Length.UNRESOLVED
                    : height.resolve(areaHeight, viewWidth, viewHeight);

            if (sized == Length.UNRESOLVED && tall == Length.UNRESOLVED) {
                return new int[]{ imageWidth, imageHeight };
            }
            if (sized == Length.UNRESOLVED) sized = Math.round(tall * (float) imageWidth / imageHeight);
            if (tall == Length.UNRESOLVED) tall = Math.round(sized * (float) imageHeight / imageWidth);
            return new int[]{ Math.max(LEAST, sized), Math.max(LEAST, tall) };
        }

        if (fit == Fit.AUTO) return new int[]{ imageWidth, imageHeight };

        float across = (float) areaWidth / imageWidth;
        float down = (float) areaHeight / imageHeight;
        float scale = fit == Fit.COVER ? Math.max(across, down) : Math.min(across, down);
        return new int[]{ Math.max(LEAST, Math.round(imageWidth * scale)),
                Math.max(LEAST, Math.round(imageHeight * scale)) };
    }

    /**
     * Where the tiles of the image go over a box. A tile may hang over the edge; whoever draws it
     * cuts it to the box, since that is the only thing that knows the box's rounded corners.
     * The tiles come out in reading order, and a size small enough to ask for more than MOST_TILES
     * of them is drawn once rather than endlessly.
     */
    public List<Tile> tiles(int areaX, int areaY, int areaWidth, int areaHeight,
                            int imageWidth, int imageHeight, int viewWidth, int viewHeight){
        List<Tile> tiles = new ArrayList<>();
        if (areaWidth <= 0 || areaHeight <= 0) return tiles;

        int[] size = size(areaWidth, areaHeight, imageWidth, imageHeight, viewWidth, viewHeight);
        int wide = size[0];
        int tall = size[1];
        if (wide <= 0 || tall <= 0) return tiles;

        int originX = areaX + offset(x, areaWidth, wide, viewWidth, viewHeight);
        int originY = areaY + offset(y, areaHeight, tall, viewWidth, viewHeight);

        boolean across = repeat == Repeat.REPEAT || repeat == Repeat.REPEAT_X;
        boolean down = repeat == Repeat.REPEAT || repeat == Repeat.REPEAT_Y;
        if (across && down && (long) count(areaWidth, wide) * count(areaHeight, tall) > MOST_TILES) {
            across = false;
            down = false;
        } else if (across && count(areaWidth, wide) > MOST_TILES) {
            across = false;
        } else if (down && count(areaHeight, tall) > MOST_TILES) {
            down = false;
        }

        int firstX = across ? originX + Math.floorDiv(areaX - originX, wide) * wide : originX;
        int firstY = down ? originY + Math.floorDiv(areaY - originY, tall) * tall : originY;
        int lastX = areaX + areaWidth;
        int lastY = areaY + areaHeight;

        for (int at = firstY; at < lastY; at += tall) {
            if (at + tall > areaY) {
                for (int along = firstX; along < lastX; along += wide) {
                    if (along + wide > areaX) tiles.add(new Tile(along, at, wide, tall));
                    if (!across) break;
                }
            }
            if (!down) break;
        }
        return tiles;
    }

    /** How many tiles an axis takes, which is what keeps a tiny image from asking for thousands. */
    private static int count(int area, int tile){
        return area / Math.max(LEAST, tile) + 2;
    }

    /**
     * How far the first copy of the image sits from the box's own edge. A percentage lines that much
     * of the image up with that much of the box, which is how css places a background: 50% centres it
     * whatever size either of them is. Anything else is a straight distance from the edge.
     */
    private static int offset(Length length, int area, int image, int viewWidth, int viewHeight){
        if (length == null) return 0;
        if (length.isPercent()) return Math.round((area - image) * length.value() / 100.0f);
        return length.resolve(area, viewWidth, viewHeight, 0);
    }
}
