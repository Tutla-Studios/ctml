import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.style.BackgroundImage;
import net.tutla.craftytml.client.ctml.style.Length;
import net.tutla.craftytml.client.ctml.style.TailwindParser;
import net.tutla.craftytml.client.render.CTMLTextures;

import java.util.List;

/** Background images: how big one comes out, where its copies land, and the classes that say so. */
public class BackgroundTest extends Harness {
    public static void main(String[] args){
        sizing();
        placing();
        tiling();
        classes();
        png();
        report();
    }

    static BackgroundImage image(String classes){
        return TailwindParser.parse(classes).get(ElementStyle.BACKGROUND_IMAGE);
    }

    /** The tiles of an image over a 100x60 box, as "x,y wxh" each, which is what the renderer blits. */
    static String tiles(BackgroundImage image, int imageWidth, int imageHeight){
        return tiles(image, 0, 0, 100, 60, imageWidth, imageHeight);
    }

    static String tiles(BackgroundImage image, int x, int y, int width, int height, int iw, int ih){
        StringBuilder out = new StringBuilder();
        for (BackgroundImage.Tile tile : image.tiles(x, y, width, height, iw, ih, 854, 480)) {
            if (!out.isEmpty()) out.append(" | ");
            out.append(tile.x()).append(",").append(tile.y())
                    .append(" ").append(tile.width()).append("x").append(tile.height());
        }
        return out.toString();
    }

    static String size(BackgroundImage image, int iw, int ih){
        int[] size = image.size(100, 60, iw, ih, 854, 480);
        return size[0] + "x" + size[1];
    }

    static void sizing(){
        section("how big the image comes out");
        BackgroundImage plain = BackgroundImage.of("mod:a.png");
        check("left alone it is its own size", size(plain, 32, 16), "32x16");

        // the box is 100x60, so covering it takes 100/32 across and 60/16 down: the bigger wins
        check("cover takes the bigger scale", size(plain.withFit(BackgroundImage.Fit.COVER), 32, 16), "120x60");
        check("contain takes the smaller", size(plain.withFit(BackgroundImage.Fit.CONTAIN), 32, 16), "100x50");
        check("cover a box the image is already wider than",
                size(plain.withFit(BackgroundImage.Fit.COVER), 200, 200), "100x100");
        check("and contain that one", size(plain.withFit(BackgroundImage.Fit.CONTAIN), 200, 200), "60x60");

        check("a size spelled out is that size",
                size(plain.withSize(Length.px(40), Length.px(10)), 32, 16), "40x10");
        check("as a share of the box it is a share of the box",
                size(plain.withSize(Length.percent(50), Length.percent(50)), 32, 16), "50x30");
        check("one axis auto keeps the image's own shape",
                size(plain.withSize(Length.px(64), Length.AUTO), 32, 16), "64x32");
        check("and the other way round",
                size(plain.withSize(Length.AUTO, Length.px(8)), 32, 16), "16x8");
        check("both auto is the image's own size",
                size(plain.withSize(Length.AUTO, Length.AUTO), 32, 16), "32x16");
        check("a size wins over a fit",
                size(plain.withFit(BackgroundImage.Fit.COVER).withSize(Length.px(8), Length.px(8)), 32, 16), "8x8");
        check("and a fit after a size drops it",
                size(plain.withSize(Length.px(8), Length.px(8)).withFit(BackgroundImage.Fit.CONTAIN), 32, 16), "100x50");
        check("an image with no size to it comes out as nothing", size(plain, 0, 0), "0x0");
        check("a size that rounds to nothing is still a pixel",
                size(plain.withSize(Length.percent(0), Length.percent(0)), 32, 16), "1x1");
    }

