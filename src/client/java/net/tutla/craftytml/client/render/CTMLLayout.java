package net.tutla.craftytml.client.render;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.element.markdown.Img;
import net.tutla.craftytml.client.ctml.element.markdown.paragraph.LineBreak;
import net.tutla.craftytml.client.ctml.element.markdown.list.ListItem;
import net.tutla.craftytml.client.ctml.property.Reversed;
import net.tutla.craftytml.client.ctml.property.Start;
import net.tutla.craftytml.client.ctml.property.Value;
import net.tutla.craftytml.client.ctml.style.Length;
import net.tutla.craftytml.client.ctml.style.Track;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.dom.DOMTreeNode;
import net.tutla.craftytml.client.dom.DOMTreeText;

import java.text.Bidi;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Works out where everything sits before anything gets drawn: boxes, line boxes and text runs.
 * A cut down css box model, so margin / border / padding / width / height, block flow with collapsing
 * margins, an inline flow with real line boxes, and flexbox with order, basis, grow, shrink, wrapping
 * and the alignment properties. Percentages are measured against whatever contains the element and the
 * viewport sits behind the vw and vh units.
 */
public final class CTMLLayout {
    /** What minecraft's font counts as, so font-size can be read as a scale against it. */
    public static final int BASE_FONT_SIZE = 16;

    /** Where the baseline sits inside a line of minecraft's own text. */
    public static final int FONT_ASCENT = 7;

    /** A box with no text anywhere in it has no baseline for anything to line up against. */
    public static final int NO_BASELINE = Integer.MIN_VALUE;

    /** Handed to the layout of a child when flex has worked its size out and the style must not win. */
    private static final int UNFORCED = -1;

    /**
     * A laid out element: the border box, the content box inside it, and what goes in there.
     * offsetX / offsetY is how far the box was moved out of the spot the flow gave it, by a drag, by
     * position relative or by sticking to a scrollport. The flow has to be able to find that spot again,
     * since a box that moved must not drag the layout around it along with it.
     * baseline is how far under the box's top edge its first line of text sits, for the alignment that
     * lines boxes up by their text rather than by their edges.
     */
    public record Box(
            DOMTreeElement element,
            ElementStyle style,
            int x, int y, int width, int height,
            int contentX, int contentY, int contentWidth, int contentHeight,
            int offsetX, int offsetY,
            Port port,
            int baseline,
            List<TextLine> lines,
            List<Box> children
    ) {
        public int right(){ return x + width; }
        public int bottom(){ return y + height; }
        public int contentRight(){ return contentX + contentWidth; }
        public int contentBottom(){ return contentY + contentHeight; }

        /** Where the flow put the box, with any offset taken back off again. */
        public int flowX(){ return x - offsetX; }
        public int flowY(){ return y - offsetY; }
        public int flowRight(){ return right() - offsetX; }

        public boolean clipsX(){ return CTMLScroll.clips(CTMLScroll.overflowX(style)); }
        public boolean clipsY(){ return CTMLScroll.clips(CTMLScroll.overflowY(style)); }

        /** Whether the user is allowed to move the content along an axis, bar or wheel. */
        public boolean userScrollsX(){ return CTMLScroll.userScrolls(CTMLScroll.overflowX(style)); }
        public boolean userScrollsY(){ return CTMLScroll.userScrolls(CTMLScroll.overflowY(style)); }

        /** A fixed box is measured against the viewport, so nothing above it may move it. */
        public boolean isFixed(){ return style.get(ElementStyle.POSITION) == ElementStyle.Position.FIXED; }
    }

    /**
     * The window a box shows its content through: the padding box, less whatever room the scrollbars
     * were given, plus how far the content reaches past it and how far it has been scrolled.
     * The scroll offset is already taken off the children, so their coordinates need no adjusting.
     */
    public record Port(
            int x, int y, int width, int height,
            int scrollWidth, int scrollHeight,
            int scrollX, int scrollY,
            int gutterRight, int gutterBottom,
            int gutterLeft, int gutterTop
    ) {
        /** A box with nothing to scroll and no room kept for a bar, which is most of them. */
        static Port still(int x, int y, int width, int height){
            return new Port(x, y, width, height, width, height, 0, 0, 0, 0, 0, 0);
        }

        public int right(){ return x + width; }
        public int bottom(){ return y + height; }
        public int maxScrollX(){ return Math.max(0, scrollWidth - width); }
        public int maxScrollY(){ return Math.max(0, scrollHeight - height); }

        Port shifted(int dx, int dy){
            return new Port(x + dx, y + dy, width, height, scrollWidth, scrollHeight,
                    scrollX, scrollY, gutterRight, gutterBottom, gutterLeft, gutterTop);
        }
    }

    /**
     * One drawn line of text, already placed against its box's content corner and already scaled for its
     * font size. content is the same text as a plain string, for the drawing that has to go glyph by glyph.
     * spacing is the letter-spacing the line was measured with, which the drawing has to put back between
     * the glyphs; it is counted after every one of them, the last included, the way css does.
     */
    public record TextLine(FormattedCharSequence text, String content, int x, int y, int width, float scale,
                           int spacing) {}

    /** Everything layout needs to ask about text, so the measuring can be swapped out. */
    public interface TextMetrics {
        int width(String text);
        int width(FormattedCharSequence text);
        int lineHeight();

        /** How far under the top of a line the baseline sits. */
        default int ascent(){ return Math.max(1, lineHeight() - 2); }
    }

    public record FontMetrics(Font font) implements TextMetrics {
        @Override public int width(String text){ return font.width(text); }
        @Override public int width(FormattedCharSequence text){ return font.width(text); }
        @Override public int lineHeight(){ return font.lineHeight; }
        @Override public int ascent(){ return FONT_ASCENT; }
    }

    private final TextMetrics metrics;
    private final int viewWidth;
    private final int viewHeight;

    /**
     * The viewport everything is being measured against, as one number an element can keep hold of,
     * so a window resize or a gui scale change can be noticed next time it is laid out. Never zero,
     * which is what an element says when it has not been measured at all.
     */
    private long viewKey(){
        return ((long) (viewWidth + 1) << 32) | (viewHeight + 1);
    }

    /**
     * Intrinsic widths worked out for an element, kept for as long as nothing in its subtree changes.
     * Flex, grid and shrink to fit all ask for the same ones over and over, and measuring walks a whole
     * subtree, so this is what keeps a change in one corner of the screen from measuring the rest of it.
     * The style is part of the key, since what an element inherits changes what its content measures.
     */
    private static final class Sizes {
        long revision = Long.MIN_VALUE;
        final Map<Measured, Intrinsic> widths = new HashMap<>();
        final Map<Measured, Integer> minimums = new HashMap<>();

        /** Everything measured before is only good while the subtree it was measured from stands still. */
        Sizes at(long revision){
            if (this.revision != revision) {
                this.revision = revision;
                widths.clear();
                minimums.clear();
            }
            return this;
        }
    }

    private static final Map<DOMTreeElement, Sizes> SIZES = new WeakHashMap<>();

    private record Measured(int basis, ElementStyle style) {}

    /**
     * Everything the layout of an element depends on bar where it was put: the style it inherits, the
     * room it was given, the size it was told to be, the viewport behind the vw and vh units, and the
     * room kept for its bars, which tells the two passes of a box that scrolls apart.
     */
    private record CacheKey(ElementStyle parentStyle, int cbWidth, int cbHeight, int forcedWidth,
                            int forcedHeight, boolean topMarginTaken, boolean root,
                            int viewWidth, int viewHeight, Gutters gutters) {}

    private record Cached(int x, int y, Box box) {}

    /**
     * Boxes worked out on an earlier pass, kept for as long as nothing in the element's subtree changes.
     * There is room for more than one answer per element, since the same element is often asked twice in
     * a pass: flex measures an item loose before telling it the size it settled on.
     */
    private static final class Boxes {
        long revision = Long.MIN_VALUE;
        final Map<CacheKey, Cached> entries = new HashMap<>();

        Boxes at(long revision){
            if (this.revision != revision) {
                this.revision = revision;
                entries.clear();
            }
            return this;
        }
    }

    /** Weakly held, so an element that leaves the tree takes its boxes with it. */
    private static final Map<DOMTreeElement, Boxes> REUSABLE = new WeakHashMap<>();
    private record Intrinsic(int min, int max) {}

    /**
     * One float, as far as everything around it is concerned: which side it was pulled to, how far down
     * it reaches, and the edge the content beside it has to keep clear of. All in screen pixels, so a box
     * nested any number of levels down can ask about it without working anything out.
     */
    private record Band(boolean right, int top, int bottom, int edge) {}

    /**
     * The floats a block and everything in it has to make room for. Lines ask it where they may start and
     * where they have to stop, for the band of the page they happen to be on.
     */
    private record Bands(List<Band> items) {
        static final Bands EMPTY = new Bands(List.of());

        boolean isEmpty(){ return items.isEmpty(); }

        Bands with(Band band){
            List<Band> all = new ArrayList<>(items);
            all.add(band);
            return new Bands(all);
        }

        /** How far right the content has to start, given what is floated to the left of it. */
        int left(int y, int height, int fallback){
            int edge = fallback;
            for (Band band : items) {
                if (band.right() || !overlaps(band, y, height)) continue;
                edge = Math.max(edge, band.edge());
            }
            return edge;
        }

        /** And where it has to stop, given what is floated to the right. */
        int right(int y, int height, int fallback){
            int edge = fallback;
            for (Band band : items) {
                if (!band.right() || !overlaps(band, y, height)) continue;
                edge = Math.min(edge, band.edge());
            }
            return edge;
        }

        /** How far down the floats on a side reach, which is what clear has to get past. */
        int bottom(boolean rightSide, boolean bothSides){
            int bottom = Integer.MIN_VALUE;
            for (Band band : items) {
                if (!bothSides && band.right() != rightSide) continue;
                bottom = Math.max(bottom, band.bottom());
            }
            return bottom;
        }

        private static boolean overlaps(Band band, int y, int height){
            return y < band.bottom() && y + Math.max(1, height) > band.top();
        }
    }

    /** What a flow came out as: how tall it was, and where its first line of text sits inside it. */
    private record Flow(int height, int baseline) {}

    /** What a line box came out as, in the same pixels the boxes are placed in. */
    private record Placed(int height, int baselineY) {}

    public CTMLLayout(Font font, int viewWidth, int viewHeight){
        this(new FontMetrics(font), viewWidth, viewHeight);
    }

    public CTMLLayout(TextMetrics metrics, int viewWidth, int viewHeight){
        this.metrics = metrics;
        this.viewWidth = viewWidth;
        this.viewHeight = viewHeight;
    }

    /**
     * Whether a box lines its own content up inside itself. A block container does, since css's
     * align-content reaches them; the layouts that align their content as they place it do not, and
     * neither does a multi column block, whose columns are already as tall as the box.
     */
    private static boolean aligns(ElementStyle.Display display, boolean columns){
        if (columns) return false;
        return switch (display) {
            case BLOCK, INLINE_BLOCK, LIST_ITEM, TABLE_CELL, TABLE_CAPTION -> true;
            default -> false;
        };
    }

    /** The screen is the root's containing block, so unless it says otherwise the root box is the screen. */
    private static final ElementStyle ROOT_DEFAULTS = ElementStyle.builder()
            .set(ElementStyle.WIDTH, Length.FULL)
            .set(ElementStyle.HEIGHT, Length.FULL)
            .build();

    public Box layout(DOMTreeElement element, int x, int y, int availableWidth, int availableHeight){
        return layout(element, null, x, y, availableWidth, availableHeight, true, UNFORCED, UNFORCED, false, Bands.EMPTY);
    }

    private Box layout(DOMTreeElement element, ElementStyle parentStyle, int x, int y, int cbWidth, int cbHeight){
        return layout(element, parentStyle, x, y, cbWidth, cbHeight, false, UNFORCED, UNFORCED, false, Bands.EMPTY);
    }

    private Box layout(DOMTreeElement element, ElementStyle parentStyle, int x, int y, int cbWidth, int cbHeight,
                       boolean root, int forcedWidth, int forcedHeight, boolean topMarginTaken){
        return layout(element, parentStyle, x, y, cbWidth, cbHeight, root, forcedWidth, forcedHeight,
                topMarginTaken, Bands.EMPTY);
    }

    /**
     * forcedWidth / forcedHeight are border box sizes flex has already worked out for an item, which beat
     * anything the style says, since the style is what flex measured in the first place.
     */
    private Box layout(DOMTreeElement element, ElementStyle parentStyle, int x, int y, int cbWidth, int cbHeight,
                       boolean root, int forcedWidth, int forcedHeight, boolean topMarginTaken, Bands bands){
        return layout(element, parentStyle, x, y, cbWidth, cbHeight, root, forcedWidth, forcedHeight,
                topMarginTaken, bands, null);
    }

    /**
     * forced is the room to keep for the bars, which is only ever handed in by the second pass below.
     */
    private Box layout(DOMTreeElement element, ElementStyle parentStyle, int x, int y, int cbWidth, int cbHeight,
                       boolean root, int forcedWidth, int forcedHeight, boolean topMarginTaken, Bands bands,
                       Gutters forced){
        // nothing inside has changed and it is being asked the same question as last time, so the answer
        // is the one from last time, moved to wherever it goes now. A box with floats around it is left
        // out of this: where its lines may go depends on where the box itself ended up.
        CacheKey key = new CacheKey(parentStyle, cbWidth, cbHeight, forcedWidth, forcedHeight,
                topMarginTaken, root, viewWidth, viewHeight, forced);
        Boxes reusable = bands.isEmpty()
                ? REUSABLE.computeIfAbsent(element, e -> new Boxes()).at(element.subtreeRevision())
                : null;
        Cached cached = reusable == null ? null : reusable.entries.get(key);
        if (cached != null) return shift(cached.box(), x - cached.x(), y - cached.y());

        ElementStyle style = element.getStyle().inheritFrom(parentStyle);
        if (root) style = style.inherit(ROOT_DEFAULTS);
        if (style.get(ElementStyle.DISPLAY) == ElementStyle.Display.NONE) return null;

        Edges margin = edges(style, ElementStyle.MARGIN_TOP, ElementStyle.MARGIN_RIGHT, ElementStyle.MARGIN_BOTTOM, ElementStyle.MARGIN_LEFT, cbWidth);
        Edges padding = edges(style, ElementStyle.PADDING_TOP, ElementStyle.PADDING_RIGHT, ElementStyle.PADDING_BOTTOM, ElementStyle.PADDING_LEFT, cbWidth);
        Edges border = borderEdges(style);
        Edges frame = padding.plus(border);

        int outerSpace = margin.horizontal() + frame.horizontal();
        int contentWidth = forcedWidth != UNFORCED
                ? Math.max(0, forcedWidth - frame.horizontal())
                : contentWidth(element, style, cbWidth, Math.max(0, cbWidth - outerSpace));

        // auto margins on both sides is how a box gets centred
        int leftMargin = margin.left;
        Length marginLeft = style.get(ElementStyle.MARGIN_LEFT);
        Length marginRight = style.get(ElementStyle.MARGIN_RIGHT);
        if (marginLeft != null && marginLeft.isAuto()) {
            int free = Math.max(0, cbWidth - contentWidth - frame.horizontal());
            leftMargin = marginRight != null && marginRight.isAuto() ? free / 2 : free;
        }

        ElementStyle.Overflow overflowX = CTMLScroll.overflowX(style);
        ElementStyle.Overflow overflowY = CTMLScroll.overflowY(style);
        boolean scrollsX = CTMLScroll.scrolls(overflowX);
        boolean scrollsY = CTMLScroll.scrolls(overflowY);

        // the room kept for the bars comes out of the content, and it is kept for as long as the axis
        // could show one rather than only while one is actually there, so nothing has to be laid out
        // twice and nothing jumps sideways the moment the content grows past the box.
        int gutterRight = forced != null ? forced.right() : CTMLScroll.gutterFor(style, overflowY);
        int gutterBottom = forced != null ? forced.bottom() : CTMLScroll.gutterFor(style, overflowX);
        int gutterLeft = CTMLScroll.bothEdges(style) ? gutterRight : 0;
        int gutterTop = CTMLScroll.bothEdges(style) ? gutterBottom : 0;

        int contentX = x + leftMargin + frame.left + gutterLeft;
        int contentY = y + margin.top + frame.top + gutterTop;

        int innerWidth = Math.max(0, contentWidth - gutterLeft - gutterRight);

        // a list item is marked with a shape or with its number. Outside, the marker hangs in whatever
        // room the list's own padding left of it, which is what css does and what a list looks like;
        // inside, it takes its room out of the content instead
        String marker = marker(element, style);
        int markerRoom = marker == null ? 0 : textWidth(marker, style) + MARKER_GAP;
        if (marker != null && style.get(ElementStyle.LIST_STYLE_POSITION,
                ElementStyle.ListStylePosition.OUTSIDE) == ElementStyle.ListStylePosition.INSIDE) {
            contentX += markerRoom;
            innerWidth = Math.max(0, innerWidth - markerRoom);
        }

        List<TextLine> lines = new ArrayList<>();
        List<Box> children = new ArrayList<>();

        // whatever the children measure their own percentages against: this box when it has a definite
        // height of its own, otherwise nothing, since its height is still waiting on them
        int definiteHeight = forcedHeight != UNFORCED
                ? Math.max(0, forcedHeight - frame.vertical())
                : contentHeight(style, cbWidth, cbHeight, Length.UNRESOLVED);
        int childBasis = definiteHeight == Length.UNRESOLVED
                ? -1
                : Math.max(0, definiteHeight - gutterTop - gutterBottom);

        boolean columns = style.isSet(ElementStyle.COLUMN_COUNT) || style.isSet(ElementStyle.COLUMN_WIDTH);
        ElementStyle.Display display = style.get(ElementStyle.DISPLAY, ElementStyle.Display.BLOCK);
        Flow flow = switch (display) {
            case FLEX -> layoutFlex(element, style, contentX, contentY, innerWidth, childBasis, lines, children);
            case GRID -> layoutGrid(element, style, contentX, contentY, innerWidth, childBasis, lines, children);
            case TABLE -> layoutTable(element, style, contentX, contentY, innerWidth, childBasis, lines, children);
            default -> columns
                    ? layoutColumns(element, style, contentX, contentY, innerWidth, childBasis, lines, children)
                    : layoutFlow(element, style, contentX, contentY, innerWidth, childBasis, lines, children,
                            topMarginTaken, bands);
        };
        int baseline = flow.baseline();

        if (marker != null) {
            // level with the first line of the item, or at the top where the item holds no text at all
            int markerY = lines.isEmpty() ? 0 : lines.getFirst().y();
            float markerScale = lines.isEmpty() ? fontScale(style) : lines.getFirst().scale();
            lines.add(new TextLine(sequence(marker, style), marker, -markerRoom, markerY,
                    markerRoom - MARKER_GAP, markerScale, 0));
        }

        // a picture has no content to be as tall as, so an auto height keeps the shape it came in at
        // whatever width it ended up: naming one of the two is naming both, the way css does it
        int[] picture = pictureSize(element);
        int used = picture != null && picture[0] > 0
                ? Math.round(innerWidth * picture[1] / (float) picture[0])
                : flow.height();

        int contentHeight = forcedHeight != UNFORCED
                ? Math.max(0, forcedHeight - frame.vertical())
                : contentHeight(style, cbWidth, cbHeight, used + gutterTop + gutterBottom);
        int innerHeight = Math.max(0, contentHeight - gutterTop - gutterBottom);

        // a block container lines its content up inside a taller box with align-content, which is what
        // centres lines of text in a box the way nothing else can: flex and grid align their own.
        int lead = aligns(display, columns) && picture == null ? spread(1, innerHeight - flow.height(),
                style.get(ElementStyle.ALIGN_CONTENT, ElementStyle.AlignContent.START))[0] : 0;
        if (lead > 0) {
            for (int i = 0; i < lines.size(); i++) {
                TextLine line = lines.get(i);
                lines.set(i, new TextLine(line.text(), line.content(), line.x(), line.y() + lead,
                        line.width(), line.scale(), line.spacing()));
            }
            for (int i = 0; i < children.size(); i++) children.set(i, shift(children.get(i), 0, lead));
            if (baseline != NO_BASELINE) baseline += lead;
        }

        // anything taken out of the flow gets placed against this box once it has a size
        layoutOutOfFlow(element, style, contentX, contentY, innerWidth, innerHeight, children);

        // how far the content reaches, which is what there is to scroll through. roomX / roomY is the
        // same thing with the dragged boxes left out, so dragging one cannot keep pushing its own limit
        int reachX = contentX;
        int reachY = contentY;
        int roomX = contentX + innerWidth;
        int roomY = contentY + innerHeight;
        for (TextLine line : lines) reachX = Math.max(reachX, contentX + line.x() + line.width());
        roomX = Math.max(roomX, reachX);
        for (Box child : children) {
            int right = reachRight(child);
            int bottom = reachBottom(child);
            reachX = Math.max(reachX, right);
            reachY = Math.max(reachY, bottom);
            if (child.offsetX() == 0 && child.offsetY() == 0) {
                roomX = Math.max(roomX, right);
                roomY = Math.max(roomY, bottom);
            }
        }

        int width = contentWidth + frame.horizontal();
        int height = contentHeight + frame.vertical();
        int boxX = x + leftMargin;
        int boxY = y + margin.top;

        // a dragged child is pulled back inside this box every frame, so a resize or a gui scale change
        // cannot leave one stranded outside the box it belongs to
        children = clampDrags(children, contentX, contentY, roomX - contentX, roomY - contentY);

        // the scrollport is the padding box with the gutters taken off it, which is what gets clipped to
        int portX = boxX + border.left + gutterLeft;
        int portY = boxY + border.top + gutterTop;
        int portWidth = Math.max(0, innerWidth + padding.horizontal());
        int portHeight = Math.max(0, innerHeight + padding.vertical());

        // css counts the far padding edge as part of the scrollable area, so content can be scrolled clear of it
        int scrollWidth = Math.max(portWidth, reachX + padding.right - portX);
        int scrollHeight = Math.max(portHeight, reachY + padding.bottom - portY);

        // how far the content can go, and where the element says it has been left. A viewport that has
        // changed since goes back to the share of the way along it was, so a resize or a gui scale change
        // leaves the content looking at the same place rather than at the same pixel.
        int maxScrollX = Math.max(0, scrollWidth - portWidth);
        int maxScrollY = Math.max(0, scrollHeight - portHeight);
        long view = viewKey();
        int scrollX = scrollsX ? element.scrollFor(false, maxScrollX, view) : 0;
        int scrollY = scrollsY ? element.scrollFor(true, maxScrollY, view) : 0;
        if (scrollsX || scrollsY) element.settleScroll(scrollX, scrollY, maxScrollX, maxScrollY, view);

        // scrolling moves everything inside the box and nothing else, so the children are moved here and
        // the text is drawn against the same offset
        if (scrollX != 0 || scrollY != 0) {
            List<Box> scrolled = new ArrayList<>(children.size());
            for (Box child : children) scrolled.add(child.isFixed() ? child : shift(child, -scrollX, -scrollY));
            children = scrolled;
        }

        Port port = new Port(portX, portY, portWidth, portHeight,
                scrollWidth, scrollHeight, scrollX, scrollY, gutterRight, gutterBottom, gutterLeft, gutterTop);

        // a sticky box holds itself against the nearest scrollport, so this is where they get nudged
        if (root || scrollsX || scrollsY) {
            // sticky is held inside the whole scrollable area, not just the part on show, so a header
            // keeps its grip however far down the content has been scrolled
            children = stickAll(children, port, contentX - scrollX, contentY - scrollY,
                    Math.max(innerWidth, reachX - contentX), Math.max(innerHeight, reachY - contentY));
        }

        // however far the user has dragged it, which moves the box and nothing else
        int offsetX = element.getDragX();
        int offsetY = element.getDragY();

        // relative nudges the box without the flow around it noticing
        if (style.get(ElementStyle.POSITION) == ElementStyle.Position.RELATIVE) {
            offsetX += style.resolve(ElementStyle.LEFT, cbWidth, viewWidth, viewHeight, 0)
                    - style.resolve(ElementStyle.RIGHT, cbWidth, viewWidth, viewHeight, 0);
            offsetY += style.resolve(ElementStyle.TOP, cbHeight, viewWidth, viewHeight, 0)
                    - style.resolve(ElementStyle.BOTTOM, cbHeight, viewWidth, viewHeight, 0);
        }

        // with no text anywhere inside it a box lines up by its bottom edge, which is what css does
        int boxBaseline = baseline == NO_BASELINE ? height : baseline + frame.top;

        Box box = shift(new Box(element, style, boxX, boxY, width, height,
                contentX, contentY, innerWidth, innerHeight, offsetX, offsetY, port, boxBaseline, lines, children),
                offsetX, offsetY);

        // a bar takes its room out of the content rather than floating over it, and whether there is one
        // at all is only known once the content has been laid out. So a box that turns out to want a bar
        // it kept no room for is laid out once more with the room kept. Once only: the second pass keeps
        // what the first asked for, whatever it then finds, so nothing can go back and forth.
        if (forced == null) {
            Gutters needed = classic(box, style, gutterRight, gutterBottom);
            if (needed != null) {
                return layout(element, parentStyle, x, y, cbWidth, cbHeight, root, forcedWidth, forcedHeight,
                        topMarginTaken, bands, needed);
            }
        }

        if (reusable != null) reusable.entries.put(key, new Cached(x, y, box));
        return box;
    }

    /** The room kept for the bars inside a box, when it is not what the box was laid out with. */
    private record Gutters(int right, int bottom) {}

    /**
     * The room a box turns out to need for its bars, or null where it already kept it. Only a bar that is
     * really there asks for any, so an axis that merely could scroll keeps none, and hidden bars keep
     * none at all: that is the one case where the content does reach the full width of the box.
     */
    private static Gutters classic(Box box, ElementStyle style, int right, int bottom){
        int thick = CTMLScroll.thickness(style);
        int wantRight = right == 0 && CTMLScroll.showsVerticalBar(box) ? thick : right;
        int wantBottom = bottom == 0 && CTMLScroll.showsHorizontalBar(box) ? thick : bottom;
        return wantRight == right && wantBottom == bottom ? null : new Gutters(wantRight, wantBottom);
    }

    // ---- width and height ----

    /** How much room is left between a list item's marker and its content. */
    private static final int MARKER_GAP = 2;

    /** The shapes, which are what the vanilla font has for a bullet, a ring and a block. */
    private static final String DISC = "\u2022", CIRCLE = "\u25E6", SQUARE = "\u25A0";

    private static final String[] ROMAN_NUMERALS =
            { "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I" };
    private static final int[] ROMAN_VALUES = { 1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1 };

    /**
     * What a list item puts in front of itself, or null for anything that is not one and for a type of
     * none. The counting types are followed by a dot, the way html writes them.
     */
    private static String marker(DOMTreeElement element, ElementStyle style){
        if (style.get(ElementStyle.DISPLAY) != ElementStyle.Display.LIST_ITEM) return null;

        ElementStyle.ListStyle type = style.get(ElementStyle.LIST_STYLE_TYPE, ElementStyle.ListStyle.DISC);
        return switch (type) {
            case NONE -> null;
            case DISC -> DISC;
            case CIRCLE -> CIRCLE;
            case SQUARE -> SQUARE;
            default -> counter(type, countOf(element)) + ".";
        };
    }

    /**
     * Which number a list item is. The items of a list count up from its start, or down from it where it
     * is reversed, and an item with a value of its own takes that number and hands the count on from
     * there, which is what html does with them.
     */
    private static int countOf(DOMTreeElement element){
        DOMTreeElement list = element.getParent();
        if (list == null) return 1;

        boolean down = list.getProperty("reversed") instanceof Reversed reversed && reversed.isOn();
        int at = list.getProperty("start") instanceof Start start && start.hasValue()
                ? start.getFirst()
                : (down ? countItems(list) : 1);

        for (DOMTreeElement child : list.getChildElements()) {
            if (!(child.getElement() instanceof ListItem)) continue;
            if (child.getProperty("value") instanceof Value value && value.hasValue()) at = value.getNumber();
            if (child == element) return at;
            at += down ? -1 : 1;
        }
        return at;
    }

    private static int countItems(DOMTreeElement list){
        int items = 0;
        for (DOMTreeElement child : list.getChildElements()) {
            if (child.getElement() instanceof ListItem) items++;
        }
        return items;
    }

    /** One number as the type writes it: 1, 01, a, A, i or I. */
    private static String counter(ElementStyle.ListStyle type, int number){
        return switch (type) {
            case DECIMAL_LEADING_ZERO -> (number >= 0 && number < 10 ? "0" : "") + number;
            case LOWER_ALPHA -> alpha(number, 'a');
            case UPPER_ALPHA -> alpha(number, 'A');
            case LOWER_ROMAN -> roman(number).toLowerCase();
            case UPPER_ROMAN -> roman(number);
            default -> String.valueOf(number);
        };
    }

    /** a to z, then aa to az, the way a spreadsheet names its columns. */
    private static String alpha(int number, char from){
        if (number < 1) return String.valueOf(number);
        StringBuilder out = new StringBuilder();
        for (int left = number; left > 0; left = (left - 1) / 26) {
            out.insert(0, (char) (from + (left - 1) % 26));
        }
        return out.toString();
    }

    /** Roman numerals, which only go as far as the numbers they have letters for. */
    private static String roman(int number){
        if (number < 1 || number > 3999) return String.valueOf(number);
        StringBuilder out = new StringBuilder();
        int left = number;
        for (int i = 0; i < ROMAN_VALUES.length; i++) {
            while (left >= ROMAN_VALUES[i]) {
                out.append(ROMAN_NUMERALS[i]);
                left -= ROMAN_VALUES[i];
            }
        }
        return out.toString();
    }

    /**
     * The size a replaced element comes with, { width, height }, or null for everything else. Only a
     * picture has one, and only once something holds the texture it names: an image nothing holds has
     * no size to be, so a box with no width or height of its own comes out empty rather than guessing.
     */
    static int[] pictureSize(DOMTreeElement element){
        if (!(element.getElement() instanceof Img picture)) return null;
        CTMLTextures.Texture texture = CTMLTextures.of(picture.source());
        return texture != null && texture.isMeasured() ? new int[]{ texture.width(), texture.height() } : null;
    }

    private int contentWidth(DOMTreeElement element, ElementStyle style, int cbWidth, int available){
        Length width = style.get(ElementStyle.WIDTH);
        int resolved;

        if (Length.isDefinite(width)) {
            resolved = width.resolve(cbWidth, viewWidth, viewHeight, available);
            if (isBorderBox(style)) resolved -= frame(style, cbWidth).horizontal();
        } else if (width != null && width.isContentBased()) {
            resolved = switch (width.unit()) {
                case MIN_CONTENT -> minContent(element, style, cbWidth);
                case MAX_CONTENT -> maxContent(element, style, cbWidth);
                default -> shrinkToFit(element, style, cbWidth, available); // fit-content
            };
        } else if (pictureSize(element) != null) {
            // a picture comes out its own size, or, where it was given a height and no width, whatever
            // width keeps the shape it came in: naming one of the two is naming both
            int[] picture = pictureSize(element);
            Length tall = style.get(ElementStyle.HEIGHT);
            if (tall != null && tall.isDefinite() && !tall.isPercent() && picture[1] > 0) {
                int inner = tall.resolve(0, viewWidth, viewHeight, 0)
                        - (isBorderBox(style) ? frame(style, cbWidth).vertical() : 0);
                resolved = Math.round(Math.max(0, inner) * picture[0] / (float) picture[1]);
            } else {
                resolved = picture[0];
            }
        } else if (isInlineLevel(style)) { // inline-ish boxes are only as wide as they need to be
            resolved = shrinkToFit(element, style, cbWidth, available);
        } else {
            resolved = available;
        }

        resolved = clamp(resolved,
                style.resolve(ElementStyle.MIN_WIDTH, cbWidth, viewWidth, viewHeight, 0),
                style.resolve(ElementStyle.MAX_WIDTH, cbWidth, viewWidth, viewHeight, Integer.MAX_VALUE));
        return Math.max(0, resolved);
    }

    private int contentHeight(ElementStyle style, int cbWidth, int cbHeight, int used){
        Length height = style.get(ElementStyle.HEIGHT);
        boolean definite = Length.isDefinite(height) && (cbHeight >= 0 || !height.isPercent());
        if (!definite && used == Length.UNRESOLVED) return Length.UNRESOLVED; // nothing to go on yet
        int resolved = definite ? height.resolve(Math.max(0, cbHeight), viewWidth, viewHeight, used) : used;

        if (definite && isBorderBox(style)) {
            // padding percentages go off the containing block's width, even on the vertical edges
            resolved -= frame(style, cbWidth).vertical();
        }

        resolved = clamp(resolved,
                style.resolve(ElementStyle.MIN_HEIGHT, Math.max(0, cbHeight), viewWidth, viewHeight, 0),
                style.resolve(ElementStyle.MAX_HEIGHT, Math.max(0, cbHeight), viewWidth, viewHeight, Integer.MAX_VALUE));
        return Math.max(0, resolved);
    }

    // ---- intrinsic sizes ----

    /**
     * The narrowest and the widest a box can be, both as border box sizes: min-content is what it needs
     * with everything that can wrap wrapped, max-content what it takes with nothing wrapping at all.
     * A row of inline content adds up along the line while blocks only have to be as wide as the widest of
     * them, and a percentage width says nothing about either, so it is measured like auto.
     */
    private Intrinsic intrinsic(DOMTreeElement element, ElementStyle style, int basis){
        Sizes sizes = SIZES.computeIfAbsent(element, e -> new Sizes()).at(element.subtreeRevision());
        Measured key = new Measured(basis, style);
        Intrinsic cached = sizes.widths.get(key);
        if (cached != null) return cached;

        Edges frame = frame(style, basis);
        Intrinsic result;

        Length width = style.get(ElementStyle.WIDTH);
        int[] picture = pictureSize(element);
        if (width != null && width.isDefinite() && !width.isPercent()) {
            int size = width.resolve(basis, viewWidth, viewHeight, 0);
            if (!isBorderBox(style)) size += frame.horizontal();
            result = new Intrinsic(size, size);
        } else if (picture != null) {
            // a picture is as wide as it is: there is nothing inside it that could wrap and give way
            int size = picture[0] + frame.horizontal();
            result = new Intrinsic(size, size);
        } else if (style.get(ElementStyle.DISPLAY) == ElementStyle.Display.FLEX) {
            result = flexIntrinsic(element, style, basis, frame);
        } else {
            result = flowIntrinsic(element, style, basis, frame);
        }

        int min = style.resolve(ElementStyle.MIN_WIDTH, basis, viewWidth, viewHeight, 0);
        int max = style.resolve(ElementStyle.MAX_WIDTH, basis, viewWidth, viewHeight, Integer.MAX_VALUE);
        result = new Intrinsic(clamp(result.min(), min, max), clamp(result.max(), min, max));

        sizes.widths.put(key, result);
        return result;
    }

    private Intrinsic flowIntrinsic(DOMTreeElement element, ElementStyle style, int basis, Edges frame){
        int blockMin = 0;
        int blockMax = 0;
        int inlineMin = 0;
        int inlineMax = 0;

        // the element's own text and its text nodes share a line with its inline children
        String own = text(element);
        if (own != null) {
            inlineMin = Math.max(inlineMin, longestWord(own, style));
            inlineMax += textWidth(own, style);
        }

        for (DOMTreeNode node : element.getChildren()) {
            if (node instanceof DOMTreeText run) {
                inlineMin = Math.max(inlineMin, longestWord(run.getText(), style));
                inlineMax += textWidth(run.getText(), style);
                continue;
            }
            if (!(node instanceof DOMTreeElement child)) continue;

            ElementStyle childStyle = child.getStyle().inheritFrom(style);
            if (childStyle.get(ElementStyle.DISPLAY) == ElementStyle.Display.NONE) continue;
            if (isOutOfFlow(childStyle)) continue;

            Intrinsic inner = intrinsic(child, childStyle, basis);
            int margins = horizontalMargins(childStyle, basis);

            if (isInlineLevel(childStyle)) {
                inlineMin = Math.max(inlineMin, inner.min() + margins);
                inlineMax += inner.max() + margins;
                continue;
            }

            // a block closes the run of inline content before it, so that run is measured on its own
            blockMin = Math.max(blockMin, inlineMin);
            blockMax = Math.max(blockMax, inlineMax);
            inlineMin = 0;
            inlineMax = 0;
            blockMin = Math.max(blockMin, inner.min() + margins);
            blockMax = Math.max(blockMax, inner.max() + margins);
        }

        blockMin = Math.max(blockMin, inlineMin);
        blockMax = Math.max(blockMax, inlineMax);
        return new Intrinsic(blockMin + frame.horizontal(), Math.max(blockMin, blockMax) + frame.horizontal());
    }

    /** A row of flex items adds up across the main axis; a column only needs its widest item. */
    private Intrinsic flexIntrinsic(DOMTreeElement element, ElementStyle style, int basis, Edges frame){
        boolean row = isRow(style);
        boolean wraps = style.get(ElementStyle.FLEX_WRAP, ElementStyle.FlexWrap.NOWRAP) != ElementStyle.FlexWrap.NOWRAP;
        int gap = style.resolve(row ? ElementStyle.COLUMN_GAP : ElementStyle.ROW_GAP, basis, viewWidth, viewHeight, 0);

        int min = 0;
        int max = 0;
        int count = 0;

        for (DOMTreeElement child : inFlow(element)) {
            ElementStyle childStyle = child.getStyle().inheritFrom(style);
            if (childStyle.get(ElementStyle.DISPLAY) == ElementStyle.Display.NONE) continue;
            Intrinsic inner = intrinsic(child, childStyle, basis);
            int margins = horizontalMargins(childStyle, basis);
            count++;

            if (row) {
                max += inner.max() + margins;
                // wrapping lets a row fall back to one item a line, so its widest item is narrow enough
                if (wraps) min = Math.max(min, inner.min() + margins);
                else min += inner.min() + margins;
            } else {
                max = Math.max(max, inner.max() + margins);
                min = Math.max(min, inner.min() + margins);
            }
        }

        if (row && count > 1) {
            int gaps = gap * (count - 1);
            max += gaps;
            if (!wraps) min += gaps;
        }

        // the container's own text is inline content above the items, so it can only widen things
        String own = text(element);
        if (own != null) {
            min = Math.max(min, longestWord(own, style));
            max = Math.max(max, textWidth(own, style));
        }

        return new Intrinsic(min + frame.horizontal(), Math.max(min, max) + frame.horizontal());
    }