    static void placing(){
        section("where the first copy sits");
        BackgroundImage once = BackgroundImage.of("mod:a.png").withRepeat(BackgroundImage.Repeat.NO_REPEAT);
        check("it starts at the corner", tiles(once, 20, 10), "0,0 20x10");
        check("a distance moves it along",
                tiles(once.withPosition(Length.px(5), Length.px(7)), 20, 10), "5,7 20x10");

        // a percentage lines that much of the image up with that much of the box: 100x60 box, 20x10 image
        check("centred is halfway into the room left over",
                tiles(once.withPosition(Length.percent(50), Length.percent(50)), 20, 10), "40,25 20x10");
        check("all the way is against the far corner",
                tiles(once.withPosition(Length.percent(100), Length.percent(100)), 20, 10), "80,50 20x10");
        check("and none of the way is back at the near one",
                tiles(once.withPosition(Length.percent(0), Length.percent(0)), 20, 10), "0,0 20x10");
        check("an image bigger than the box centres by hanging over both sides",
                tiles(once.withPosition(Length.percent(50), Length.percent(50)), 120, 80), "-10,-10 120x80");
        check("the box's own corner is where it is measured from",
                tiles(once.withPosition(Length.percent(50), Length.percent(50)), 30, 40, 100, 60, 20, 10),
                "70,65 20x10");
    }

    static void tiling(){
        section("repeating");
        BackgroundImage image = BackgroundImage.of("mod:a.png"); // repeat is what css starts with
        check("a 50x30 image fills a 100x60 box four times", tiles(image, 50, 30),
                "0,0 50x30 | 50,0 50x30 | 0,30 50x30 | 50,30 50x30");
        check("one that does not divide evenly runs over the edge",
                tiles(image, 60, 60), "0,0 60x60 | 60,0 60x60");
        check("across only is one row", tiles(image.withRepeat(BackgroundImage.Repeat.REPEAT_X), 50, 30),
                "0,0 50x30 | 50,0 50x30");
        check("down only is one column", tiles(image.withRepeat(BackgroundImage.Repeat.REPEAT_Y), 50, 30),
                "0,0 50x30 | 0,30 50x30");
        check("and no repeat is the one copy",
                tiles(image.withRepeat(BackgroundImage.Repeat.NO_REPEAT), 50, 30), "0,0 50x30");

        // a tiled background starts from the position and steps back to cover the near edge as well
        check("tiling steps back over the edge to reach it",
                tiles(image.withPosition(Length.px(10), Length.px(10)), 50, 50),
                "-40,-40 50x50 | 10,-40 50x50 | 60,-40 50x50 | -40,10 50x50 | 10,10 50x50 | 60,10 50x50");

        check("nothing at all for a box with no room in it", tiles(image, 0, 0, 0, 10, 20, 20), "");
        check("or for an image with no size", tiles(image, 0, 0), "");

        // a tile a pixel across would be thousands of draws, so past the cap it is drawn once
        List<BackgroundImage.Tile> many = image.tiles(0, 0, 4000, 4000, 1, 1, 854, 480);
        check("an image too small to tile sensibly is drawn once", many.size(), 1);
        check("at its own size", many.getFirst().width() + "x" + many.getFirst().height(), "1x1");
    }