    /**
     * How narrow a box can be before the content inside it gives way, measured as if it had no width of
     * its own: what css calls the content based minimum, and what a flex item is not allowed to go under.
     */
    private int contentMinimum(DOMTreeElement element, ElementStyle style, int basis){
        Sizes sizes = SIZES.computeIfAbsent(element, e -> new Sizes()).at(element.subtreeRevision());
        Measured key = new Measured(basis, style);
        Integer cached = sizes.minimums.get(key);
        if (cached != null) return cached;

        Edges frame = frame(style, basis);
        Intrinsic inner = style.get(ElementStyle.DISPLAY) == ElementStyle.Display.FLEX
                ? flexIntrinsic(element, style, basis, frame)
                : flowIntrinsic(element, style, basis, frame);

        sizes.minimums.put(key, inner.min());
        return inner.min();
    }

    private int maxContent(DOMTreeElement element, ElementStyle style, int basis){
        return Math.max(0, intrinsic(element, style, basis).max() - frame(style, basis).horizontal());
    }

    private int minContent(DOMTreeElement element, ElementStyle style, int basis){
        return Math.max(0, intrinsic(element, style, basis).min() - frame(style, basis).horizontal());
    }

    /** As wide as it wants to be, but never wider than what is left and never narrower than it can go. */
    private int shrinkToFit(DOMTreeElement element, ElementStyle style, int basis, int available){
        Intrinsic inner = intrinsic(element, style, basis);
        int frame = frame(style, basis).horizontal();
        int min = Math.max(0, inner.min() - frame);
        int max = Math.max(0, inner.max() - frame);
        return Math.max(min, Math.min(max, available));
    }

    // ---- block and inline flow ----

    /**
     * Block children stack downwards with their vertical margins collapsing into one another, while text
     * and inline children build line boxes in between them.
     */
    private Flow layoutFlow(DOMTreeElement element, ElementStyle style, int contentX, int contentY,
                            int width, int cbHeight, List<TextLine> lines, List<Box> boxes,
                            boolean topMarginTaken, Bands inherited){
        List<Item> pending = new ArrayList<>();
        int cursorY = contentY;
        int collapsed = 0;     // the bottom margin of the block before, waiting to collapse into the next one
        boolean first = true;  // nothing placed yet, so there is no margin for the next one to collapse with
        int baseline = NO_BASELINE;
        Bands bands = inherited;
        int floatBottom = contentY; // how far down the floats in here reach, for the box that contains them

        // the text property reads as the first thing inside the element, since it is a property not a node
        addText(pending, text(element), style);

        for (DOMTreeNode node : element.getChildren()) {
            if (node instanceof DOMTreeText run) {
                addText(pending, run.getText(), style);
                continue;
            }
            if (!(node instanceof DOMTreeElement child)) continue;

            ElementStyle childStyle = child.getStyle().inheritFrom(style);
            if (childStyle.get(ElementStyle.DISPLAY) == ElementStyle.Display.NONE) continue;
            if (isOutOfFlow(childStyle)) continue;

            ElementStyle.Floating floating = childStyle.get(ElementStyle.FLOAT, ElementStyle.Floating.NONE);
            if (floating != ElementStyle.Floating.NONE) {
                // a float goes beside the content rather than above or below it, and takes a band out of
                // every line that reaches down to where it sits
                int at = Math.max(cursorY, cursorY + (pending.isEmpty() ? 0 : 0));
                Box box = layoutFloat(child, childStyle, style, contentX, at, width, cbHeight, bands);
                if (box == null) continue;
                boxes.add(box);
                Edges floatMargin = marginEdges(childStyle, width);
                boolean right = floating == ElementStyle.Floating.RIGHT;
                bands = bands.with(new Band(right, box.y() - floatMargin.top,
                        box.bottom() + floatMargin.bottom,
                        right ? box.x() - floatMargin.left : box.right() + floatMargin.right));
                floatBottom = Math.max(floatBottom, box.bottom() + floatMargin.bottom);
                continue;
            }

            if (isInlineLevel(childStyle)) {
                collectInline(child, childStyle, pending, null, contentX, cursorY, width, cbHeight, boxes);
                continue;
            }

            if (!pending.isEmpty()) { // a block closes whatever line is open
                Placed placed = layoutLines(pending, style, contentX, contentY, cursorY, width, lines, boxes, bands);
                if (baseline == NO_BASELINE) baseline = placed.baselineY() - contentY;
                cursorY += placed.height();
                pending.clear();
                collapsed = 0; // margins do not collapse across text
                first = false;
            }

            // clear has to get past whatever is floated on the side it names before it may start
            ElementStyle.Clear clear = childStyle.get(ElementStyle.CLEAR, ElementStyle.Clear.NONE);
            if (clear != ElementStyle.Clear.NONE) {
                int past = bands.bottom(clear == ElementStyle.Clear.RIGHT, clear == ElementStyle.Clear.BOTH);
                if (past > cursorY) {
                    cursorY = past;
                    collapsed = 0;
                }
            }

            Edges childMargin = marginEdges(childStyle, width);

            // the margin that actually shows outside the child, its own collapsed with its first child's
            int childTop = topMargin(child, childStyle, width);
            int childBottom = bottomMargin(child, childStyle, width);

            // the first child's margin has already been taken by whatever placed this box, where the two
            // of them collapsed into one
            int gap = first
                    ? (topMarginTaken ? 0 : childTop)
                    : Math.max(collapsed, childTop);

            // the floats carry on down into a plain block, so its own text runs past them too; a box that
            // starts a formatting context of its own is left out of it, which is what css says
            Box box = layout(child, style, contentX, cursorY + gap - childMargin.top, width, cbHeight,
                    false, UNFORCED, UNFORCED, collapsesTop(childStyle, width),
                    isBlockFlow(childStyle) ? bands : Bands.EMPTY);
            if (box == null) continue;
            boxes.add(box);
            first = false;

            if (collapsesThrough(box, childStyle, width)) {
                // an empty block has nothing to keep apart, so its own margins collapse into one
                collapsed = Math.max(gap, childBottom);
            } else {
                cursorY = box.flowY() + box.height(); // flowY, so a drag or a relative nudge never moves the flow
                collapsed = childBottom;
                if (baseline == NO_BASELINE && box.baseline() != NO_BASELINE) {
                    baseline = box.flowY() + box.baseline() - contentY;
                }
            }
        }

        if (!pending.isEmpty()) {
            Placed placed = layoutLines(pending, style, contentX, contentY, cursorY, width, lines, boxes, bands);
            if (baseline == NO_BASELINE) baseline = placed.baselineY() - contentY;
            cursorY += placed.height();
            collapsed = 0;
        }

        // a box that keeps its own formatting to itself grows to hold the floats inside it; one that does
        // not lets them hang out of the bottom, which is what css does
        int height = cursorY + (collapsesBottom(style, width) ? 0 : collapsed) - contentY;
        if (!isBlockFlow(style)) height = Math.max(height, floatBottom - contentY);
        return new Flow(height, baseline);
    }

    /**
     * Puts a float where it goes: as wide as its content wants, against the side it was pulled to, and
     * pushed on down until there is room for it beside whatever is floated there already.
     */
    private Box layoutFloat(DOMTreeElement element, ElementStyle style, ElementStyle parentStyle,
                            int contentX, int cursorY, int width, int cbHeight, Bands bands){
        Edges margin = marginEdges(style, width);
        int outer = margin.horizontal();
        int wanted = Length.isDefinite(style.get(ElementStyle.WIDTH))
                ? UNFORCED
                : shrinkToFit(element, style, width, Math.max(0, width - outer)) + frame(style, width).horizontal();

        Box box = layout(element, parentStyle, contentX, cursorY, width, cbHeight, false, wanted, UNFORCED, false);
        if (box == null) return null;

        boolean right = style.get(ElementStyle.FLOAT) == ElementStyle.Floating.RIGHT;
        int need = box.width() + outer;
        int y = cursorY;

        // the first band down the page with room enough across it
        for (int guard = 0; guard < bands.items().size() + 1; guard++) {
            int left = bands.left(y, box.height(), contentX);
            int limit = bands.right(y, box.height(), contentX + width);
            if (limit - left >= need) {
                int x = right ? limit - need : left;
                return shift(box, x + margin.left - box.flowX(), y - box.flowY());
            }
            int next = Integer.MAX_VALUE;
            for (Band band : bands.items()) {
                if (band.bottom() > y) next = Math.min(next, band.bottom());
            }
            if (next == Integer.MAX_VALUE) break;
            y = next;
        }

        int left = bands.left(y, box.height(), contentX);
        int limit = bands.right(y, box.height(), contentX + width);
        int x = right ? Math.max(left, limit - need) : left;
        return shift(box, x + margin.left - box.flowX(), y - box.flowY());
    }

    /**
     * Puts an inline child onto the line. An inline element whose content is text is followed into, so its
     * words join the line around it and it can be broken across as many lines as it reaches onto, each one
     * a fragment of its own. Anything else - inline-block, or an inline with a block inside it - goes on
     * the line whole, as one box.
     */
    private void collectInline(DOMTreeElement element, ElementStyle style, List<Item> items, Fragment parent,
                               int contentX, int cursorY, int width, int cbHeight, List<Box> boxes){
        if (element.getElement() instanceof LineBreak) {
            items.add(new Break()); // the line stops here and carries on underneath
            return;
        }
        if (!fragments(element, style)) {
            Box box = layout(element, style, contentX, cursorY, width, cbHeight);
            if (box == null) return;
            boxes.add(box);
            items.add(new Atom(boxes.size() - 1, box, marginEdges(style, width), style, parent));
            return;
        }

        Edges margin = marginEdges(style, width);
        Edges frame = frame(style, width);
        Fragment fragment = new Fragment(element, style, margin, frame, parent);

        items.add(new Edge(fragment, margin.left + frame.left, true));
        addText(items, text(element), style, fragment);

        for (DOMTreeNode node : element.getChildren()) {
            if (node instanceof DOMTreeText run) {
                addText(items, run.getText(), style, fragment);
                continue;
            }
            if (!(node instanceof DOMTreeElement child)) continue;
            ElementStyle childStyle = child.getStyle().inheritFrom(style);
            if (childStyle.get(ElementStyle.DISPLAY) == ElementStyle.Display.NONE) continue;
            if (isOutOfFlow(childStyle)) continue;
            collectInline(child, childStyle, items, fragment, contentX, cursorY, width, cbHeight, boxes);
        }

        items.add(new Edge(fragment, margin.right + frame.right, false));
    }

    /** Whether an inline element can be broken across lines, which anything holding a block cannot. */
    private static boolean fragments(DOMTreeElement element, ElementStyle style){
        if (style.get(ElementStyle.DISPLAY) != ElementStyle.Display.INLINE) return false;
        for (DOMTreeElement child : element.getChildElements()) {
            ElementStyle childStyle = child.getStyle();
            if (childStyle.get(ElementStyle.DISPLAY) == ElementStyle.Display.NONE) continue;
            if (isOutOfFlow(childStyle)) continue;
            if (!isInlineLevel(childStyle)) return false; // a block inside it needs a box of its own
        }
        return true;
    }

    /** A block with nothing in it and no edges of its own: css lets margins collapse right through it. */
    private boolean collapsesThrough(Box box, ElementStyle style, int basis){
        return box.height() == 0
                && borderEdges(style).vertical() == 0
                && frame(style, basis).vertical() == 0
                && !Length.isDefinite(style.get(ElementStyle.HEIGHT));
    }

    /**
     * Whether a box lets a child's vertical margin collapse out through an edge of its own. Anything that
     * starts a formatting context of its own keeps them in: a flex container, a box that clips or scrolls,
     * an inline-block. So does any padding or border on that edge, since that is something to keep apart.
     */
    private boolean collapsesTop(ElementStyle style, int basis){
        return isBlockFlow(style)
                && style.resolve(ElementStyle.PADDING_TOP, basis, viewWidth, viewHeight, 0) == 0
                && borderWidth(style, ElementStyle.BORDER_TOP_WIDTH, ElementStyle.BORDER_TOP_STYLE) == 0;
    }

    /** The same at the bottom, where a box with a height of its own has nowhere for the margin to go. */
    private boolean collapsesBottom(ElementStyle style, int basis){
        return isBlockFlow(style)
                && !Length.isDefinite(style.get(ElementStyle.HEIGHT))
                && style.resolve(ElementStyle.PADDING_BOTTOM, basis, viewWidth, viewHeight, 0) == 0
                && style.get(ElementStyle.BORDER_BOTTOM_WIDTH, 0) == 0;
    }

    private static boolean isBlockFlow(ElementStyle style){
        ElementStyle.Display display = style.get(ElementStyle.DISPLAY, ElementStyle.Display.BLOCK);
        return (display == ElementStyle.Display.BLOCK || display == ElementStyle.Display.LIST_ITEM)
                && !CTMLScroll.clips(CTMLScroll.overflowX(style))
                && !CTMLScroll.clips(CTMLScroll.overflowY(style));
    }

    /**
     * The top margin a block really shows: its own, collapsed with its first child's where the two of them
     * are allowed to meet, and so on down as far as the collapsing goes.
     */
    private int topMargin(DOMTreeElement element, ElementStyle style, int basis){
        int own = style.resolve(ElementStyle.MARGIN_TOP, basis, viewWidth, viewHeight, 0);
        if (!collapsesTop(style, basis)) return own;
        if (hasOwnText(element)) return own; // the text property reads first, so it is already in the way

        for (DOMTreeNode node : element.getChildren()) {
            if (node instanceof DOMTreeText run && !run.getText().isBlank()) return own; // text keeps them apart
            if (!(node instanceof DOMTreeElement child)) continue;
            ElementStyle childStyle = child.getStyle().inheritFrom(style);
            if (childStyle.get(ElementStyle.DISPLAY) == ElementStyle.Display.NONE) continue;
            if (isOutOfFlow(childStyle)) continue;
            if (isInlineLevel(childStyle)) return own; // a line box is content, so there is no collapsing
            return Math.max(own, topMargin(child, childStyle, basis));
        }
        return own;
    }

    private int bottomMargin(DOMTreeElement element, ElementStyle style, int basis){
        int own = style.resolve(ElementStyle.MARGIN_BOTTOM, basis, viewWidth, viewHeight, 0);
        if (!collapsesBottom(style, basis)) return own;

        List<DOMTreeNode> children = element.getChildren();
        if (hasOwnText(element) && children.isEmpty()) return own;
        for (int i = children.size() - 1; i >= 0; i--) {
            DOMTreeNode node = children.get(i);
            if (node instanceof DOMTreeText run && !run.getText().isBlank()) return own;
            if (!(node instanceof DOMTreeElement child)) continue;
            ElementStyle childStyle = child.getStyle().inheritFrom(style);
            if (childStyle.get(ElementStyle.DISPLAY) == ElementStyle.Display.NONE) continue;
            if (isOutOfFlow(childStyle)) continue;
            if (isInlineLevel(childStyle)) return own;
            return Math.max(own, bottomMargin(child, childStyle, basis));
        }
        return own;
    }

    // ---- line boxes ----

    /** Something that can sit on a line: a run of text, an inline level box, or a break between them. */
    private sealed interface Item {}

    /**
     * A run of text that is drawn in one go. space marks the runs that only separate words, since those
     * are dropped at the end of a line and a line never starts with one. owner is the inline element the
     * run belongs to, or null where it is the block's own text.
     */
    private record Run(String text, ElementStyle style, float scale, int width, boolean space, Fragment owner) implements Item {}

    /**
     * The start or the end of an inline element: the room its padding, border and margin take on that
     * side. css only puts them on the first and the last fragment, which is what these two stand for.
     */
    private record Edge(Fragment owner, int width, boolean leading) implements Item {}

    /**
     * An inline element being laid out as fragments rather than as one box: one box per line it reaches
     * onto, nested inside the fragment of whatever inline element holds it.
     */
    private record Fragment(DOMTreeElement element, ElementStyle style, Edges margin, Edges frame, Fragment parent) {}

    /**
     * An inline level box, laid out already, waiting to be told which line it goes on. index is the slot
     * it took in its block's children, and owner the inline element it sits inside, if any.
     */
    private record Atom(int index, Box box, Edges margin, ElementStyle style, Fragment owner) implements Item {}

    /** A line ending that was asked for rather than worked out, so pre text keeps the shape it was written in. */
    private record Break() implements Item {}

    /**
     * Lays text and inline boxes out into line boxes, wrapping at the spaces where white-space allows it.
     * Everything on a line lines up by its baseline unless vertical-align says otherwise, and the line box
     * ends up as tall as it has to be to hold all of it.
     */
    private Placed layoutLines(List<Item> items, ElementStyle style, int contentX, int contentY, int top,
                               int width, List<TextLine> lines, List<Box> boxes){
        return layoutLines(items, style, contentX, contentY, top, width, lines, boxes, Bands.EMPTY);
    }

    private Placed layoutLines(List<Item> items, ElementStyle style, int contentX, int contentY, int top,
                               int width, List<TextLine> lines, List<Box> boxes, Bands bands){
        int clamp = Math.max(0, style.get(ElementStyle.LINE_CLAMP, 0));
        List<Item> work = new ArrayList<>(items);
        int y = top;
        int baselineY = NO_BASELINE;
        int index = 0;
        int placedLines = 0;

        while (index < work.size()) {
            // how much room this line has, which is whatever the floats beside it leave
            int probe = lineHeight(style);
            int from = bands.left(y, probe, contentX);
            int to = bands.right(y, probe, contentX + width);
            int available = to - from;

            if (available <= 0) { // no room at all beside them, so the line goes below instead
                int next = Integer.MAX_VALUE;
                for (Band band : bands.items()) if (band.bottom() > y) next = Math.min(next, band.bottom());
                if (next != Integer.MAX_VALUE) {
                    y = next;
                    continue;
                }
                available = 0;
            }

            OneLine broken = breakOne(work, index, style, available);
            index = broken.next();
            boolean last = clamp > 0 && placedLines + 1 >= clamp && index < work.size();

            Placed placed = placeLine(broken.items(), style, contentX, contentY, y, from,
                    Math.max(0, available), lines, boxes, last);
            if (baselineY == NO_BASELINE) baselineY = placed.baselineY();
            y += placed.height();
            placedLines++;

            if (last) { // everything the clamp cut away is already laid out, so it is hidden instead
                hideAtoms(work.subList(index, work.size()), boxes);
                break;
            }
        }

        if (placedLines == 0) {
            Placed placed = placeLine(new ArrayList<>(), style, contentX, contentY, y, contentX,
                    Math.max(0, width), lines, boxes, false);
            y += placed.height();
            baselineY = placed.baselineY();
        }

        return new Placed(y - top, baselineY == NO_BASELINE ? top + strutAbove(style) : baselineY);
    }

    /** The items for one line, and where the next one starts from. */
    private record OneLine(List<Item> items, int next) {}

    /**
     * Fills one line until it runs out of room. A word longer than the line it is on is left to overflow,
     * the way css does, unless word-break says it may be split, and break-all fills every line right up
     * to the edge.
     */
    private OneLine breakOne(List<Item> work, int from, ElementStyle style, int width){
        boolean wraps = style.get(ElementStyle.WHITE_SPACE, ElementStyle.WhiteSpace.NORMAL)
                == ElementStyle.WhiteSpace.NORMAL && width > 0;
        ElementStyle.WordBreak wordBreak = style.get(ElementStyle.WORD_BREAK, ElementStyle.WordBreak.NORMAL);
        boolean breakAnywhere = wordBreak == ElementStyle.WordBreak.BREAK_ALL;
        boolean splitsWords = breakAnywhere || wordBreak == ElementStyle.WordBreak.BREAK_WORD;

        List<Item> line = new ArrayList<>();
        int used = 0;
        int i = from;

        for (; i < work.size(); i++) {
            Item item = work.get(i);

            if (item instanceof Break) { // a break that was asked for ends the line wherever it is
                i++;
                break;
            }

            boolean space = item instanceof Run run && run.space();
            if (line.isEmpty() && space) continue; // a line never starts with a space

            int itemWidth = itemWidth(item);
            if (wraps && !space && used + itemWidth > width) {
                // break-all fills the line it is on right up to the edge before moving on
                if (breakAnywhere && item instanceof Run run && used < width
                        && split(work, i, line, run, width - used, line.isEmpty())) break;

                if (!line.isEmpty()) break; // try it again on a line of its own, where it may well fit

                // a line of its own is not enough either: split the word if it may be split at all
                if (splitsWords && item instanceof Run run && split(work, i, line, run, width, true)) break;
            }

            line.add(item);
            used += itemWidth;
        }

        return new OneLine(line, i);
    }

    /**
     * Puts as much of a word as fits on the line and leaves the rest behind for the next one.
     * False when none of it would fit, which leaves the word to overflow instead.
     */
    private boolean split(List<Item> work, int index, List<Item> line, Run run, int room, boolean atLeastOne){
        String head = prefix(run, room, atLeastOne);
        if (head.isEmpty() || head.length() >= run.text().length()) return false;
        line.add(run(head, run.style(), run.scale(), false, run.owner()));
        work.set(index, run(run.text().substring(head.length()), run.style(), run.scale(), false, run.owner()));
        return true;
    }

    /**
     * The longest run of characters that fits in the room given, keeping at least one where the line has
     * nothing on it yet, since a line that takes nothing would never end.
     */
    private String prefix(Run run, int room, boolean atLeastOne){
        String text = run.text();
        StringBuilder kept = new StringBuilder();
        int used = 0;

        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            String glyph = new String(Character.toChars(codePoint));
            int width = runWidth(glyph, run.style(), run.scale());
            if (used + width > room && !(kept.isEmpty() && atLeastOne)) break;
            used += width;
            kept.append(glyph);
            i += Character.charCount(codePoint);
            if (used >= room && atLeastOne && !kept.isEmpty()) break;
        }

        return kept.toString();
    }

    /** Anything on a line that was cut away is laid out already, so it is hidden rather than drawn. */
    private static void hideAtoms(List<Item> items, List<Box> boxes){
        for (Item item : items) {
            if (!(item instanceof Atom atom)) continue;
            Box box = boxes.get(atom.index());
            boxes.set(atom.index(), withStyle(box, box.style().with(ElementStyle.VISIBILITY, ElementStyle.Visibility.HIDDEN)));
        }
    }

    /** One line box: how tall it is, where its baseline falls, and everything on it put in its place. */
    private Placed placeLine(List<Item> line, ElementStyle style, int contentX, int contentY, int top,
                             int originX, int width, List<TextLine> lines, List<Box> boxes, boolean mustEllipsise){
        // the spaces hanging off the end count for nothing: not towards the width, not towards the alignment
        while (!line.isEmpty() && line.getLast() instanceof Run run && run.space()) line.removeLast();

        // the strut: the element's own font holds the line open even where there is nothing on it
        int above = strutAbove(style);
        int below = strutBelow(style);
        if (line.isEmpty()) return new Placed(above + below, top + above);

        int reach = 0;
        for (Item item : line) reach += itemWidth(item);
        if (mustEllipsise || (reach > width && ellipsises(style))) {
            List<Item> before = line;
            line = ellipsise(line, style, width, mustEllipsise);
            List<Item> gone = new ArrayList<>(before);
            gone.removeAll(line);
            hideAtoms(gone, boxes); // an inline box the dots took the place of is not drawn
        }

        for (Item item : line) {
            if (!linesUpOnBaseline(item)) continue;
            int itemAbove = itemAbove(item, style);
            above = Math.max(above, itemAbove);
            below = Math.max(below, itemHeight(item) - itemAbove);
        }

        // the ones that go by the line box rather than the baseline can only make it taller
        int height = above + below;
        for (Item item : line) {
            if (!linesUpOnBaseline(item)) height = Math.max(height, itemHeight(item));
        }
        below = height - above;
        int baselineY = top + above;

        int lineWidth = 0;
        for (Item item : line) lineWidth += itemWidth(item);

        // a line with any right to left text in it is put in the order it is read in, which can mean
        // moving whole runs about, not just the characters inside one of them
        line = reorder(line);

        ElementStyle.TextAlign align = style.get(ElementStyle.TEXT_ALIGN, ElementStyle.TextAlign.LEFT);
        int cursorX = originX + switch (align) {
            case CENTER -> Math.max(0, width - lineWidth) / 2;
            case RIGHT -> Math.max(0, width - lineWidth);
            case LEFT -> 0;
        };

        // the block itself, and whatever inline elements the line passes through, each gathering the text
        // it holds as it goes. An inline element that runs on gets one of these for every line it reaches.
        Open block = new Open(null, null, cursorX);
        Open open = block;

        for (Item item : line) {
            open = reconcile(open, ownerOf(item), cursorX, baselineY, boxes);

            if (item instanceof Run run) {
                open.add(run, cursorX);
                cursorX += run.width();
                continue;
            }
            if (item instanceof Edge edge) {
                cursorX += edge.width(); // the padding and border of an inline element take their room here
                open.endX = cursorX;
                continue;
            }
            if (!(item instanceof Atom atom)) continue;

            int boxTop = switch (atom.style().get(ElementStyle.VERTICAL_ALIGN, ElementStyle.VerticalAlign.BASELINE)) {
                case TOP -> top + atom.margin().top;
                case BOTTOM -> top + height - itemHeight(atom) + atom.margin().top;
                default -> baselineY - itemAbove(atom, style) + atom.margin().top;
            };
            Box placed = shift(atom.box(),
                    cursorX + atom.margin().left - atom.box().flowX(),
                    boxTop - atom.box().flowY());
            if (open.fragment == null) {
                boxes.set(atom.index(), placed);
            } else {
                // it goes inside the fragment instead, so the inline element's own background stays behind it
                open.children.add(placed);
                boxes.set(atom.index(), withStyle(placed,
                        placed.style().with(ElementStyle.VISIBILITY, ElementStyle.Visibility.HIDDEN)));
            }
            cursorX += itemWidth(atom);
            open.endX = cursorX;
        }

        reconcile(open, null, cursorX, baselineY, boxes);
        block.flush(baselineY, contentX, contentY, lines);

        return new Placed(height, baselineY);
    }

    /**
     * One inline element (or the block itself) as far as a single line is concerned: where it starts and
     * ends on that line, the text it gathered there, and the fragments of whatever is nested inside it.
     */
    private final class Open {
        final Fragment fragment;
        final Open parent;
        final int startX;
        int endX;
        final List<RunLine> runs = new ArrayList<>();
        final List<Box> children = new ArrayList<>();

        Open(Fragment fragment, Open parent, int startX){
            this.fragment = fragment;
            this.parent = parent;
            this.startX = startX;
            this.endX = startX;
        }

        /** Adds a run, carrying on from the one before where the two are drawn the same way. */
        void add(Run run, int x){
            RunLine last = runs.isEmpty() ? null : runs.getLast();
            if (last != null && last.style == run.style() && last.scale == run.scale() && last.x + last.width == x) {
                last.text.append(run.text());
                last.width += run.width();
            } else {
                RunLine line = new RunLine();
                line.style = run.style();
                line.scale = run.scale();
                line.x = x;
                line.width = run.width();
                line.text.append(run.text());
                runs.add(line);
            }
            endX = x + run.width();
        }

        /** Turns the gathered runs into the text lines that get drawn, measured from a content corner. */
        void flush(int baselineY, int originX, int originY, List<TextLine> into){
            for (RunLine run : runs) {
                String content = run.text.toString();
                if (content.isEmpty()) continue;
                int y = baselineY - Math.round(metrics.ascent() * run.scale) - originY;
                into.add(new TextLine(sequence(content, run.style), content, run.x - originX, y, run.width,
                        run.scale, letterSpacing(run.style)));
            }
            runs.clear();
        }
    }

    /** A run of text being gathered up, before it becomes a TextLine. */
    private static final class RunLine {
        final StringBuilder text = new StringBuilder();
        ElementStyle style;
        float scale;
        int x;
        int width;
    }

    private static Fragment ownerOf(Item item){
        if (item instanceof Run run) return run.owner();
        if (item instanceof Edge edge) return edge.owner();
        if (item instanceof Atom atom) return atom.owner();
        return null;
    }

    /**
     * Closes whatever inline elements the line has just left and opens the ones it has just reached, so
     * what is open always matches the element the next item belongs to.
     */
    private Open reconcile(Open open, Fragment owner, int cursorX, int baselineY, List<Box> boxes){
        List<Fragment> wanted = new ArrayList<>();
        for (Fragment fragment = owner; fragment != null; fragment = fragment.parent()) wanted.add(fragment);
        wanted = wanted.reversed();

        List<Fragment> current = new ArrayList<>();
        for (Open at = open; at != null && at.fragment != null; at = at.parent) current.add(at.fragment);
        current = current.reversed();

        int shared = 0;
        while (shared < current.size() && shared < wanted.size() && current.get(shared) == wanted.get(shared)) shared++;

        Open at = open;
        for (int i = current.size(); i > shared; i--) {
            close(at, baselineY, boxes);
            at = at.parent;
        }
        for (int i = shared; i < wanted.size(); i++) at = new Open(wanted.get(i), at, cursorX);
        return at;
    }

    /**
     * Builds the box for one fragment of an inline element and hands it to whatever holds it: the box of
     * the inline element around it, or the block's own children where there is none.
     */
    private void close(Open open, int baselineY, List<Box> boxes){
        Fragment fragment = open.fragment;
        Edges margin = fragment.margin();
        Edges frame = fragment.frame();
        ElementStyle style = fragment.style();

        int x = open.startX + margin.left;
        int right = Math.max(x, open.endX - margin.right);
        int ascent = Math.round(metrics.ascent() * fontScale(style));
        int contentHeight = fontHeight(style);
        int y = baselineY - ascent - frame.top;
        int height = contentHeight + frame.vertical();
        int boxContentX = x + frame.left;
        int boxContentY = y + frame.top;
        int width = Math.max(0, right - x);

        List<TextLine> own = new ArrayList<>();
        open.flush(baselineY, boxContentX, boxContentY, own);

        Edges border = borderEdges(style);
        int portWidth = Math.max(0, width - border.horizontal());
        int portHeight = Math.max(0, height - border.vertical());
        Port port = Port.still(x + border.left, y + border.top, portWidth, portHeight);

        Box box = new Box(fragment.element(), style, x, y, width, height,
                boxContentX, boxContentY, Math.max(0, width - frame.horizontal()), contentHeight,
                0, 0, port, ascent + frame.top, own, List.copyOf(open.children));

        if (open.parent == null || open.parent.fragment == null) boxes.add(box);
        else open.parent.children.add(box);
    }

    /** What gets put on the end of a line that was cut short. Minecraft's font has no ellipsis glyph. */
    private static final String ELLIPSIS = "\u2026";

    /**
     * Whether a line that will not fit gets cut short with dots. css only does it where the overflow is
     * cut off, and a box the user can scroll keeps the whole line instead, since there would otherwise be
     * nothing left to scroll to.
     */
    private boolean ellipsises(ElementStyle style){
        return style.get(ElementStyle.TEXT_OVERFLOW) == ElementStyle.TextOverflow.ELLIPSIS
                && CTMLScroll.clips(CTMLScroll.overflowX(style))
                && !CTMLScroll.userScrolls(CTMLScroll.overflowX(style));
    }

    /**
     * Keeps as much of the line as fits next to the dots and drops the rest. Only text is cut: an inline
     * box that ran off the end is already laid out, so the box's own clip is what deals with it.
     */
    private List<Item> ellipsise(List<Item> line, ElementStyle style, int width, boolean must){
        int dots = runWidth(ELLIPSIS, style, fontScale(style));
        int room = Math.max(0, width - dots);
        List<Item> kept = new ArrayList<>(line.size());
        int used = 0;

        for (Item item : line) {
            int itemWidth = itemWidth(item);
            if (used + itemWidth <= room) {
                kept.add(item);
                used += itemWidth;
                continue;
            }

            // this is the one that has to give: a word is cut where it stops fitting, a box is left be
            if (item instanceof Run run && !run.space()) {
                String fitting = fits(run, room - used);
                if (!fitting.isEmpty()) kept.add(run(fitting, run.style(), run.scale(), false, run.owner()));
            } else if (item instanceof Atom) {
                kept.add(item);
            }

            while (!kept.isEmpty() && kept.getLast() instanceof Run last && last.space()) kept.removeLast();
            kept.add(run(ELLIPSIS, style, fontScale(style), false));
            return kept;
        }

        // nothing had to be cut, but the lines after this one were, so the dots still belong on the end
        if (must) {
            while (!kept.isEmpty() && kept.getLast() instanceof Run last && last.space()) kept.removeLast();
            kept.add(run(ELLIPSIS, style, fontScale(style), false));
        }
        return kept;
    }

    /** The longest run of characters that still fits in the room given. */
    private String fits(Run run, int room){
        if (room <= 0) return "";
        StringBuilder kept = new StringBuilder();
        int used = 0;
        String text = run.text();

        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            String glyph = new String(Character.toChars(codePoint));
            int width = runWidth(glyph, run.style(), run.scale());
            if (used + width > room) break;
            used += width;
            kept.append(glyph);
            i += Character.charCount(codePoint);
        }

        return kept.toString().stripTrailing();
    }

    private FormattedCharSequence sequence(String content, ElementStyle style){
        return Component.literal(content).setStyle(textStyle(style)).getVisualOrderText();
    }

    /**
     * Puts a line's runs into the order they are read in. Minecraft already sorts the characters inside
     * each run out for itself; what it cannot do is move two runs past one another, which is what has to
     * happen where a styled word sits in the middle of right to left text. A line with nothing right to
     * left in it is handed straight back, so this costs nothing in the usual case.
     */
    private static List<Item> reorder(List<Item> line){
        if (line.size() < 2) return line;

        StringBuilder text = new StringBuilder();
        int[] starts = new int[line.size()];
        for (int i = 0; i < line.size(); i++) {
            starts[i] = text.length();
            // anything that is not text counts as one neutral object, the way css treats a replaced box
            text.append(line.get(i) instanceof Run run ? run.text() : "\uFFFC");
        }

        Bidi bidi = new Bidi(text.toString(), Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
        if (bidi.isLeftToRight()) return line;

        // each item takes the level of where it starts, then the standard reordering does the rest
        byte[] levels = new byte[line.size()];
        Integer[] order = new Integer[line.size()];
        for (int i = 0; i < line.size(); i++) {
            levels[i] = (byte) bidi.getLevelAt(starts[i]);
            order[i] = i;
        }

        Bidi.reorderVisually(levels, 0, order, 0, line.size());
        List<Item> visual = new ArrayList<>(line.size());
        for (Integer from : order) visual.add(line.get(from));
        return visual;
    }

    /** Items line up on the baseline unless they were told to go by the line box instead. */
    private static boolean linesUpOnBaseline(Item item){
        if (!(item instanceof Atom atom)) return true;
        ElementStyle.VerticalAlign align = atom.style().get(ElementStyle.VERTICAL_ALIGN, ElementStyle.VerticalAlign.BASELINE);
        return align != ElementStyle.VerticalAlign.TOP && align != ElementStyle.VerticalAlign.BOTTOM;
    }

    private int itemWidth(Item item){
        if (item instanceof Run run) return run.width();
        if (item instanceof Atom atom) return atom.box().width() + atom.margin().horizontal();
        if (item instanceof Edge edge) return edge.width();
        return 0;
    }

    private int itemHeight(Item item){
        if (item instanceof Run run) return lineHeight(run.style());
        if (item instanceof Atom atom) return atom.box().height() + atom.margin().vertical();
        if (item instanceof Edge edge) return lineHeight(edge.owner().style());
        return 0;
    }

    /** How far above the baseline the top of an item's margin box sits, which is what makes the line box. */
    private int itemAbove(Item item, ElementStyle parentStyle){
        if (item instanceof Run run) return strutAbove(run.style());
        if (item instanceof Edge edge) return strutAbove(edge.owner().style());
        Atom atom = (Atom) item;
        int height = atom.box().height();
        int baseline = atom.box().baseline() == NO_BASELINE ? height : atom.box().baseline();
        int ascent = Math.round(metrics.ascent() * fontScale(parentStyle));

        int above = switch (atom.style().get(ElementStyle.VERTICAL_ALIGN, ElementStyle.VerticalAlign.BASELINE)) {
            case BASELINE, TOP, BOTTOM -> baseline;
            case SUB -> baseline - ascent / 4;
            case SUPER -> baseline + ascent / 3;
            case TEXT_TOP -> ascent;
            case TEXT_BOTTOM -> height - strutBelow(parentStyle);
            // middle sits the box's own middle on the middle of the lower case letters
            case MIDDLE -> height / 2 + ascent / 4;
        };
        return above + atom.margin().top;
    }

    // ---- text into runs ----

    /**
     * Breaks text into the runs that go on a line: one per word, with the spaces between them kept as runs
     * of their own so a line can be broken there and the space dropped. Runs of whitespace collapse into a
     * single space the way css does, unless white-space is pre, where the text keeps the shape it was
     * written in and only the newlines break the line.
     */
    private void addText(List<Item> items, String text, ElementStyle style){
        addText(items, text, style, null);
    }

    private void addText(List<Item> items, String text, ElementStyle style, Fragment owner){
        if (text == null || text.isEmpty()) return;
        float scale = fontScale(style);

        if (style.get(ElementStyle.WHITE_SPACE) == ElementStyle.WhiteSpace.PRE) {
            String[] hard = text.split("\n", -1);
            for (int i = 0; i < hard.length; i++) {
                if (i > 0) items.add(new Break());
                if (!hard[i].isEmpty()) items.add(run(hard[i], style, scale, false, owner));
            }
            return;
        }

        int i = 0;
        while (i < text.length()) {
            if (Character.isWhitespace(text.charAt(i))) {
                while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
                // a space only separates, so two in a row say no more than one does
                if (!items.isEmpty() && !(items.getLast() instanceof Run last && last.space())) {
                    items.add(run(" ", style, scale, true, owner));
                }
                continue;
            }
            int start = i;
            while (i < text.length() && !Character.isWhitespace(text.charAt(i))) i++;
            addWord(items, text.substring(start, i), style, scale, owner);
        }
    }

    /**
     * One word, split again wherever a line is allowed to break inside it. The scripts written without
     * spaces break between their characters, which is how a wall of them wraps at all, and word-break
     * keep-all is how to ask for the whole word to stay together.
     */
    private void addWord(List<Item> items, String word, ElementStyle style, float scale, Fragment owner){
        if (word.isEmpty()) return;
        if (style.get(ElementStyle.WORD_BREAK) == ElementStyle.WordBreak.KEEP_ALL) {
            items.add(run(word, style, scale, false, owner));
            return;
        }

        int from = 0;
        for (int i = 0; i < word.length(); ) {
            int codePoint = word.codePointAt(i);
            int next = i + Character.charCount(codePoint);
            boolean breaks = next < word.length()
                    && breaksAfter(codePoint)
                    && !startsNoLine(word.codePointAt(next));
            if (breaks) {
                items.add(run(word.substring(from, next), style, scale, false, owner));
                from = next;
            }
            i = next;
        }
        if (from < word.length()) items.add(run(word.substring(from), style, scale, false, owner));
    }

    /** The characters a line may break after: the ones from the scripts that are written without spaces. */
    private static boolean breaksAfter(int codePoint){
        return (codePoint >= 0x1100 && codePoint <= 0x11FF)   // hangul jamo
                || (codePoint >= 0x2E80 && codePoint <= 0x303E) // cjk radicals and punctuation
                || (codePoint >= 0x3041 && codePoint <= 0x33FF) // kana and the cjk compatibility blocks
                || (codePoint >= 0x3400 && codePoint <= 0x4DBF)
                || (codePoint >= 0x4E00 && codePoint <= 0x9FFF) // the han characters themselves
                || (codePoint >= 0xA000 && codePoint <= 0xA4CF) // yi
                || (codePoint >= 0xAC00 && codePoint <= 0xD7A3) // hangul syllables
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
                || (codePoint >= 0xFF00 && codePoint <= 0xFF60) // the full width forms
                || (codePoint >= 0xFFE0 && codePoint <= 0xFFE6);
    }

    /** Punctuation that is never left to start a line, so a break before it is no break at all. */
    private static boolean startsNoLine(int codePoint){
        return switch (codePoint) {
            case 0x3001, 0x3002, 0x3005, 0x301C, 0x303B,            // 、 。 々 〜 〻
                 0x30FC, 0x309B, 0x309C, 0x3063, 0x3083, 0x3085,    // ー and the small kana
                 0x3087, 0x30C3, 0x30E3, 0x30E5, 0x30E7, 0x30FB,
                 0xFF01, 0xFF09, 0xFF0C, 0xFF0E, 0xFF1A, 0xFF1B,    // ！ ） ， ． ： ；
                 0xFF1F, 0xFF3D, 0xFF5D, 0x3009, 0x300B, 0x300D,
                 0x300F, 0x3011, 0x3015 -> true;
            default -> false;
        };
    }

    private Run run(String text, ElementStyle style, float scale, boolean space){
        return run(text, style, scale, space, null);
    }

    private Run run(String text, ElementStyle style, float scale, boolean space, Fragment owner){
        return new Run(text, style, scale, runWidth(text, style, scale), space, owner);
    }

    /**
     * How wide a run of text comes out: what the font makes of it at its size, plus the room letter-spacing
     * asks for after each of its characters.
     */
    private int runWidth(String text, ElementStyle style, float scale){
        return Math.round(metrics.width(sequence(text, style)) * scale)
                + letterSpacing(style) * text.codePointCount(0, text.length());
    }

    static int letterSpacing(ElementStyle style){
        return style.get(ElementStyle.LETTER_SPACING, 0);
    }

    // ---- flex ----

    /**
     * A flex item being worked out: what size it asked for, what it ended up with, and the box for it.
     * element is null for the anonymous item css wraps a flex container's own text in: that one has no box
     * of its own, so it keeps the text runs it is made of and the lines they ended up as instead.
     */
    private static final class FlexItem {
        final DOMTreeElement element;
        final ElementStyle style;
        final Edges margin;
        final List<Item> text;
        int base;
        int hypothetical;
        int min;
        int max;
        int grow;
        int shrink;
        boolean frozen;
        int main;
        Box box;
        int textHeight;
        int textBaseline = NO_BASELINE;
        int lineFrom;
        int lineTo;
        List<TextLine> measuredLines;

        FlexItem(DOMTreeElement element, ElementStyle style, Edges margin, List<Item> text){
            this.element = element;
            this.style = style;
            this.margin = margin;
            this.text = text;
        }

        boolean isText(){ return element == null; }
        int mainMargin(boolean row){ return row ? margin.horizontal() : margin.vertical(); }
        int crossMargin(boolean row){ return row ? margin.vertical() : margin.horizontal(); }
        int outerMain(boolean row){ return main + mainMargin(row); }

        int crossSize(boolean row){
            if (isText()) return row ? textHeight : main;
            return box == null ? 0 : (row ? box.height() : box.width());
        }

        int outerCross(boolean row){ return crossSize(row) + crossMargin(row); }
    }

    /**
     * Flexbox: items in order, sized from their basis and then grown or shrunk to fill the line, wrapped
     * onto as many lines as they need, each line aligned across the cross axis and the lines themselves
     * spread out by align-content.
     */
    private Flow layoutFlex(DOMTreeElement element, ElementStyle style, int contentX, int contentY,
                            int width, int cbHeight, List<TextLine> lines, List<Box> boxes){
        int baseline = NO_BASELINE;
        int originY = contentY;
        boolean row = isRow(style);
        boolean reverse = isReverse(style);
        ElementStyle.FlexWrap wrapMode = style.get(ElementStyle.FLEX_WRAP, ElementStyle.FlexWrap.NOWRAP);
        boolean wraps = wrapMode != ElementStyle.FlexWrap.NOWRAP;

        int inner = cbHeight < 0 ? -1 : cbHeight;
        int mainSize = row ? width : inner;
        int crossSize = row ? inner : width;
        // the gap along the main axis is the one that runs that way: across a row, down a column
        int gap = style.resolve(row ? ElementStyle.COLUMN_GAP : ElementStyle.ROW_GAP,
                row ? width : Math.max(0, inner), viewWidth, viewHeight, 0);
        int lineGap = style.resolve(row ? ElementStyle.ROW_GAP : ElementStyle.COLUMN_GAP,
                row ? Math.max(0, inner) : width, viewWidth, viewHeight, 0);

        List<FlexItem> items = new ArrayList<>();

        // css wraps a run of text in a flex container in an anonymous item of its own, so it takes part
        // in the main axis like anything else instead of sitting above it
        List<Item> own = new ArrayList<>();
        addText(own, text(element), style);
        for (DOMTreeNode node : element.getChildren()) {
            if (node instanceof DOMTreeText run) addText(own, run.getText(), style);
        }
        if (!own.isEmpty()) items.add(new FlexItem(null, style, new Edges(0, 0, 0, 0), own));

        for (DOMTreeElement child : inFlow(element)) {
            ElementStyle childStyle = child.getStyle().inheritFrom(style);
            if (childStyle.get(ElementStyle.DISPLAY) == ElementStyle.Display.NONE) continue;
            items.add(new FlexItem(child, childStyle, marginEdges(childStyle, width), null));
        }
        if (items.isEmpty()) return new Flow(0, baseline);

        // order only moves items about; equal orders keep the order they were written in
        items.sort(Comparator.comparingInt(item -> item.style.get(ElementStyle.ORDER, 0)));

        // css starts at stretch, which is what fills a line's thickness when an item has no size of its own
        ElementStyle.AlignItems align = style.get(ElementStyle.ALIGN_ITEMS, ElementStyle.AlignItems.STRETCH);
        for (FlexItem item : items) sizeItem(item, style, contentX, originY, width, cbHeight, mainSize, row, align);

        // break the items into lines, each as full as the container's main size allows
        List<List<FlexItem>> flexLines = new ArrayList<>();
        if (!wraps || mainSize < 0) {
            flexLines.add(items);
        } else {
            List<FlexItem> current = new ArrayList<>();
            int filled = 0;
            for (FlexItem item : items) {
                int outer = item.hypothetical + item.mainMargin(row);
                int cost = outer + (current.isEmpty() ? 0 : gap);
                if (!current.isEmpty() && filled + cost > mainSize) {
                    flexLines.add(current);
                    current = new ArrayList<>();
                    filled = 0;
                    cost = outer;
                }
                current.add(item);
                filled += cost;
            }
            if (!current.isEmpty()) flexLines.add(current);
        }
        if (wrapMode == ElementStyle.FlexWrap.WRAP_REVERSE) flexLines = flexLines.reversed();

        // grow or shrink each line to fill the main size, then lay the items out at the size they got
        int[] lineCross = new int[flexLines.size()];
        for (int i = 0; i < flexLines.size(); i++) {
            List<FlexItem> flexLine = flexLines.get(i);
            resolveFlexible(flexLine, mainSize, gap, row);

            int cross = 0;
            for (FlexItem item : flexLine) {
                item.box = layoutItem(item, style, contentX, originY, width, cbHeight, row, item.main,
                        crossForce(item, width, row, align));
                cross = Math.max(cross, item.outerCross(row));
            }
            lineCross[i] = cross;
        }

        // a single line is as thick as the container itself, which is what lets stretch fill it
        if (flexLines.size() == 1 && crossSize >= 0) lineCross[0] = Math.max(lineCross[0], crossSize);

        int crossUsed = lineGap * Math.max(0, flexLines.size() - 1);
        for (int cross : lineCross) crossUsed += cross;

        // align-content spreads the lines themselves once there is room left over
        ElementStyle.AlignContent alignContent = style.get(ElementStyle.ALIGN_CONTENT, ElementStyle.AlignContent.START);
        int crossFree = crossSize < 0 ? 0 : Math.max(0, crossSize - crossUsed);
        int[] crossLead = spread(flexLines.size(), crossFree, alignContent);
        if (alignContent == ElementStyle.AlignContent.STRETCH && crossFree > 0) {
            // every line gets a share of what is left, the odd pixels going to the first ones
            for (int i = 0; i < lineCross.length; i++) {
                lineCross[i] += crossFree / lineCross.length + (i < crossFree % lineCross.length ? 1 : 0);
            }
        }

        int crossCursor = row ? originY : contentX;
        int mainEnd = row ? contentX : originY;

        for (int i = 0; i < flexLines.size(); i++) {
            List<FlexItem> flexLine = flexLines.get(i);
            int cross = lineCross[i];

            // stretch is the one alignment that changes a size, so it is settled before anything moves
            for (FlexItem item : flexLine) {
                if (item.isText()) continue; // text is as tall as its lines, there is nothing to stretch
                if (alignOf(item, align) != ElementStyle.AlignItems.STRETCH) continue;
                if (Length.isDefinite(item.style.get(row ? ElementStyle.HEIGHT : ElementStyle.WIDTH))) continue;
                int stretched = Math.max(0, cross - item.crossMargin(row));
                if (stretched == item.crossSize(row)) continue;
                item.box = layoutItem(item, style, contentX, originY, width, cbHeight, row, item.main, stretched);
            }

            int usedMain = gap * Math.max(0, flexLine.size() - 1);
            for (FlexItem item : flexLine) usedMain += item.outerMain(row);

            int free = mainSize < 0 ? 0 : Math.max(0, mainSize - usedMain);
            ElementStyle.JustifyContent justify = style.get(ElementStyle.JUSTIFY_CONTENT, ElementStyle.JustifyContent.START);
            int[] lead = spread(flexLine.size(), free, justify);

            // items aligned by their text all hang off the deepest baseline on the line
            int lineBaseline = 0;
            for (FlexItem item : flexLine) {
                if (alignOf(item, align) != ElementStyle.AlignItems.BASELINE) continue;
                lineBaseline = Math.max(lineBaseline, item.margin.top + itemBaseline(item, row));
            }

            // a reversed direction turns the main axis round: the items keep their order and the places
            // they would have had are mirrored, so the first one starts at the far end
            int mainStart = row ? contentX : originY;
            int span = mainSize < 0 ? usedMain : mainSize;
            int main = mainStart;
            int placedCount = 0;

            for (FlexItem item : flexLine) {
                int offset = switch (alignOf(item, align)) {
                    case CENTER -> Math.max(0, cross - item.outerCross(row)) / 2;
                    case END -> Math.max(0, cross - item.outerCross(row));
                    case BASELINE -> Math.max(0, lineBaseline - item.margin.top - itemBaseline(item, row));
                    default -> 0;
                };
                int crossStart = crossCursor + crossLead[i] + offset;
                int at = main + lead[placedCount++];
                int mainStop = reverse
                        ? mainStart + span - (at - mainStart) - item.outerMain(row)
                        : at;

                int targetX = row ? mainStop + item.margin.left : crossStart + item.margin.left;
                int targetY = row ? crossStart + item.margin.top : mainStop + item.margin.top;

                if (item.isText()) {
                    // the lines were laid out off to one side, so they are carried over to where the item went
                    int dx = targetX - contentX;
                    int dy = targetY - contentY;
                    for (TextLine line : item.measuredLines) {
                        lines.add(new TextLine(line.text(), line.content(),
                                line.x() + dx, line.y() + dy, line.width(), line.scale(), line.spacing()));
                    }
                    if (baseline == NO_BASELINE) baseline = item.textBaseline + dy;
                    main += item.outerMain(row) + gap;
                    continue;
                }

                Box placed = shift(item.box, targetX - item.box.flowX(), targetY - item.box.flowY());
                boxes.add(placed);

                if (baseline == NO_BASELINE && placed.baseline() != NO_BASELINE) {
                    baseline = placed.flowY() + placed.baseline() - contentY;
                }

                main += item.outerMain(row) + gap;
            }

            mainEnd = Math.max(mainEnd, main - gap + (flexLine.isEmpty() ? 0 : lead[flexLine.size() - 1]));
            crossCursor += cross + lineGap;
        }

        int crossEnd = crossCursor - lineGap + (crossFree > 0 ? crossLead[flexLines.size() - 1] : 0);
        return new Flow(Math.max(0, (row ? crossEnd : mainEnd) - contentY), baseline);
    }

    /**
     * Where each of a line's items (or each of the lines themselves) starts once the free space has been
     * shared out. Worked out from the whole free space for every one of them rather than added up a gap at
     * a time, so the rounding cannot leave a pixel or two unaccounted for at the far end.
     */
    private static int[] spread(int count, int free, ElementStyle.JustifyContent justify){
        return spread(count, free, switch (justify) {
            case CENTER -> ElementStyle.AlignContent.CENTER;
            case END -> ElementStyle.AlignContent.END;
            case SPACE_BETWEEN -> ElementStyle.AlignContent.SPACE_BETWEEN;
            case SPACE_AROUND -> ElementStyle.AlignContent.SPACE_AROUND;
            case SPACE_EVENLY -> ElementStyle.AlignContent.SPACE_EVENLY;
            case START -> ElementStyle.AlignContent.START;
        });
    }

    private static int[] spread(int count, int free, ElementStyle.AlignContent mode){
        int[] offsets = new int[Math.max(1, count)];
        if (count <= 0 || free <= 0) return offsets;

        for (int i = 0; i < count; i++) {
            offsets[i] = switch (mode) {
                case CENTER -> free / 2;
                case STRETCH -> 0; // stretch has already given the free space to the lines themselves
                case END -> free;
                case SPACE_BETWEEN -> count > 1 ? Math.round(free * (float) i / (count - 1)) : 0;
                case SPACE_AROUND -> Math.round(free * (2 * i + 1) / (float) (2 * count));
                case SPACE_EVENLY -> Math.round(free * (i + 1) / (float) (count + 1));
                case START -> 0;
            };
        }
        return offsets;
    }

    /** The size a flex item asks for along the main axis, before any of the free space is handed out. */
    private void sizeItem(FlexItem item, ElementStyle parentStyle, int contentX, int originY,
                          int width, int cbHeight, int mainSize, boolean row, ElementStyle.AlignItems align){
        if (item.isText()) {
            sizeTextItem(item, width, mainSize, row);
            return;
        }

        Length basis = item.style.get(ElementStyle.FLEX_BASIS);
        Length size = item.style.get(row ? ElementStyle.WIDTH : ElementStyle.HEIGHT);
        Length used = basis != null && !basis.isAuto() ? basis : size;
        int against = row ? width : Math.max(0, mainSize);

        if (Length.isDefinite(used)) {
            int resolved = used.resolve(against, viewWidth, viewHeight, 0);
            if (!isBorderBox(item.style)) {
                Edges frame = frame(item.style, width);
                resolved += row ? frame.horizontal() : frame.vertical();
            }
            item.base = resolved;
        } else if (row) {
            // as wide as its content wants to be, which is what auto means for an item on a row
            item.base = intrinsic(item.element, item.style, width).max();
        } else {
            // down a column, how tall an item is only comes out of laying it out
            item.box = layoutItem(item, parentStyle, contentX, originY, width, cbHeight, row, UNFORCED,
                    crossForce(item, width, row, align));
            item.base = item.box == null ? 0 : item.box.height();
        }

        // css gives a flex item a minimum of its own content, so it cannot be squashed away to nothing.
        // It is what the content needs rather than what the size asked for, and never more than the size
        // the item was going to be anyway, or a wide empty box could never give any of it back.
        int autoMin = 0;
        if (row && !CTMLScroll.clips(CTMLScroll.overflowX(item.style))) {
            autoMin = Math.min(item.base, contentMinimum(item.element, item.style, width));
        } else if (!row && !CTMLScroll.clips(CTMLScroll.overflowY(item.style))) {
            // down a column it is how tall the content comes out, which is what laying it out says
            Box natural = item.box != null && !Length.isDefinite(item.style.get(ElementStyle.HEIGHT))
                    ? item.box
                    : layoutItem(item, parentStyle, contentX, originY, width, cbHeight, row, UNFORCED,
                            crossForce(item, width, row, align));
            autoMin = Math.min(item.base, natural == null ? 0 : natural.height());
        }
        item.min = item.style.resolve(row ? ElementStyle.MIN_WIDTH : ElementStyle.MIN_HEIGHT,
                row ? width : Math.max(0, cbHeight), viewWidth, viewHeight, autoMin);
        item.max = item.style.resolve(row ? ElementStyle.MAX_WIDTH : ElementStyle.MAX_HEIGHT,
                row ? width : Math.max(0, cbHeight), viewWidth, viewHeight, Integer.MAX_VALUE);

        item.grow = Math.max(0, item.style.get(ElementStyle.FLEX_GROW, 0));
        item.shrink = Math.max(0, item.style.get(ElementStyle.FLEX_SHRINK, 1));
        item.hypothetical = clamp(item.base, item.min, item.max);
        item.main = item.hypothetical;
    }

    /**
     * The anonymous text item: as wide as the text with nothing wrapping, as narrow as its longest word,
     * and like any anonymous item it does not grow but will give room back.
     */
    private void sizeTextItem(FlexItem item, int width, int mainSize, boolean row){
        int widest = 0;
        int longest = 0;
        for (Item run : item.text) {
            widest += itemWidth(run);
            if (run instanceof Run word && !word.space()) longest = Math.max(longest, word.width());
        }

        if (row) {
            item.base = widest;
            item.min = longest;
            item.max = Integer.MAX_VALUE;
        } else {
            // down a column the text is as wide as the container and only its height is in the main axis
            item.main = width;
            measureTextItem(item, item.style, width);
            item.base = item.textHeight;
            item.min = 0;
            item.max = Integer.MAX_VALUE;
        }

        item.grow = 0;
        item.shrink = 1;
        item.hypothetical = clamp(item.base, item.min, item.max);
        item.main = item.hypothetical;
    }

    /** Text laid out off to one side, ready to be carried over to wherever its item ends up. */
    private record MeasuredText(int height, int baseline, List<TextLine> lines) {}

    private MeasuredText measureText(List<Item> runs, ElementStyle style, int width){
        List<TextLine> lines = new ArrayList<>();
        Placed placed = layoutLines(copyOf(runs), style, 0, 0, 0, Math.max(0, width), lines, new ArrayList<>());
        return new MeasuredText(placed.height(), placed.baselineY(), lines);
    }

    /** Copies measured lines over to where the item was put. */
    private static void placeText(MeasuredText text, int dx, int dy, List<TextLine> into){
        for (TextLine line : text.lines()) {
            into.add(new TextLine(line.text(), line.content(), line.x() + dx, line.y() + dy, line.width(),
                    line.scale(), line.spacing()));
        }
    }

    /** Lays the anonymous item's text out at the width it was given, off to one side, ready to be moved. */
    private void measureTextItem(FlexItem item, ElementStyle style, int width){
        MeasuredText measured = measureText(item.text, style, width);
        item.textHeight = measured.height();
        item.textBaseline = measured.baseline();
        item.measuredLines = measured.lines();
    }

    private static List<Item> copyOf(List<Item> items){
        return new ArrayList<>(items); // placeLine eats the trailing spaces off the list it is handed
    }

    /**
     * Hands the free space on a line out by flex-grow, or takes the overflow back by flex-shrink weighted
     * by how big each item is, freezing anything that runs into its own minimum or maximum and sharing the
     * rest out again, the way css settles it.
     */
    private void resolveFlexible(List<FlexItem> line, int mainSize, int gap, boolean row){
        for (FlexItem item : line) {
            item.main = item.hypothetical;
            item.frozen = false;
        }
        if (mainSize < 0) return; // nothing to fill, so everything keeps the size it asked for

        int margins = 0;
        for (FlexItem item : line) margins += item.mainMargin(row);
        int available = mainSize - margins - gap * Math.max(0, line.size() - 1);

        int asked = 0;
        for (FlexItem item : line) asked += item.main;
        boolean growing = asked < available;

        for (FlexItem item : line) {
            if (growing ? item.grow == 0 : item.shrink == 0) item.frozen = true;
        }

        for (int pass = 0; pass <= line.size(); pass++) {
            int used = 0;
            for (FlexItem item : line) used += item.main;
            int remaining = available - used;
            if (remaining == 0) break;

            double factors = 0;
            for (FlexItem item : line) {
                if (!item.frozen) factors += growing ? item.grow : (double) item.shrink * Math.max(1, item.base);
            }
            if (factors <= 0) break;

            boolean clamped = false;
            for (FlexItem item : line) {
                if (item.frozen) continue;
                double factor = growing ? item.grow : (double) item.shrink * Math.max(1, item.base);
                int target = item.main + (int) Math.round(remaining * (factor / factors));
                int settled = clamp(target, item.min, item.max);
                item.main = Math.max(0, settled);
                if (settled != target) {
                    item.frozen = true;
                    clamped = true;
                }
            }
            if (!clamped) break;
        }

        // rounding can leave a pixel or two over; the last item that is still free to move takes them
        int used = 0;
        for (FlexItem item : line) used += item.main;
        int left = available - used;
        if (left == 0) return;
        for (int i = line.size() - 1; i >= 0; i--) {
            FlexItem item = line.get(i);
            if (growing ? item.grow == 0 : item.shrink == 0) continue;
            int settled = clamp(item.main + left, item.min, item.max);
            if (settled == item.main) continue;
            item.main = Math.max(0, settled);
            return;
        }
    }

    private Box layoutItem(FlexItem item, ElementStyle parentStyle, int contentX, int originY,
                           int width, int cbHeight, boolean row, int main, int cross){
        if (item.isText()) {
            measureTextItem(item, item.style, row ? main : (cross == UNFORCED ? width : cross));
            return null;
        }
        return layout(item.element, parentStyle, contentX, originY, width, cbHeight, false,
                row ? main : cross, row ? cross : main, false);
    }

    /**
     * How wide a flex item should be told to be across a column's cross axis: stretch fills the line, and
     * anything else is only as wide as its content wants, which is what css shrinks a non stretched item to.
     */
    private int crossForce(FlexItem item, int width, boolean row, ElementStyle.AlignItems align){
        if (row || item.isText()) return UNFORCED;
        if (alignOf(item, align) == ElementStyle.AlignItems.STRETCH) return UNFORCED;
        if (Length.isDefinite(item.style.get(ElementStyle.WIDTH))) return UNFORCED;
        return shrinkToFit(item.element, item.style, width, width) + frame(item.style, width).horizontal();
    }

    /** Where an item's text sits, which is what baseline alignment lines up. */
    private int itemBaseline(FlexItem item, boolean row){
        if (!row) return 0; // down a column there is nothing to line up sideways
        if (item.isText()) return item.textBaseline == NO_BASELINE ? item.textHeight : item.textBaseline;
        if (item.box == null) return 0;
        return item.box.baseline() == NO_BASELINE ? item.box.height() : item.box.baseline();
    }

    private static ElementStyle.AlignItems alignOf(FlexItem item, ElementStyle.AlignItems fallback){
        return item.style.get(ElementStyle.ALIGN_SELF, fallback);
    }

    // ---- grid ----

    /** An item on the grid: where it sits, how many tracks it covers, and the box for it. */
    private static final class GridItem {
        final DOMTreeElement element;
        final ElementStyle style;
        final Edges margin;
        final List<Item> text;
        int column;
        int row;
        int columnSpan = 1;
        int rowSpan = 1;
        Box box;
        MeasuredText measured;
        int forcedWidth = UNFORCED;

        GridItem(DOMTreeElement element, ElementStyle style, Edges margin, List<Item> text){
            this.element = element;
            this.style = style;
            this.margin = margin;
            this.text = text;
        }

        boolean isText(){ return element == null; }
        int columnEnd(){ return column + columnSpan; }
        int rowEnd(){ return row + rowSpan; }
    }

    /**
     * A grid: tracks across and down, items placed on them by hand or filled in by the auto flow, the
     * tracks sized from what they hold and from whatever fr shares are left over, and each item lined up
     * in its cell. One track list per axis, so no named lines, no areas and no dense packing.
     */
    private Flow layoutGrid(DOMTreeElement element, ElementStyle style, int contentX, int contentY,
                            int width, int cbHeight, List<TextLine> lines, List<Box> boxes){
        int columnGap = style.resolve(ElementStyle.COLUMN_GAP, width, viewWidth, viewHeight, 0);
        int rowGap = style.resolve(ElementStyle.ROW_GAP, Math.max(0, cbHeight), viewWidth, viewHeight, 0);

        List<Track> columnTemplate = template(style, ElementStyle.GRID_TEMPLATE_COLUMNS);
        List<Track> rowTemplate = template(style, ElementStyle.GRID_TEMPLATE_ROWS);
        Track autoColumn = implicitTrack(style, ElementStyle.GRID_AUTO_COLUMNS);
        Track autoRow = implicitTrack(style, ElementStyle.GRID_AUTO_ROWS);
        boolean rowFlow = style.get(ElementStyle.GRID_AUTO_FLOW, ElementStyle.GridFlow.ROW) == ElementStyle.GridFlow.ROW;

        List<GridItem> items = new ArrayList<>();

        // a run of text in a grid container is an item of its own, the same as it is in a flex one
        List<Item> own = new ArrayList<>();
        addText(own, text(element), style);
        for (DOMTreeNode node : element.getChildren()) {
            if (node instanceof DOMTreeText run) addText(own, run.getText(), style);
        }
        if (!own.isEmpty()) items.add(new GridItem(null, style, new Edges(0, 0, 0, 0), own));

        for (DOMTreeElement child : inFlow(element)) {
            ElementStyle childStyle = child.getStyle().inheritFrom(style);
            if (childStyle.get(ElementStyle.DISPLAY) == ElementStyle.Display.NONE) continue;
            items.add(new GridItem(child, childStyle, marginEdges(childStyle, width), null));
        }
        if (items.isEmpty()) return new Flow(0, NO_BASELINE);

        // how many tracks there are across: the template, or as many as the items ask for
        int columnCount = Math.max(1, columnTemplate.isEmpty() ? 0 : columnTemplate.size());
        if (columnTemplate.isEmpty()) {
            int asked = 1;
            for (GridItem item : items) asked = Math.max(asked, spanOf(item, true, Integer.MAX_VALUE));
            columnCount = asked;
        }

        // how far down the grid goes before the flow moves along: the rows it was given, or one when
        // there are none and the columns are the implicit axis
        int rowLimit = rowTemplate.isEmpty() ? 1 : rowTemplate.size();
        place(items, columnCount, rowFlow, rowLimit);

        int rowCount = Math.max(rowTemplate.size(), 1);
        for (GridItem item : items) rowCount = Math.max(rowCount, item.rowEnd());

        // ---- the columns, which only the content and the room across can settle ----
        int[] columns = new int[columnCount];
        boolean[] flexibleColumn = new boolean[columnCount];
        float[] columnShare = new float[columnCount];

        for (int c = 0; c < columnCount; c++) {
            Track track = c < columnTemplate.size() ? columnTemplate.get(c) : autoColumn;
            if (track.isFraction()) {
                flexibleColumn[c] = true;
                columnShare[c] = track.fraction();
            } else if (track.isDefinite()) {
                columns[c] = Math.max(0, track.length().resolve(width, viewWidth, viewHeight, 0));
            } else {
                columns[c] = contentTrack(items, c, width, track);
            }
        }

        int columnsUsed = columnGap * (columnCount - 1);
        for (int size : columns) columnsUsed += size;
        shareOut(columns, flexibleColumn, columnShare, width - columnsUsed);

        ElementStyle.AlignItems alignItems = style.get(ElementStyle.ALIGN_ITEMS, ElementStyle.AlignItems.STRETCH);
        ElementStyle.AlignItems justifyItems = style.get(ElementStyle.JUSTIFY_ITEMS, ElementStyle.AlignItems.STRETCH);

        // ---- the rows, which need the items laid out at the width they ended up with ----
        for (GridItem item : items) {
            int cell = spanOf(columns, item.column, item.columnSpan, columnGap);
            item.forcedWidth = cellWidth(item, cell, item.style.get(ElementStyle.JUSTIFY_SELF, justifyItems));
            measureItem(item, style, contentX, contentY, cell, cbHeight);
        }

        int[] rows = new int[rowCount];
        boolean[] flexibleRow = new boolean[rowCount];
        float[] rowShare = new float[rowCount];

        for (int r = 0; r < rowCount; r++) {
            Track track = r < rowTemplate.size() ? rowTemplate.get(r) : autoRow;
            if (track.isFraction()) {
                flexibleRow[r] = true;
                rowShare[r] = track.fraction();
            } else if (track.isDefinite() && cbHeight >= 0) {
                rows[r] = Math.max(0, track.length().resolve(cbHeight, viewWidth, viewHeight, 0));
            } else if (track.isDefinite()) {
                rows[r] = Math.max(0, track.length().resolve(0, viewWidth, viewHeight, 0));
            } else {
                rows[r] = contentRow(items, r);
            }
        }

        int rowsUsed = rowGap * (rowCount - 1);
        for (int size : rows) rowsUsed += size;
        if (cbHeight >= 0) shareOut(rows, flexibleRow, rowShare, cbHeight - rowsUsed); // fr only has room when there is a height

        // ---- and now everything goes in its cell ----
        int baseline = NO_BASELINE;
        int bottom = contentY;

        for (GridItem item : items) {
            int cellX = contentX + offsetOf(columns, item.column, columnGap);
            int cellY = contentY + offsetOf(rows, item.row, rowGap);
            int cellWidth = spanOf(columns, item.column, item.columnSpan, columnGap);
            int cellHeight = spanOf(rows, item.row, item.rowSpan, rowGap);

            ElementStyle.AlignItems across = item.style.get(ElementStyle.JUSTIFY_SELF, justifyItems);
            ElementStyle.AlignItems down = item.style.get(ElementStyle.ALIGN_SELF, alignItems);

            if (item.isText()) {
                int textWidth = Math.max(0, cellWidth - item.margin.horizontal());
                if (item.measured == null || across == ElementStyle.AlignItems.STRETCH) {
                    item.measured = measureText(item.text, item.style, textWidth);
                }
                int dx = cellX + lineUp(across, cellWidth, textWidth);
                int dy = cellY + lineUp(down, cellHeight, item.measured.height());
                placeText(item.measured, dx - contentX, dy - contentY, lines);
                if (baseline == NO_BASELINE) baseline = item.measured.baseline() + dy - contentY;
                bottom = Math.max(bottom, dy + item.measured.height());
                continue;
            }

            int forcedHeight = down == ElementStyle.AlignItems.STRETCH && !Length.isDefinite(item.style.get(ElementStyle.HEIGHT))
                    ? Math.max(0, cellHeight - item.margin.vertical())
                    : UNFORCED;

            Box box = layout(item.element, style, contentX, contentY, cellWidth, cellHeight,
                    false, item.forcedWidth, forcedHeight, false);
            if (box == null) continue;

            int targetX = cellX + item.margin.left + lineUp(across, cellWidth, box.width() + item.margin.horizontal());
            int targetY = cellY + item.margin.top + lineUp(down, cellHeight, box.height() + item.margin.vertical());
            Box placed = shift(box, targetX - box.flowX(), targetY - box.flowY());
            boxes.add(placed);

            if (baseline == NO_BASELINE && placed.baseline() != NO_BASELINE) {
                baseline = placed.flowY() + placed.baseline() - contentY;
            }
            bottom = Math.max(bottom, placed.flowY() + placed.height() + item.margin.bottom);
        }

        int total = rowGap * (rowCount - 1);
        for (int size : rows) total += size;
        return new Flow(Math.max(total, bottom - contentY), baseline);
    }

    /** Where an item goes when it does not say: the next free cells, going along the flow axis. */
    private static void place(List<GridItem> items, int columnCount, boolean rowFlow, int rowLimit){
        List<boolean[]> taken = new ArrayList<>();
        int cursorColumn = 0;
        int cursorRow = 0;

        for (GridItem item : items) {
            Integer columnStart = item.style.get(ElementStyle.GRID_COLUMN_START);
            Integer rowStart = item.style.get(ElementStyle.GRID_ROW_START);
            item.columnSpan = Math.max(1, Math.min(columnCount, spanOf(item, true, columnCount)));
            item.rowSpan = Math.max(1, spanOf(item, false, Integer.MAX_VALUE));

            boolean fixedColumn = columnStart != null;
            boolean fixedRow = rowStart != null;
            int column = fixedColumn ? Math.max(0, columnStart - 1) : 0;
            int row = fixedRow ? Math.max(0, rowStart - 1) : 0;

            if (fixedColumn && fixedRow) {
                item.column = column;
                item.row = row;
            } else if (fixedColumn) {
                // down the column it was given, as far as the first free run of cells
                int r = 0;
                while (!free(taken, r, column, item.columnSpan, item.rowSpan, columnCount)) r++;
                item.column = column;
                item.row = r;
            } else if (fixedRow) {
                int c = 0;
                while (c + item.columnSpan <= columnCount
                        && !free(taken, row, c, item.columnSpan, item.rowSpan, columnCount)) c++;
                item.column = Math.min(c, Math.max(0, columnCount - item.columnSpan));
                item.row = row;
            } else if (rowFlow) {
                int r = cursorRow;
                int c = cursorColumn;
                while (true) {
                    if (c + item.columnSpan > columnCount) { c = 0; r++; continue; }
                    if (free(taken, r, c, item.columnSpan, item.rowSpan, columnCount)) break;
                    c++;
                }
                item.column = c;
                item.row = r;
                cursorColumn = c + item.columnSpan;
                cursorRow = r;
            } else {
                // filling down the columns instead: on down this one, then along to the next
                int c = cursorColumn;
                int r = cursorRow;
                while (true) {
                    if (r + item.rowSpan > Math.max(rowLimit, item.rowSpan)) { r = 0; c++; continue; }
                    if (free(taken, r, c, item.columnSpan, item.rowSpan, Math.max(columnCount, c + item.columnSpan))) break;
                    r++;
                }
                item.column = c;
                item.row = r;
                cursorColumn = c;
                cursorRow = r + item.rowSpan;
            }

            mark(taken, item, columnCount);
        }
    }

    private static int spanOf(GridItem item, boolean across, int limit){
        Integer span = item.style.get(across ? ElementStyle.GRID_COLUMN_SPAN : ElementStyle.GRID_ROW_SPAN);
        if (span != null) return span == Integer.MAX_VALUE ? limit : Math.max(1, span);

        Integer start = item.style.get(across ? ElementStyle.GRID_COLUMN_START : ElementStyle.GRID_ROW_START);
        Integer end = item.style.get(across ? ElementStyle.GRID_COLUMN_END : ElementStyle.GRID_ROW_END);
        if (start != null && end != null) return Math.max(1, end - start);
        if (end != null && start == null) return 1;
        return 1;
    }

    private static boolean free(List<boolean[]> taken, int row, int column, int columnSpan, int rowSpan, int columnCount){
        if (column < 0 || column + columnSpan > columnCount) return false;
        for (int r = row; r < row + rowSpan; r++) {
            if (r >= taken.size()) continue;
            boolean[] cells = taken.get(r);
            for (int c = column; c < column + columnSpan; c++) if (cells[c]) return false;
        }
        return true;
    }

    private static void mark(List<boolean[]> taken, GridItem item, int columnCount){
        while (taken.size() < item.rowEnd()) taken.add(new boolean[columnCount]);
        for (int r = item.row; r < item.rowEnd(); r++) {
            boolean[] cells = taken.get(r);
            for (int c = item.column; c < Math.min(columnCount, item.columnEnd()); c++) cells[c] = true;
        }
    }

    /** An auto column is as wide as the widest thing in it wants to be. */
    private int contentTrack(List<GridItem> items, int column, int basis, Track track){
        int size = 0;
        for (GridItem item : items) {
            if (item.column != column || item.columnSpan != 1) continue; // a spanning item is not one track's doing
            if (item.isText()) {
                int widest = 0;
                for (Item run : item.text) widest += itemWidth(run);
                size = Math.max(size, widest);
                continue;
            }
            Intrinsic inner = intrinsic(item.element, item.style, basis);
            int wanted = track != null && track.length() != null && track.length().unit() == Length.Unit.MIN_CONTENT
                    ? inner.min()
                    : inner.max();
            size = Math.max(size, wanted + item.margin.horizontal());
        }
        return size;
    }

    /** An auto row is as tall as the tallest thing in it came out, once it was laid out at its width. */
    private static int contentRow(List<GridItem> items, int row){
        int size = 0;
        for (GridItem item : items) {
            if (item.row != row || item.rowSpan != 1) continue;
            int height = item.isText()
                    ? (item.measured == null ? 0 : item.measured.height())
                    : (item.box == null ? 0 : item.box.height() + item.margin.vertical());
            size = Math.max(size, height);
        }
        return size;
    }

    /**
     * How wide an item should be told to be in its cell: a stretched one fills it, and anything else is
     * only as wide as its content wants to be, which is what css shrinks an unstretched item to.
     */
    private int cellWidth(GridItem item, int cell, ElementStyle.AlignItems across){
        int room = Math.max(0, cell - item.margin.horizontal());
        if (item.isText() || Length.isDefinite(item.style.get(ElementStyle.WIDTH))) return UNFORCED;
        if (across == ElementStyle.AlignItems.STRETCH) return room;
        return shrinkToFit(item.element, item.style, cell, room) + frame(item.style, cell).horizontal();
    }

    /** Lays an item out at the width of the cells it covers, which is what its height comes out of. */
    private void measureItem(GridItem item, ElementStyle parentStyle, int contentX, int contentY,
                             int cell, int cbHeight){
        if (item.isText()) {
            item.measured = measureText(item.text, item.style, Math.max(0, cell - item.margin.horizontal()));
            return;
        }
        item.box = layout(item.element, parentStyle, contentX, contentY, cell, cbHeight, false,
                item.forcedWidth, UNFORCED, false);
    }

    /** Hands whatever room is left to the fr tracks, by their shares, to the pixel. */
    private static void shareOut(int[] sizes, boolean[] flexible, float[] shares, int free){
        float total = 0;
        for (int i = 0; i < sizes.length; i++) if (flexible[i]) total += shares[i];
        if (total <= 0 || free <= 0) return;

        int given = 0;
        float running = 0;
        for (int i = 0; i < sizes.length; i++) {
            if (!flexible[i]) continue;
            running += shares[i];
            int upTo = Math.round(free * (running / total));
            sizes[i] = upTo - given;
            given = upTo;
        }
    }

    /** How far along the axis a track starts, gaps included. */
    private static int offsetOf(int[] sizes, int index, int gap){
        int offset = 0;
        for (int i = 0; i < index && i < sizes.length; i++) offset += sizes[i] + gap;
        return offset;
    }

    /** How much room a run of tracks gives an item, the gaps between them included. */
    private static int spanOf(int[] sizes, int from, int count, int gap){
        int total = 0;
        for (int i = from; i < from + count && i < sizes.length; i++) {
            total += sizes[i];
            if (i > from) total += gap;
        }
        return Math.max(0, total);
    }

    /** Where something smaller than its cell sits in it. */
    private static int lineUp(ElementStyle.AlignItems align, int room, int size){
        return switch (align) {
            case CENTER -> Math.max(0, room - size) / 2;
            case END -> Math.max(0, room - size);
            default -> 0;
        };
    }

    private static List<Track> template(ElementStyle style, ElementStyle.Key<ElementStyle.Tracks> key){
        ElementStyle.Tracks tracks = style.get(key);
        return tracks == null ? List.of() : tracks.items();
    }

    private static Track implicitTrack(ElementStyle style, ElementStyle.Key<ElementStyle.Tracks> key){
        ElementStyle.Tracks tracks = style.get(key);
        return tracks == null || tracks.items().isEmpty() ? Track.AUTO : tracks.items().get(0);
    }

    // ---- tables ----

    /** One cell of a table: where it ended up on the grid of them, and how much of it it takes. */
    private static final class Cell {
        final DOMTreeElement element;
        final ElementStyle style;
        final Edges margin;
        int row;
        int column;
        int columnSpan = 1;
        int rowSpan = 1;
        Box box;

        Cell(DOMTreeElement element, ElementStyle style, Edges margin){
            this.element = element;
            this.style = style;
            this.margin = margin;
        }
    }

    /** One row: where it came from, the group it belongs to, and the cells that start on it. */
    private static final class Row {
        final DOMTreeElement element;
        final ElementStyle style;
        final DOMTreeElement group;
        final List<Cell> cells = new ArrayList<>();
        int y;
        int height;

        Row(DOMTreeElement element, ElementStyle style, DOMTreeElement group){
            this.element = element;
            this.style = style;
            this.group = group;
        }
    }

    /**
     * A table: a caption, then the head, the body and the foot in that order whatever order they were
     * written in, rows down and cells across. Columns are as wide as what is in them, scaled back to fit
     * where they ask for more than there is, and a row is as tall as its tallest cell. colspan and rowspan
     * say how many of each a cell takes, and the rest of the row flows around it.
     */
    private Flow layoutTable(DOMTreeElement element, ElementStyle style, int contentX, int contentY,
                             int width, int cbHeight, List<TextLine> lines, List<Box> boxes){
        List<Row> rows = new ArrayList<>();
        List<DOMTreeElement> captions = new ArrayList<>();
        collectRows(element, style, width, rows, captions);
        if (rows.isEmpty() && captions.isEmpty()) return new Flow(0, NO_BASELINE);

        int columnCount = placeCells(rows);
        boolean collapsed = style.get(ElementStyle.BORDER_COLLAPSE, ElementStyle.BorderCollapse.SEPARATE)
                == ElementStyle.BorderCollapse.COLLAPSE;

        // collapsed borders sit on top of one another instead of each keeping their own room, which is a
        // negative gap: the cell after shares the edge of the one before it
        int shared = collapsed ? sharedBorder(rows) : 0;
        int columnGap = collapsed ? -shared : style.resolve(ElementStyle.COLUMN_GAP, width, viewWidth, viewHeight, 0);
        int rowGap = collapsed ? -shared : style.resolve(ElementStyle.ROW_GAP, Math.max(0, cbHeight), viewWidth, viewHeight, 0);

        boolean fixed = style.get(ElementStyle.TABLE_LAYOUT, ElementStyle.TableLayout.AUTO) == ElementStyle.TableLayout.FIXED;
        int[] columns = columnSizes(rows, columnCount, width, columnGap, fixed);

        int cursorY = contentY;
        int baseline = NO_BASELINE;

        // a caption on top comes before anything else, and takes the whole width of the table
        for (DOMTreeElement caption : captions) {
            if (side(caption, style) != ElementStyle.CaptionSide.TOP) continue;
            Box box = layout(caption, style, contentX, cursorY, width, cbHeight);
            if (box == null) continue;
            boxes.add(box);
            cursorY = box.flowY() + box.height();
            if (baseline == NO_BASELINE && box.baseline() != NO_BASELINE) {
                baseline = box.flowY() + box.baseline() - contentY;
            }
        }

        // every cell at the width of the columns it covers, which is what the row heights come out of
        for (Row row : rows) {
            for (Cell cell : row.cells) {
                int cellWidth = spanOf(columns, cell.column, cell.columnSpan, columnGap) - cell.margin.horizontal();
                cell.box = layout(cell.element, row.style, contentX, cursorY, Math.max(0, cellWidth), cbHeight,
                        false, Math.max(0, cellWidth), UNFORCED, false);
            }
        }
        rowSizes(rows, rowGap);

        int tableTop = cursorY;
        for (Row row : rows) {
            row.y = cursorY;
            cursorY += row.height + rowGap;
        }
        if (!rows.isEmpty()) cursorY -= rowGap;

        // and then each cell stretched to the rows and columns it covers
        for (Row row : rows) {
            List<Box> cellBoxes = new ArrayList<>();
            for (Cell cell : row.cells) {
                int cellWidth = spanOf(columns, cell.column, cell.columnSpan, columnGap) - cell.margin.horizontal();
                int cellHeight = rowSpanHeight(rows, cell, rowGap) - cell.margin.vertical();
                int x = contentX + offsetOf(columns, cell.column, columnGap) + cell.margin.left;
                int y = row.y + cell.margin.top;

                Box box = layout(cell.element, row.style, x, y, Math.max(0, cellWidth), cellHeight, false,
                        Math.max(0, cellWidth), Math.max(0, cellHeight), false);
                if (box == null) continue;
                cellBoxes.add(box);
                if (baseline == NO_BASELINE && box.baseline() != NO_BASELINE) {
                    baseline = box.flowY() + box.baseline() - contentY;
                }
            }
            row.cells.clear();
            rowBoxes.put(row, cellBoxes);
        }

        // the rows, gathered back under the groups they came from so a group can carry a background
        List<Box> placed = new ArrayList<>();
        DOMTreeElement group = null;
        List<Box> groupRows = new ArrayList<>();
        int groupTop = tableTop;

        for (Row row : rows) {
            Box box = row.element == null
                    ? null
                    : rowBox(row.element, row.style, contentX, row.y, width, row.height, rowBoxes.get(row));

            if (row.group != group) {
                closeGroup(group, style, contentX, groupTop, width, groupRows, placed, rowGap);
                group = row.group;
                groupRows = new ArrayList<>();
                groupTop = row.y;
            }

            if (box == null) groupRows.addAll(rowBoxes.get(row));
            else groupRows.add(box);
        }
        closeGroup(group, style, contentX, groupTop, width, groupRows, placed, rowGap);
        boxes.addAll(placed);
        rowBoxes.clear();

        // and a caption underneath, for the tables that want it there
        for (DOMTreeElement caption : captions) {
            if (side(caption, style) != ElementStyle.CaptionSide.BOTTOM) continue;
            Box box = layout(caption, style, contentX, cursorY, width, cbHeight);
            if (box == null) continue;
            boxes.add(box);
            cursorY = box.flowY() + box.height();
        }

        return new Flow(cursorY - contentY, baseline);
    }

    /** The cell boxes of each row while the table is being put together. */
    private final Map<Row, List<Box>> rowBoxes = new HashMap<>();

    /**
     * Gathers a table's rows in the order they are laid out: the head first, then the body, then the foot,
     * whatever order they were written in. Cells put straight into the table get a row of their own.
     */
    private void collectRows(DOMTreeElement element, ElementStyle style, int width,
                             List<Row> rows, List<DOMTreeElement> captions){
        List<Row> head = new ArrayList<>();
        List<Row> body = new ArrayList<>();
        List<Row> foot = new ArrayList<>();
        Row loose = null;

        for (DOMTreeElement child : inFlow(element)) {
            ElementStyle childStyle = child.getStyle().inheritFrom(style);
            switch (childStyle.get(ElementStyle.DISPLAY, ElementStyle.Display.BLOCK)) {
                case NONE -> { }
                case TABLE_CAPTION -> captions.add(child);
                case TABLE_HEADER_GROUP -> collectGroup(child, childStyle, width, head);
                case TABLE_FOOTER_GROUP -> collectGroup(child, childStyle, width, foot);
                case TABLE_ROW_GROUP -> collectGroup(child, childStyle, width, body);
                case TABLE_ROW -> body.add(row(child, childStyle, null, width));
                default -> {
                    // a cell with no row around it: html would wrap it in one, so that is what happens here
                    if (loose == null) {
                        loose = new Row(null, style, null);
                        body.add(loose);
                    }
                    loose.cells.add(new Cell(child, childStyle, marginEdges(childStyle, width)));
                }
            }
        }

        rows.addAll(head);
        rows.addAll(body);
        rows.addAll(foot);
    }

    private void collectGroup(DOMTreeElement group, ElementStyle groupStyle, int width, List<Row> into){
        for (DOMTreeElement child : inFlow(group)) {
            ElementStyle childStyle = child.getStyle().inheritFrom(groupStyle);
            if (childStyle.get(ElementStyle.DISPLAY) == ElementStyle.Display.NONE) continue;
            into.add(row(child, childStyle, group, width));
        }
    }

    private Row row(DOMTreeElement element, ElementStyle style, DOMTreeElement group, int width){
        Row row = new Row(element, style, group);
        for (DOMTreeElement child : inFlow(element)) {
            ElementStyle cellStyle = child.getStyle().inheritFrom(style);
            if (cellStyle.get(ElementStyle.DISPLAY) == ElementStyle.Display.NONE) continue;
            row.cells.add(new Cell(child, cellStyle, marginEdges(cellStyle, width)));
        }
        return row;
    }

    /**
     * Puts every cell on the grid of rows and columns, stepping over the ones a colspan or a rowspan has
     * already taken, and hands back how many columns the table ended up needing.
     */
    private static int placeCells(List<Row> rows){
        Set<Long> taken = new HashSet<>();
        int columnCount = 0;

        for (int r = 0; r < rows.size(); r++) {
            int column = 0;
            for (Cell cell : rows.get(r).cells) {
                while (taken.contains(cellAt(r, column))) column++;

                cell.row = r;
                cell.column = column;
                cell.columnSpan = Math.max(1, span(cell.element, "colspan"));
                cell.rowSpan = Math.clamp(span(cell.element, "rowspan"), 1, rows.size() - r);

                for (int rr = r; rr < r + cell.rowSpan; rr++) {
                    for (int cc = column; cc < column + cell.columnSpan; cc++) taken.add(cellAt(rr, cc));
                }

                column += cell.columnSpan;
                columnCount = Math.max(columnCount, column);
            }
        }

        return Math.max(1, columnCount);
    }

    private static long cellAt(int row, int column){
        return ((long) row << 32) | (column & 0xFFFFFFFFL);
    }

    /** colspan and rowspan are properties of the cell, the way html has them, not styles. */
    private static int span(DOMTreeElement element, String name){
        return element.getPropertyValue(name) instanceof Number span && span.intValue() > 0 ? span.intValue() : 1;
    }

    /** How wide each column has to be, the cells spanning several of them topping up what they cover. */
    private int[] columnSizes(List<Row> rows, int columnCount, int width, int gap, boolean fixed){
        int[] wanted = new int[columnCount];
        int[] least = new int[columnCount];

        if (!fixed) {
            for (Row row : rows) {
                for (Cell cell : row.cells) {
                    if (cell.columnSpan != 1) continue;
                    Intrinsic inner = intrinsic(cell.element, cell.style, width);
                    wanted[cell.column] = Math.max(wanted[cell.column], inner.max() + cell.margin.horizontal());
                    least[cell.column] = Math.max(least[cell.column], inner.min() + cell.margin.horizontal());
                }
            }

            // a cell across several columns only asks that they add up to enough between them
            List<Cell> spanning = new ArrayList<>();
            for (Row row : rows) for (Cell cell : row.cells) if (cell.columnSpan > 1) spanning.add(cell);
            spanning.sort(Comparator.comparingInt(cell -> cell.columnSpan));

            for (Cell cell : spanning) {
                Intrinsic inner = intrinsic(cell.element, cell.style, width);
                int gaps = gap * (cell.columnSpan - 1);
                topUp(wanted, cell.column, cell.columnSpan, inner.max() + cell.margin.horizontal() - gaps);
                topUp(least, cell.column, cell.columnSpan, inner.min() + cell.margin.horizontal() - gaps);
            }
        }

        int room = Math.max(0, width - gap * (columnCount - 1));
        return shareColumns(wanted, least, room, fixed);
    }

    /** Shares a shortfall out over the tracks a spanning cell covers, evenly, pixel by pixel. */
    private static void topUp(int[] sizes, int from, int count, int needed){
        int have = 0;
        for (int i = from; i < from + count && i < sizes.length; i++) have += sizes[i];
        if (have >= needed) return;

        int short_ = needed - have;
        for (int i = 0; i < count && from + i < sizes.length; i++) {
            sizes[from + i] += short_ / count + (i < short_ % count ? 1 : 0);
        }
    }

    /** How tall each row has to be, the cells spanning several of them topping up what they cover. */
    private static void rowSizes(List<Row> rows, int gap){
        for (Row row : rows) {
            for (Cell cell : row.cells) {
                if (cell.rowSpan != 1 || cell.box == null) continue;
                row.height = Math.max(row.height, cell.box.height() + cell.margin.vertical());
            }
        }

        for (Row row : rows) {
            for (Cell cell : row.cells) {
                if (cell.rowSpan == 1 || cell.box == null) continue;
                int needed = cell.box.height() + cell.margin.vertical() - gap * (cell.rowSpan - 1);
                int have = 0;
                for (int r = cell.row; r < cell.row + cell.rowSpan && r < rows.size(); r++) have += rows.get(r).height;
                if (have >= needed) continue;

                int short_ = needed - have;
                for (int i = 0; i < cell.rowSpan && cell.row + i < rows.size(); i++) {
                    rows.get(cell.row + i).height += short_ / cell.rowSpan + (i < short_ % cell.rowSpan ? 1 : 0);
                }
            }
        }
    }

    /** How tall a cell gets to be: its own row, plus any it reaches down into and the gaps between. */
    private static int rowSpanHeight(List<Row> rows, Cell cell, int gap){
        int height = 0;
        for (int r = cell.row; r < cell.row + cell.rowSpan && r < rows.size(); r++) {
            height += rows.get(r).height;
            if (r > cell.row) height += gap;
        }
        return height;
    }

    /** The border two cells share once they are collapsed, taken from the first cell that has one. */
    private static int sharedBorder(List<Row> rows){
        for (Row row : rows) {
            for (Cell cell : row.cells) {
                int border = Math.max(
                        borderWidth(cell.style, ElementStyle.BORDER_LEFT_WIDTH, ElementStyle.BORDER_LEFT_STYLE),
                        borderWidth(cell.style, ElementStyle.BORDER_TOP_WIDTH, ElementStyle.BORDER_TOP_STYLE));
                if (border > 0) return border;
            }
        }
        return 0;
    }

    /** Gives a row group a box of its own around the rows it holds, so it can carry a background. */
    private void closeGroup(DOMTreeElement group, ElementStyle style, int contentX, int top, int width,
                            List<Box> rows, List<Box> into, int gap){
        if (rows.isEmpty()) return;
        if (group == null) {
            into.addAll(rows);
            return;
        }

        int bottom = top;
        for (Box row : rows) bottom = Math.max(bottom, row.flowY() + row.height());
        into.add(rowBox(group, group.getStyle().inheritFrom(style), contentX, top, width, bottom - top, rows));
    }

    private static ElementStyle.CaptionSide side(DOMTreeElement caption, ElementStyle style){
        return caption.getStyle().inheritFrom(style)
                .get(ElementStyle.CAPTION_SIDE, ElementStyle.CaptionSide.TOP);
    }

    /**
     * Shares the table's width out between its columns: what they asked for where there is room for it,
     * cut back towards what they need where there is not, and evenly where nothing was measured.
     */
    private static int[] shareColumns(int[] wanted, int[] least, int room, boolean fixed){
        int count = wanted.length;
        int[] columns = new int[count];

        if (fixed) {
            for (int c = 0; c < count; c++) {
                columns[c] = room / count + (c < room % count ? 1 : 0);
            }
            return columns;
        }

        int asked = 0;
        int needed = 0;
        for (int c = 0; c < count; c++) {
            asked += wanted[c];
            needed += least[c];
        }

        if (asked <= room) {
            // room to spare: everyone gets what they asked for and the rest is shared out evenly
            int spare = room - asked;
            for (int c = 0; c < count; c++) {
                columns[c] = wanted[c] + spare / count + (c < spare % count ? 1 : 0);
            }
            return columns;
        }

        if (needed >= room) { // nothing to give: everyone is squeezed down to what they need
            System.arraycopy(least, 0, columns, 0, count);
            return columns;
        }

        // somewhere in between: each column gives up the same share of what it asked for over the minimum
        int give = asked - room;
        int over = asked - needed;
        int handed = 0;
        for (int c = 0; c < count; c++) {
            int share = over <= 0 ? 0 : Math.round(give * ((wanted[c] - least[c]) / (float) over));
            if (c == count - 1) share = give - handed; // the last one takes whatever the rounding left
            handed += share;
            columns[c] = Math.max(least[c], wanted[c] - share);
        }
        return columns;
    }

    /** The box a table row gets: the full width of the table, as tall as its cells, holding them. */
    private Box rowBox(DOMTreeElement element, ElementStyle style, int x, int y, int width, int height,
                       List<Box> cells){
        Edges border = borderEdges(style);
        Port port = Port.still(x + border.left, y + border.top,
                Math.max(0, width - border.horizontal()), Math.max(0, height - border.vertical()));
        int baseline = NO_BASELINE;
        for (Box cell : cells) {
            if (cell.baseline() != NO_BASELINE) { baseline = cell.flowY() + cell.baseline() - y; break; }
        }
        return new Box(element, style, x, y, width, height, x, y, width, height, 0, 0, port,
                baseline == NO_BASELINE ? height : baseline, List.of(), List.copyOf(cells));
    }

    // ---- columns ----

    /**
     * Spreads a block's children over several columns, as evenly as whole children allow: nothing is split
     * down the middle, so a child taller than its share of the height simply makes its column taller.
     */
    private Flow layoutColumns(DOMTreeElement element, ElementStyle style, int contentX, int contentY,
                               int width, int cbHeight, List<TextLine> lines, List<Box> boxes){
        int gap = style.resolve(ElementStyle.COLUMN_GAP, width, viewWidth, viewHeight, 0);
        int count = style.get(ElementStyle.COLUMN_COUNT, 0);

        if (count <= 0) { // as many as will fit at the width asked for
            int wide = Math.max(1, style.resolve(ElementStyle.COLUMN_WIDTH, width, viewWidth, viewHeight, width));
            count = Math.max(1, (width + gap) / (wide + gap));
        }
        count = Math.max(1, Math.min(count, Math.max(1, width)));

        int columnWidth = Math.max(0, (width - gap * (count - 1)) / count);

        // lay every child out at the column width, off to one side, to see how tall each of them is
        List<Box> laid = new ArrayList<>();
        List<Edges> margins = new ArrayList<>();
        int total = 0;
        for (DOMTreeElement child : inFlow(element)) {
            ElementStyle childStyle = child.getStyle().inheritFrom(style);
            if (childStyle.get(ElementStyle.DISPLAY) == ElementStyle.Display.NONE) continue;
            Box box = layout(child, style, contentX, contentY, columnWidth, cbHeight);
            if (box == null) continue;
            Edges margin = marginEdges(childStyle, columnWidth);
            laid.add(box);
            margins.add(margin);
            total += box.height() + margin.vertical();
        }
        if (laid.isEmpty()) return new Flow(0, NO_BASELINE);

        // balance them: fill each column up to its share of the whole before moving on to the next
        int target = Math.max(1, (total + count - 1) / count);
        int column = 0;
        int cursorY = contentY;
        int tallest = 0;
        int baseline = NO_BASELINE;

        for (int i = 0; i < laid.size(); i++) {
            Box box = laid.get(i);
            Edges margin = margins.get(i);
            int outer = box.height() + margin.vertical();

            // a column takes children until it has had its share; one that then runs over is still its own
            boolean room = cursorY == contentY || cursorY - contentY < target;
            if (!room && column < count - 1) {
                tallest = Math.max(tallest, cursorY - contentY);
                column++;
                cursorY = contentY;
            }

            int x = contentX + column * (columnWidth + gap);
            Box placed = shift(box, x + margin.left - box.flowX(), cursorY + margin.top - box.flowY());
            boxes.add(placed);
            if (baseline == NO_BASELINE && placed.baseline() != NO_BASELINE) {
                baseline = placed.flowY() + placed.baseline() - contentY;
            }
            cursorY += outer;
        }

        return new Flow(Math.max(tallest, cursorY - contentY), baseline);
    }

    // ---- out of flow ----

    /** Absolute and fixed boxes, placed against this box (or the viewport) and left out of the flow. */
    private void layoutOutOfFlow(DOMTreeElement element, ElementStyle style, int contentX, int contentY,
                                 int contentWidth, int contentHeight, List<Box> boxes){
        for (DOMTreeElement child : element.getChildElements()) {
            ElementStyle childStyle = child.getStyle().inheritFrom(style);
            ElementStyle.Position position = childStyle.get(ElementStyle.POSITION);
            if (position != ElementStyle.Position.ABSOLUTE && position != ElementStyle.Position.FIXED) continue;

            boolean fixed = position == ElementStyle.Position.FIXED;
            int originX = fixed ? 0 : contentX;
            int originY = fixed ? 0 : contentY;
            int boxWidth = fixed ? viewWidth : contentWidth;
            int boxHeight = fixed ? viewHeight : contentHeight;

            Box box = layout(child, style, originX, originY, boxWidth, boxHeight);
            if (box == null) continue;

            int offsetX = 0;
            int offsetY = 0;
            Length left = childStyle.get(ElementStyle.LEFT);
            Length right = childStyle.get(ElementStyle.RIGHT);
            Length top = childStyle.get(ElementStyle.TOP);
            Length bottom = childStyle.get(ElementStyle.BOTTOM);

            if (Length.isDefinite(left)) offsetX = left.resolve(boxWidth, viewWidth, viewHeight, 0);
            else if (Length.isDefinite(right)) offsetX = boxWidth - box.width() - right.resolve(boxWidth, viewWidth, viewHeight, 0);
            if (Length.isDefinite(top)) offsetY = top.resolve(boxHeight, viewWidth, viewHeight, 0);
            else if (Length.isDefinite(bottom)) offsetY = boxHeight - box.height() - bottom.resolve(boxHeight, viewWidth, viewHeight, 0);

            boxes.add(shift(box, offsetX, offsetY));
        }
    }

    // ---- sticky ----

    /**
     * Holds the sticky boxes under a scrollport against its edges as the content goes by, without ever
     * letting one leave the box it sits in, which is what css clamps them to.
     * The walk stops at any box that scrolls in its own right, since that one does this for itself.
     */
    private List<Box> stickAll(List<Box> children, Port port, int left, int top, int width, int height){
        if (!hasSticky(children)) return children;
        List<Box> out = new ArrayList<>(children.size());
        for (Box child : children) out.add(stick(child, port, left, top, left + width, top + height));
        return out;
    }

    private static boolean hasSticky(List<Box> children){
        for (Box child : children) {
            if (child.style().get(ElementStyle.POSITION) == ElementStyle.Position.STICKY) return true;
            if (child.clipsX() || child.clipsY()) continue; // it looks after its own
            if (hasSticky(child.children())) return true;
        }
        return false;
    }

    private Box stick(Box box, Port port, int cbLeft, int cbTop, int cbRight, int cbBottom){
        Box result = box;

        if (box.style().get(ElementStyle.POSITION) == ElementStyle.Position.STICKY) {
            int dx = 0;
            int dy = 0;
            Length top = box.style().get(ElementStyle.TOP);
            Length bottom = box.style().get(ElementStyle.BOTTOM);
            Length left = box.style().get(ElementStyle.LEFT);
            Length right = box.style().get(ElementStyle.RIGHT);

            if (Length.isDefinite(top)) {
                int want = port.y() + top.resolve(port.height(), viewWidth, viewHeight, 0);
                dy = Math.max(dy, Math.min(want - box.y(), cbBottom - box.bottom()));
            }
            if (Length.isDefinite(bottom)) {
                int want = port.bottom() - bottom.resolve(port.height(), viewWidth, viewHeight, 0) - box.height();
                dy = Math.min(dy, Math.max(want - box.y(), cbTop - box.y()));
            }
            if (Length.isDefinite(left)) {
                int want = port.x() + left.resolve(port.width(), viewWidth, viewHeight, 0);
                dx = Math.max(dx, Math.min(want - box.x(), cbRight - box.right()));
            }
            if (Length.isDefinite(right)) {
                int want = port.right() - right.resolve(port.width(), viewWidth, viewHeight, 0) - box.width();
                dx = Math.min(dx, Math.max(want - box.x(), cbLeft - box.x()));
            }
            if (dx != 0 || dy != 0) result = nudge(box, dx, dy);
        }

        if (result.clipsX() || result.clipsY() || result.children().isEmpty()) return result;

        List<Box> children = new ArrayList<>(result.children().size());
        for (Box child : result.children()) {
            children.add(stick(child, port, result.contentX(), result.contentY(),
                    result.contentRight(), result.contentBottom()));
        }
        return withChildren(result, children);
    }

    // ---- dragging ----

    /**
     * Pulls every dragged child back inside the room it is allowed, which is the content box or, where this
     * box scrolls, as much of the scrollable area as everything else reaches. Done on every layout, so a
     * window resize or a gui scale change cannot leave a dragged box stranded where it cannot be put back.
     */
    private List<Box> clampDrags(List<Box> children, int contentX, int contentY, int roomWidth, int roomHeight){
        List<Box> out = null;
        for (int i = 0; i < children.size(); i++) {
            Box child = children.get(i);
            if (child.isFixed() || !child.element().isDragged()) continue;

            int dragX = child.element().getDragX();
            int dragY = child.element().getDragY();
            int flowX = child.flowX();
            int flowY = child.flowY();

            int minX = contentX - flowX;
            int minY = contentY - flowY;
            int maxX = Math.max(minX, contentX + roomWidth - child.width() - flowX);
            int maxY = Math.max(minY, contentY + roomHeight - child.height() - flowY);

            // where the element was put as a share of the room it had then, read back against the room
            // it has now, so the box comes back to the same spot rather than the same pixel count
            float shareX = child.element().getDragShareX();
            float shareY = child.element().getDragShareY();
            int wantedX = shareX >= 0 ? minX + Math.round(shareX * (maxX - minX)) : dragX;
            int wantedY = shareY >= 0 ? minY + Math.round(shareY * (maxY - minY)) : dragY;

            int clampedX = clamp(wantedX, minX, maxX);
            int clampedY = clamp(wantedY, minY, maxY);

            // whatever it ended up at, that is the share to come back to next time the room changes
            child.element().setDragOffset(clampedX, clampedY);
            child.element().setDragShare(
                    maxX > minX ? (clampedX - minX) / (float) (maxX - minX) : shareX,
                    maxY > minY ? (clampedY - minY) / (float) (maxY - minY) : shareY);
            if (clampedX == dragX && clampedY == dragY) continue;

            if (out == null) out = new ArrayList<>(children);
            out.set(i, nudge(child, clampedX - dragX, clampedY - dragY));
        }
        return out == null ? children : out;
    }

    /**
     * How much room a dragged box has inside its parent, so a grab and the layout after it agree on the
     * limits: the content box, grown to whatever else in there reaches further.
     */
    public static int dragRoomWidth(Box parent, Box box){
        int room = parent.contentWidth();
        if (parent.clipsX()) {
            int origin = parent.contentX() - parent.port().scrollX();
            for (Box child : parent.children()) {
                if (child == box || child.isFixed()) continue;
                room = Math.max(room, reachRight(child) - origin);
            }
        }
        return room;
    }

    public static int dragRoomHeight(Box parent, Box box){
        int room = parent.contentHeight();
        if (parent.clipsY()) {
            int origin = parent.contentY() - parent.port().scrollY();
            for (Box child : parent.children()) {
                if (child == box || child.isFixed()) continue;
                room = Math.max(room, reachBottom(child) - origin);
            }
        }
        return room;
    }

    // ---- text ----

    /** Whether the element carries text of its own, which is content and keeps margins apart. */
    private static boolean hasOwnText(DOMTreeElement element){
        String own = text(element);
        return own != null && !own.isBlank();
    }

    private static String text(DOMTreeElement element){
        return element.getString("text"); // as the screen shows it: every ${variable} in it is filled in
    }

    /**
     * How wide the text is on one line, measured the way the flow is about to measure it: a run at a
     * time, with a space between the words. Every run is rounded to whole pixels as it is laid out, so
     * measuring the whole string in one go can come out a pixel short of its pieces, and text given
     * exactly the room this asked for would wrap anyway.
     */
    private int textWidth(String text, ElementStyle style){
        if (text == null || text.isBlank()) return 0;
        float scale = fontScale(style);

        // pre keeps the spacing it was written with and breaks only where it says to, so each of those
        // lines is one run, the way addText makes them
        if (style.get(ElementStyle.WHITE_SPACE) == ElementStyle.WhiteSpace.PRE) {
            int widest = 0;
            for (String line : text.split("\n", -1)) widest = Math.max(widest, runWidth(line, style, scale));
            return widest;
        }

        int width = 0;
        int gaps = -1;
        for (String word : text.strip().split("\\s+")) {
            width += runWidth(word, style, scale);
            gaps++;
        }
        return width + Math.max(0, gaps) * runWidth(" ", style, scale);
    }

    private int longestWord(String text, ElementStyle style){
        if (text == null || text.isBlank()) return 0;
        if (style.get(ElementStyle.WHITE_SPACE, ElementStyle.WhiteSpace.NORMAL) != ElementStyle.WhiteSpace.NORMAL) {
            return textWidth(text, style); // nothing is allowed to wrap, so the whole run is the minimum
        }
        int widest = 0;
        for (String word : text.strip().split("\\s+")) widest = Math.max(widest, textWidth(word, style));
        return widest;
    }

    /** The line box an element's own font asks for, which is line-height wherever that is set. */
    private int lineHeight(ElementStyle style){
        return style.get(ElementStyle.LINE_HEIGHT, fontHeight(style));
    }

    private int fontHeight(ElementStyle style){
        return Math.max(1, Math.round(metrics.lineHeight() * fontScale(style)));
    }

    /** How far above the baseline a line of this style reaches, half of the leading included. */
    private int strutAbove(ElementStyle style){
        int lead = lineHeight(style) - fontHeight(style);
        return Math.round(metrics.ascent() * fontScale(style)) + Math.floorDiv(lead, 2);
    }

    private int strutBelow(ElementStyle style){
        return lineHeight(style) - strutAbove(style);
    }

    public static float fontScale(ElementStyle style){
        return style.get(ElementStyle.FONT_SIZE, BASE_FONT_SIZE) / (float) BASE_FONT_SIZE;
    }

    public static Style textStyle(ElementStyle style){
        Style text = Style.EMPTY;
        if (style.isSet(ElementStyle.BOLD)) text = text.withBold(style.get(ElementStyle.BOLD));
        if (style.isSet(ElementStyle.ITALIC)) text = text.withItalic(style.get(ElementStyle.ITALIC));
        if (style.isSet(ElementStyle.UNDERLINE)) text = text.withUnderlined(style.get(ElementStyle.UNDERLINE));
        if (style.isSet(ElementStyle.STRIKETHROUGH)) text = text.withStrikethrough(style.get(ElementStyle.STRIKETHROUGH));
        return text;
    }

    // ---- odds and ends ----

    /** Children that take part in the flow, so everything bar the absolute and fixed ones. */
    private static List<DOMTreeElement> inFlow(DOMTreeElement element){
        List<DOMTreeElement> children = new ArrayList<>();
        for (DOMTreeElement child : element.getChildElements()) {
            if (isOutOfFlow(child.getStyle())) continue;
            children.add(child);
        }
        return children; // document order: z-index decides what paints on top, not where a box sits
    }

    private static boolean isOutOfFlow(ElementStyle style){
        ElementStyle.Position position = style.get(ElementStyle.POSITION);
        return position == ElementStyle.Position.ABSOLUTE || position == ElementStyle.Position.FIXED;
    }

    /** Tailwind puts everything in border-box, so that is what an unset box-sizing means here. */
    private static boolean isBorderBox(ElementStyle style){
        return style.get(ElementStyle.BOX_SIZING, ElementStyle.BoxSizing.BORDER_BOX) == ElementStyle.BoxSizing.BORDER_BOX;
    }

    /** Inline and inline-block both sit on a line and are only as wide as they need to be. */
    private static boolean isInlineLevel(ElementStyle style){
        ElementStyle.Display display = style.get(ElementStyle.DISPLAY, ElementStyle.Display.BLOCK);
        return display == ElementStyle.Display.INLINE || display == ElementStyle.Display.INLINE_BLOCK;
    }

    private static boolean isRow(ElementStyle style){
        ElementStyle.FlexDirection direction = style.get(ElementStyle.FLEX_DIRECTION, ElementStyle.FlexDirection.ROW);
        return direction == ElementStyle.FlexDirection.ROW || direction == ElementStyle.FlexDirection.ROW_REVERSE;
    }

    private static boolean isReverse(ElementStyle style){
        ElementStyle.FlexDirection direction = style.get(ElementStyle.FLEX_DIRECTION, ElementStyle.FlexDirection.ROW);
        return direction == ElementStyle.FlexDirection.ROW_REVERSE || direction == ElementStyle.FlexDirection.COLUMN_REVERSE;
    }

    private Edges edges(ElementStyle style, ElementStyle.Key<Length> top, ElementStyle.Key<Length> right,
                        ElementStyle.Key<Length> bottom, ElementStyle.Key<Length> left, int basis){
        return new Edges(
                style.resolve(top, basis, viewWidth, viewHeight, 0),
                style.resolve(right, basis, viewWidth, viewHeight, 0),
                style.resolve(bottom, basis, viewWidth, viewHeight, 0),
                style.resolve(left, basis, viewWidth, viewHeight, 0));
    }

    private Edges marginEdges(ElementStyle style, int basis){
        return edges(style, ElementStyle.MARGIN_TOP, ElementStyle.MARGIN_RIGHT, ElementStyle.MARGIN_BOTTOM, ElementStyle.MARGIN_LEFT, basis);
    }

    private int horizontalMargins(ElementStyle style, int basis){
        return marginEdges(style, basis).horizontal();
    }

    private static Edges borderEdges(ElementStyle style){
        return new Edges(
                borderWidth(style, ElementStyle.BORDER_TOP_WIDTH, ElementStyle.BORDER_TOP_STYLE),
                borderWidth(style, ElementStyle.BORDER_RIGHT_WIDTH, ElementStyle.BORDER_RIGHT_STYLE),
                borderWidth(style, ElementStyle.BORDER_BOTTOM_WIDTH, ElementStyle.BORDER_BOTTOM_STYLE),
                borderWidth(style, ElementStyle.BORDER_LEFT_WIDTH, ElementStyle.BORDER_LEFT_STYLE));
    }

    /** A side with no style to it takes up no room, which is what css does with border-style none. */
    public static int borderWidth(ElementStyle style, ElementStyle.Key<Integer> width,
                                 ElementStyle.Key<ElementStyle.BorderStyle> kind){
        return style.get(kind) == ElementStyle.BorderStyle.NONE ? 0 : style.get(width, 0);
    }

    /** Padding and border together, which is what sits between the border box and the content. */
    private Edges frame(ElementStyle style, int basis){
        return edges(style, ElementStyle.PADDING_TOP, ElementStyle.PADDING_RIGHT, ElementStyle.PADDING_BOTTOM, ElementStyle.PADDING_LEFT, basis)
                .plus(borderEdges(style));
    }

    /**
     * How far right the content of a box reaches, following into the children that let theirs spill out.
     * A box that clips stops the walk: whatever is inside it is its own business to scroll.
     */
    private static int reachRight(Box box){
        int reach = box.right();
        if (!box.clipsX()) for (Box child : box.children()) reach = Math.max(reach, reachRight(child));
        return reach;
    }

    private static int reachBottom(Box box){
        int reach = box.bottom();
        if (!box.clipsY()) for (Box child : box.children()) reach = Math.max(reach, reachBottom(child));
        return reach;
    }

    /** Moves a box and everything in it, leaving the fixed ones where the viewport put them. */
    private static Box shift(Box box, int dx, int dy){
        if (dx == 0 && dy == 0) return box;
        List<Box> children = new ArrayList<>(box.children().size());
        for (Box child : box.children()) children.add(child.isFixed() ? child : shift(child, dx, dy));
        return new Box(box.element(), box.style(),
                box.x() + dx, box.y() + dy, box.width(), box.height(),
                box.contentX() + dx, box.contentY() + dy, box.contentWidth(), box.contentHeight(),
                box.offsetX(), box.offsetY(), box.port().shifted(dx, dy), box.baseline(), box.lines(), children);
    }

    /** A shift the flow has to know about, so flowX / flowY still finds the spot layout first gave it. */
    private static Box nudge(Box box, int dx, int dy){
        Box moved = shift(box, dx, dy);
        return new Box(moved.element(), moved.style(),
                moved.x(), moved.y(), moved.width(), moved.height(),
                moved.contentX(), moved.contentY(), moved.contentWidth(), moved.contentHeight(),
                moved.offsetX() + dx, moved.offsetY() + dy, moved.port(), moved.baseline(),
                moved.lines(), moved.children());
    }

    private static Box withStyle(Box box, ElementStyle style){
        return new Box(box.element(), style,
                box.x(), box.y(), box.width(), box.height(),
                box.contentX(), box.contentY(), box.contentWidth(), box.contentHeight(),
                box.offsetX(), box.offsetY(), box.port(), box.baseline(), box.lines(), box.children());
    }

    private static Box withChildren(Box box, List<Box> children){
        return new Box(box.element(), box.style(),
                box.x(), box.y(), box.width(), box.height(),
                box.contentX(), box.contentY(), box.contentWidth(), box.contentHeight(),
                box.offsetX(), box.offsetY(), box.port(), box.baseline(), box.lines(), children);
    }

    private static int clamp(int value, int min, int max){
        return Math.max(min, Math.min(max, value));
    }

    private record Edges(int top, int right, int bottom, int left) {
        int horizontal(){ return left + right; }
        int vertical(){ return top + bottom; }
        Edges plus(Edges other){
            return new Edges(top + other.top, right + other.right, bottom + other.bottom, left + other.left);
        }
    }
}