    static void classes(){
        section("the classes");
        check("bg-[url(...)] names the image", image("bg-[url(mod:ui/panel.png)]").source(), "mod:ui/panel.png");
        check("so does bg-[image:...]", image("bg-[image:mod:ui/panel.png]").source(), "mod:ui/panel.png");
        check("quotes inside the url come off", image("bg-[url('mod:a.png')]").source(), "mod:a.png");
        check("a colour is still a colour", image("bg-red-500"), null);
        check("and the colour still lands",
                TailwindParser.parse("bg-red-500").get(ElementStyle.BACKGROUND_COLOR) != null, true);
        check("a colour and an image together both land",
                TailwindParser.parse("bg-red-500 bg-[url(mod:a.png)]").get(ElementStyle.BACKGROUND_COLOR) != null, true);

        check("bg-cover", image("bg-[url(mod:a.png)] bg-cover").fit(), BackgroundImage.Fit.COVER);
        check("bg-contain", image("bg-[url(mod:a.png)] bg-contain").fit(), BackgroundImage.Fit.CONTAIN);
        check("bg-auto", image("bg-[url(mod:a.png)] bg-auto").fit(), BackgroundImage.Fit.AUTO);
        check("bg-no-repeat", image("bg-[url(mod:a.png)] bg-no-repeat").repeat(), BackgroundImage.Repeat.NO_REPEAT);
        check("bg-repeat-x", image("bg-[url(mod:a.png)] bg-repeat-x").repeat(), BackgroundImage.Repeat.REPEAT_X);
        check("bg-repeat-y", image("bg-[url(mod:a.png)] bg-repeat-y").repeat(), BackgroundImage.Repeat.REPEAT_Y);
        check("repeating is what it starts as", image("bg-[url(mod:a.png)]").repeat(), BackgroundImage.Repeat.REPEAT);

        check("bg-center", image("bg-center").x() + " " + image("bg-center").y(), "50% 50%");
        check("bg-top", image("bg-top").x() + " " + image("bg-top").y(), "50% 0%");
        check("bg-bottom", image("bg-bottom").x() + " " + image("bg-bottom").y(), "50% 100%");
        check("bg-left", image("bg-left").x() + " " + image("bg-left").y(), "0% 50%");
        check("bg-right", image("bg-right").x() + " " + image("bg-right").y(), "100% 50%");
        check("bg-bottom-right", image("bg-bottom-right").x() + " " + image("bg-bottom-right").y(), "100% 100%");
        check("and tailwind 3's way round", image("bg-right-bottom").x() + " " + image("bg-right-bottom").y(), "100% 100%");

        check("bg-size-[64px_32px]", image("bg-size-[64px_32px]").width() + " " + image("bg-size-[64px_32px]").height(),
                "64px 32px");
        check("one value stands for both", image("bg-size-[24px]").height(), Length.px(24));
        check("bg-size-[50%_auto]", image("bg-size-[50%_auto]").height(), Length.AUTO);
        check("bg-position-[10px_20px]",
                image("bg-position-[10px_20px]").x() + " " + image("bg-position-[10px_20px]").y(), "10px 20px");

        section("and what they will not take");
        check("an empty url is no image", TailwindParser.parseInto(ElementStyle.builder(), "bg-[url()]").size(), 1);
        check("nor is a size that is not one",
                String.valueOf(TailwindParser.parseInto(ElementStyle.builder(), "bg-size-[wide]")),
                "[bg-size-[wide] (no value background-image can take)]");
        check("nor three lengths",
                TailwindParser.parseInto(ElementStyle.builder(), "bg-position-[1px_2px_3px]").size(), 1);
        check("a good one is not reported",
                TailwindParser.parseInto(ElementStyle.builder(), "bg-size-[12px] bg-cover bg-top").size(), 0);
        check("the classes merge whatever order they come in",
                image("bg-cover bg-no-repeat bg-[url(mod:a.png)]").source()
                        + " " + image("bg-cover bg-no-repeat bg-[url(mod:a.png)]").fit(), "mod:a.png COVER");
    }

    /** The size is read off the front of the png, since nothing on the gui side will say. */
    static void png(){
        section("reading a png's size");
        check("the header says how big it is", java.util.Arrays.toString(CTMLTextures.size(header(64, 32))),
                "[64, 32]");
        check("a file that is not a png says nothing", CTMLTextures.size(new byte[24]), null);
        check("nor does one cut off before the size", CTMLTextures.size(java.util.Arrays.copyOf(header(64, 32), 20)), null);

        byte[] notHeaderChunk = header(64, 32);
        notHeaderChunk[12] = 'g'; // gAMA where IHDR has to be
        check("nor one whose first chunk is not the header", CTMLTextures.size(notHeaderChunk), null);

        check("nor one that claims no size at all", CTMLTextures.size(header(0, 0)), null);
        check("a big one is read as written", java.util.Arrays.toString(CTMLTextures.size(header(4096, 2048))),
                "[4096, 2048]");
    }

    /** The first 24 bytes of a png that is w by h: the signature, the header chunk, and the two sizes. */
    static byte[] header(int width, int height){
        byte[] bytes = new byte[24];
        byte[] signature = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };
        System.arraycopy(signature, 0, bytes, 0, 8);
        bytes[11] = 13; // the header chunk is thirteen bytes long
        bytes[12] = 'I'; bytes[13] = 'H'; bytes[14] = 'D'; bytes[15] = 'R';
        for (int i = 0; i < 4; i++) {
            bytes[16 + i] = (byte) (width >>> (24 - 8 * i));
            bytes[20 + i] = (byte) (height >>> (24 - 8 * i));
        }
        return bytes;
    }
}
