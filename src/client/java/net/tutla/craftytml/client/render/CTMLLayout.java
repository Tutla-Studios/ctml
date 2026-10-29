package net.tutla.craftytml.client.render;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.property.Text;
import net.tutla.craftytml.client.ctml.style.Length;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.dom.DOMTreeNode;
import net.tutla.craftytml.client.dom.DOMTreeText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            int gutterRight, int gutterBottom
    ) {
        public int right(){ return x + width; }
        public int bottom(){ return y + height; }
        public int maxScrollX(){ return Math.max(0, scrollWidth - width); }
        public int maxScrollY(){ return Math.max(0, scrollHeight - height); }

        Port shifted(int dx, int dy){
            return new Port(x + dx, y + dy, width, height, scrollWidth, scrollHeight, scrollX, scrollY, gutterRight, gutterBottom);
        }
    }

    /**
     * One drawn line of text, already placed against its box's content corner and already scaled for its
     * font size. content is the same text as a plain string, for the drawing that has to go glyph by glyph.
     */
    public record TextLine(FormattedCharSequence text, String content, int x, int y, int width, float scale) {}

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

    /** Intrinsic widths worked out this pass, since flex and shrink to fit ask for the same ones over and over. */
    private final Map<Measured, Intrinsic> measured = new HashMap<>();

    /** The same again for the content based minimums, which ignore the width an element asks for. */
    private final Map<Measured, Integer> contentMinimums = new HashMap<>();

    private record Measured(DOMTreeElement element, int basis) {}
    private record Intrinsic(int min, int max) {}

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

    /** The screen is the root's containing block, so unless it says otherwise the root box is the screen. */
    private static final ElementStyle ROOT_DEFAULTS = ElementStyle.builder()
            .set(ElementStyle.WIDTH, Length.FULL)
            .set(ElementStyle.HEIGHT, Length.FULL)
            .build();

    public Box layout(DOMTreeElement element, int x, int y, int availableWidth, int availableHeight){
        return layout(element, null, x, y, availableWidth, availableHeight, true, UNFORCED, UNFORCED);
    }

    private Box layout(DOMTreeElement element, ElementStyle parentStyle, int x, int y, int cbWidth, int cbHeight){
        return layout(element, parentStyle, x, y, cbWidth, cbHeight, false, UNFORCED, UNFORCED);
    }

    /**
     * forcedWidth / forcedHeight are border box sizes flex has already worked out for an item, which beat
     * anything the style says, since the style is what flex measured in the first place.
     */
    private Box layout(DOMTreeElement element, ElementStyle parentStyle, int x, int y, int cbWidth, int cbHeight,
                       boolean root, int forcedWidth, int forcedHeight){
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

        int contentX = x + leftMargin + frame.left;
        int contentY = y + margin.top + frame.top;

        ElementStyle.Overflow overflowX = CTMLScroll.overflowX(style);
        ElementStyle.Overflow overflowY = CTMLScroll.overflowY(style);
        boolean scrollsX = CTMLScroll.scrolls(overflowX);
        boolean scrollsY = CTMLScroll.scrolls(overflowY);
        int barThickness = CTMLScroll.thickness(style);
        boolean keepsGutter = CTMLScroll.gutter(style) && (scrollsX || scrollsY);

        // With a gutter the bars take their room out of the content, which can change what overflows,
        // so the children are laid out again until the room kept for the bars stops moving.
        int gutterRight = 0;
        int gutterBottom = 0;
        List<TextLine> lines;
        List<Box> children;
        int contentHeight;
        int innerWidth;
        int innerHeight;
        int reachX;
        int reachY;
        int roomX;
        int roomY;
        int baseline;

        for (int attempt = 0; ; attempt++) {
            innerWidth = Math.max(0, contentWidth - gutterRight);
            lines = new ArrayList<>();
            children = new ArrayList<>();

            // whatever the children measure their own percentages against: this box when it has a definite
            // height of its own, otherwise nothing, since its height is still waiting on them
            int definiteHeight = forcedHeight != UNFORCED
                    ? Math.max(0, forcedHeight - frame.vertical())
                    : contentHeight(style, cbWidth, cbHeight, Length.UNRESOLVED);
            int childBasis = definiteHeight == Length.UNRESOLVED ? -1 : Math.max(0, definiteHeight - gutterBottom);

            Flow flow = style.get(ElementStyle.DISPLAY) == ElementStyle.Display.FLEX
                    ? layoutFlex(element, style, contentX, contentY, innerWidth, childBasis, lines, children)
                    : layoutFlow(element, style, contentX, contentY, innerWidth, childBasis, lines, children);
            baseline = flow.baseline();

            contentHeight = forcedHeight != UNFORCED
                    ? Math.max(0, forcedHeight - frame.vertical())
                    : contentHeight(style, cbWidth, cbHeight, flow.height() + gutterBottom);
            innerHeight = Math.max(0, contentHeight - gutterBottom);

            // anything taken out of the flow gets placed against this box once it has a size
            layoutOutOfFlow(element, style, contentX, contentY, innerWidth, innerHeight, children);

            // how far the content reaches, which is what there is to scroll through. roomX / roomY is the
            // same thing with the dragged boxes left out, so dragging one cannot keep pushing its own limit
            reachX = contentX;
            reachY = contentY;
            roomX = contentX + innerWidth;
            roomY = contentY + innerHeight;
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

            if (!keepsGutter || attempt >= 2) break;

            int wantRight = CTMLScroll.showsBar(style, overflowY, reachY - contentY > innerHeight) ? barThickness : 0;
            int wantBottom = CTMLScroll.showsBar(style, overflowX, reachX - contentX > innerWidth) ? barThickness : 0;
            if (wantRight == gutterRight && wantBottom == gutterBottom) break;
            gutterRight = wantRight;
            gutterBottom = wantBottom;
        }

        int width = contentWidth + frame.horizontal();
        int height = contentHeight + frame.vertical();
        int boxX = x + leftMargin;
        int boxY = y + margin.top;

        // a dragged child is pulled back inside this box every frame, so a resize or a gui scale change
        // cannot leave one stranded outside the box it belongs to
        children = clampDrags(children, contentX, contentY, roomX - contentX, roomY - contentY);

        // the scrollport is the padding box with the gutters taken off it, which is what gets clipped to
        int portX = boxX + border.left;
        int portY = boxY + border.top;
        int portWidth = Math.max(0, innerWidth + padding.horizontal());
        int portHeight = Math.max(0, innerHeight + padding.vertical());

        // css counts the far padding edge as part of the scrollable area, so content can be scrolled clear of it
        int scrollWidth = Math.max(portWidth, reachX + padding.right - portX);
        int scrollHeight = Math.max(portHeight, reachY + padding.bottom - portY);

        int scrollX = scrollsX ? clamp(element.getScrollX(), 0, Math.max(0, scrollWidth - portWidth)) : 0;
        int scrollY = scrollsY ? clamp(element.getScrollY(), 0, Math.max(0, scrollHeight - portHeight)) : 0;
        if (scrollsX || scrollsY) element.setScrollOffset(scrollX, scrollY); // the content may have shrunk under it

        // scrolling moves everything inside the box and nothing else, so the children are moved here and
        // the text is drawn against the same offset
        if (scrollX != 0 || scrollY != 0) {
            List<Box> scrolled = new ArrayList<>(children.size());
            for (Box child : children) scrolled.add(child.isFixed() ? child : shift(child, -scrollX, -scrollY));
            children = scrolled;
        }

        Port port = new Port(portX, portY, portWidth, portHeight,
                scrollWidth, scrollHeight, scrollX, scrollY, gutterRight, gutterBottom);

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

        Box box = new Box(element, style, boxX, boxY, width, height,
                contentX, contentY, innerWidth, innerHeight, offsetX, offsetY, port, boxBaseline, lines, children);
        return shift(box, offsetX, offsetY);
    }

    // ---- width and height ----

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
        Measured key = new Measured(element, basis);
        Intrinsic cached = measured.get(key);
        if (cached != null) return cached;

        Edges frame = frame(style, basis);
        Intrinsic result;

        Length width = style.get(ElementStyle.WIDTH);
        if (width != null && width.isDefinite() && !width.isPercent()) {
            int size = width.resolve(basis, viewWidth, viewHeight, 0);
            if (!isBorderBox(style)) size += frame.horizontal();
            result = new Intrinsic(size, size);
        } else if (style.get(ElementStyle.DISPLAY) == ElementStyle.Display.FLEX) {
            result = flexIntrinsic(element, style, basis, frame);
        } else {
            result = flowIntrinsic(element, style, basis, frame);
        }

        int min = style.resolve(ElementStyle.MIN_WIDTH, basis, viewWidth, viewHeight, 0);
        int max = style.resolve(ElementStyle.MAX_WIDTH, basis, viewWidth, viewHeight, Integer.MAX_VALUE);
        result = new Intrinsic(clamp(result.min(), min, max), clamp(result.max(), min, max));

        measured.put(key, result);
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
        int gap = style.resolve(ElementStyle.GAP, basis, viewWidth, viewHeight, 0);

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
        Measured key = new Measured(element, basis);
        Integer cached = contentMinimums.get(key);
        if (cached != null) return cached;

        Edges frame = frame(style, basis);
        Intrinsic inner = style.get(ElementStyle.DISPLAY) == ElementStyle.Display.FLEX
                ? flexIntrinsic(element, style, basis, frame)
                : flowIntrinsic(element, style, basis, frame);

        contentMinimums.put(key, inner.min());
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
                            int width, int cbHeight, List<TextLine> lines, List<Box> boxes){
        List<Item> pending = new ArrayList<>();
        int cursorY = contentY;
        int collapsed = 0;     // the bottom margin of the block before, waiting to collapse into the next one
        boolean first = true;  // nothing placed yet, so there is no margin for the next one to collapse with
        int baseline = NO_BASELINE;

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

            if (isInlineLevel(childStyle)) {
                Box box = layout(child, style, contentX, cursorY, width, cbHeight);
                if (box == null) continue;
                boxes.add(box);
                pending.add(new Atom(boxes.size() - 1, box, marginEdges(childStyle, width), childStyle));
                continue;
            }

            if (!pending.isEmpty()) { // a block closes whatever line is open
                Placed placed = layoutLines(pending, style, contentX, contentY, cursorY, width, lines, boxes);
                if (baseline == NO_BASELINE) baseline = placed.baselineY() - contentY;
                cursorY += placed.height();
                pending.clear();
                collapsed = 0; // margins do not collapse across text
                first = false;
            }

            Edges childMargin = marginEdges(childStyle, width);
            int gap = first ? childMargin.top : Math.max(collapsed, childMargin.top);
            Box box = layout(child, style, contentX, cursorY + gap - childMargin.top, width, cbHeight);
            if (box == null) continue;
            boxes.add(box);
            first = false;

            if (collapsesThrough(box, childStyle)) {
                // an empty block has nothing to keep apart, so its own margins collapse into one
                collapsed = Math.max(gap, childMargin.bottom);
            } else {
                cursorY = box.flowY() + box.height(); // flowY, so a drag or a relative nudge never moves the flow
                collapsed = childMargin.bottom;
                if (baseline == NO_BASELINE && box.baseline() != NO_BASELINE) {
                    baseline = box.flowY() + box.baseline() - contentY;
                }
            }
        }

        if (!pending.isEmpty()) {
            Placed placed = layoutLines(pending, style, contentX, contentY, cursorY, width, lines, boxes);
            if (baseline == NO_BASELINE) baseline = placed.baselineY() - contentY;
            cursorY += placed.height();
            collapsed = 0;
        }

        return new Flow(cursorY + collapsed - contentY, baseline);
    }

    /** A block with nothing in it and no edges of its own: css lets margins collapse right through it. */
    private boolean collapsesThrough(Box box, ElementStyle style){
        return box.height() == 0
                && borderEdges(style).vertical() == 0
                && !Length.isDefinite(style.get(ElementStyle.HEIGHT));
    }

    // ---- line boxes ----

    /** Something that can sit on a line: a run of text, an inline level box, or a break between them. */
    private sealed interface Item {}

    /**
     * A run of text that is drawn in one go. space marks the runs that only separate words, since those
     * are dropped at the end of a line and a line never starts with one.
     */
    private record Run(String text, ElementStyle style, float scale, int width, boolean space) implements Item {}

    /** An inline level box, laid out already, waiting to be told which line it goes on. */
    private record Atom(int index, Box box, Edges margin, ElementStyle style) implements Item {}

    /** A line ending that was asked for rather than worked out, so pre text keeps the shape it was written in. */
    private record Break() implements Item {}

    /**
     * Lays text and inline boxes out into line boxes, wrapping at the spaces where white-space allows it.
     * Everything on a line lines up by its baseline unless vertical-align says otherwise, and the line box
     * ends up as tall as it has to be to hold all of it.
     */
    private Placed layoutLines(List<Item> items, ElementStyle style, int contentX, int contentY, int top,
                               int width, List<TextLine> lines, List<Box> boxes){
        ElementStyle.WhiteSpace whiteSpace = style.get(ElementStyle.WHITE_SPACE, ElementStyle.WhiteSpace.NORMAL);
        boolean wraps = whiteSpace == ElementStyle.WhiteSpace.NORMAL && width > 0;

        int y = top;
        int baselineY = NO_BASELINE;
        List<Item> line = new ArrayList<>();
        int used = 0;

        for (Item item : items) {
            if (item instanceof Break) {
                Placed placed = placeLine(line, style, contentX, contentY, y, width, lines, boxes);
                if (baselineY == NO_BASELINE) baselineY = placed.baselineY();
                y += placed.height();
                line.clear();
                used = 0;
                continue;
            }

            int itemWidth = itemWidth(item);
            boolean space = item instanceof Run run && run.space();
            if (line.isEmpty() && space) continue; // a line never starts with a space

            if (wraps && !line.isEmpty() && !space && used + itemWidth > width) {
                Placed placed = placeLine(line, style, contentX, contentY, y, width, lines, boxes);
                if (baselineY == NO_BASELINE) baselineY = placed.baselineY();
                y += placed.height();
                line.clear();
                used = 0;
            }

            line.add(item);
            used += itemWidth;
        }

        if (!line.isEmpty()) {
            Placed placed = placeLine(line, style, contentX, contentY, y, width, lines, boxes);
            if (baselineY == NO_BASELINE) baselineY = placed.baselineY();
            y += placed.height();
        }

        return new Placed(y - top, baselineY == NO_BASELINE ? top + strutAbove(style) : baselineY);
    }

    /** One line box: how tall it is, where its baseline falls, and everything on it put in its place. */
    private Placed placeLine(List<Item> line, ElementStyle style, int contentX, int contentY, int top,
                             int width, List<TextLine> lines, List<Box> boxes){
        // the spaces hanging off the end count for nothing: not towards the width, not towards the alignment
        while (!line.isEmpty() && line.getLast() instanceof Run run && run.space()) line.removeLast();

        // the strut: the element's own font holds the line open even where there is nothing on it
        int above = strutAbove(style);
        int below = strutBelow(style);
        if (line.isEmpty()) return new Placed(above + below, top + above);

        int reach = 0;
        for (Item item : line) reach += itemWidth(item);
        if (reach > width && ellipsises(style)) line = ellipsise(line, style, width);

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

        ElementStyle.TextAlign align = style.get(ElementStyle.TEXT_ALIGN, ElementStyle.TextAlign.LEFT);
        int cursorX = contentX + switch (align) {
            case CENTER -> Math.max(0, width - lineWidth) / 2;
            case RIGHT -> Math.max(0, width - lineWidth);
            case LEFT -> 0;
        };

        // runs sitting next to each other in the same style are drawn as one, so a sentence is a single call
        StringBuilder buffer = new StringBuilder();
        ElementStyle runStyle = null;
        float runScale = 1.0f;
        int runX = cursorX;
        int runWidth = 0;

        for (Item item : line) {
            if (item instanceof Run run) {
                if (!buffer.isEmpty() && (run.style() != runStyle || run.scale() != runScale)) {
                    flushRun(buffer, runStyle, runScale, runX - contentX, baselineY - contentY, runWidth, lines);
                    runX = cursorX;
                    runWidth = 0;
                }
                if (buffer.isEmpty()) runX = cursorX;
                runStyle = run.style();
                runScale = run.scale();
                buffer.append(run.text());
                runWidth += run.width();
                cursorX += run.width();
                continue;
            }

            if (!buffer.isEmpty()) {
                flushRun(buffer, runStyle, runScale, runX - contentX, baselineY - contentY, runWidth, lines);
                runWidth = 0;
            }

            Atom atom = (Atom) item;
            int boxTop = switch (atom.style().get(ElementStyle.VERTICAL_ALIGN, ElementStyle.VerticalAlign.BASELINE)) {
                case TOP -> top + atom.margin().top;
                case BOTTOM -> top + height - itemHeight(atom) + atom.margin().top;
                default -> baselineY - itemAbove(atom, style) + atom.margin().top;
            };
            boxes.set(atom.index(), shift(atom.box(),
                    cursorX + atom.margin().left - atom.box().flowX(),
                    boxTop - atom.box().flowY()));
            cursorX += itemWidth(atom);
            runX = cursorX;
        }

        if (!buffer.isEmpty()) {
            flushRun(buffer, runStyle, runScale, runX - contentX, baselineY - contentY, runWidth, lines);
        }

        return new Placed(height, baselineY);
    }

    /** What gets put on the end of a line that was cut short. Minecraft's font has no ellipsis glyph. */
    private static final String ELLIPSIS = "...";

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
    private List<Item> ellipsise(List<Item> line, ElementStyle style, int width){
        int dots = Math.round(metrics.width(sequence(ELLIPSIS, style)) * fontScale(style));
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
                if (!fitting.isEmpty()) kept.add(run(fitting, run.style(), run.scale(), false));
            } else if (item instanceof Atom) {
                kept.add(item);
            }

            while (!kept.isEmpty() && kept.getLast() instanceof Run last && last.space()) kept.removeLast();
            kept.add(run(ELLIPSIS, style, fontScale(style), false));
            break;
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
            int width = Math.round(metrics.width(sequence(glyph, run.style())) * run.scale());
            if (used + width > room) break;
            used += width;
            kept.append(glyph);
            i += Character.charCount(codePoint);
        }

        return kept.toString().stripTrailing();
    }

    /** Turns the buffered run into the one text line that gets drawn. */
    private void flushRun(StringBuilder buffer, ElementStyle style, float scale, int x, int baseline,
                          int width, List<TextLine> lines){
        String content = buffer.toString();
        buffer.setLength(0);
        if (content.isEmpty()) return;

        int y = baseline - Math.round(metrics.ascent() * scale);
        lines.add(new TextLine(sequence(content, style), content, x, y, width, scale));
    }

    private FormattedCharSequence sequence(String content, ElementStyle style){
        return Component.literal(content).setStyle(textStyle(style)).getVisualOrderText();
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
        return 0;
    }

    private int itemHeight(Item item){
        if (item instanceof Run run) return lineHeight(run.style());
        if (item instanceof Atom atom) return atom.box().height() + atom.margin().vertical();
        return 0;
    }

    /** How far above the baseline the top of an item's margin box sits, which is what makes the line box. */
    private int itemAbove(Item item, ElementStyle parentStyle){
        if (item instanceof Run run) return strutAbove(run.style());
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
        if (text == null || text.isEmpty()) return;
        float scale = fontScale(style);

        if (style.get(ElementStyle.WHITE_SPACE) == ElementStyle.WhiteSpace.PRE) {
            String[] hard = text.split("\n", -1);
            for (int i = 0; i < hard.length; i++) {
                if (i > 0) items.add(new Break());
                if (!hard[i].isEmpty()) items.add(run(hard[i], style, scale, false));
            }
            return;
        }

        int i = 0;
        while (i < text.length()) {
            if (Character.isWhitespace(text.charAt(i))) {
                while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
                // a space only separates, so two in a row say no more than one does
                if (!items.isEmpty() && !(items.getLast() instanceof Run last && last.space())) {
                    items.add(run(" ", style, scale, true));
                }
                continue;
            }
            int start = i;
            while (i < text.length() && !Character.isWhitespace(text.charAt(i))) i++;
            items.add(run(text.substring(start, i), style, scale, false));
        }
    }

    private Run run(String text, ElementStyle style, float scale, boolean space){
        return new Run(text, style, scale, Math.round(metrics.width(sequence(text, style)) * scale), space);
    }

    // ---- flex ----

    /** A flex item being worked out: what size it asked for, what it ended up with, and the box for it. */
    private static final class FlexItem {
        final DOMTreeElement element;
        final ElementStyle style;
        final Edges margin;
        int base;
        int hypothetical;
        int min;
        int max;
        int grow;
        int shrink;
        boolean frozen;
        int main;
        Box box;

        FlexItem(DOMTreeElement element, ElementStyle style, Edges margin){
            this.element = element;
            this.style = style;
            this.margin = margin;
        }

        int mainMargin(boolean row){ return row ? margin.horizontal() : margin.vertical(); }
        int crossMargin(boolean row){ return row ? margin.vertical() : margin.horizontal(); }
        int outerMain(boolean row){ return main + mainMargin(row); }
        int crossSize(boolean row){ return box == null ? 0 : (row ? box.height() : box.width()); }
        int outerCross(boolean row){ return crossSize(row) + crossMargin(row); }
    }

    /**
     * Flexbox: items in order, sized from their basis and then grown or shrunk to fill the line, wrapped
     * onto as many lines as they need, each line aligned across the cross axis and the lines themselves
     * spread out by align-content.
     */
    private Flow layoutFlex(DOMTreeElement element, ElementStyle style, int contentX, int contentY,
                            int width, int cbHeight, List<TextLine> lines, List<Box> boxes){
        // a flex container's own text has no item to live in, so it is laid out across the top
        int textHeight = 0;
        int baseline = NO_BASELINE;
        List<Item> own = new ArrayList<>();
        addText(own, text(element), style);
        if (!own.isEmpty()) {
            Placed placed = layoutLines(own, style, contentX, contentY, contentY, width, lines, boxes);
            textHeight = placed.height();
            baseline = placed.baselineY() - contentY;
        }

        int originY = contentY + textHeight;
        boolean row = isRow(style);
        boolean reverse = isReverse(style);
        ElementStyle.FlexWrap wrapMode = style.get(ElementStyle.FLEX_WRAP, ElementStyle.FlexWrap.NOWRAP);
        boolean wraps = wrapMode != ElementStyle.FlexWrap.NOWRAP;

        int inner = cbHeight < 0 ? -1 : Math.max(0, cbHeight - textHeight);
        int mainSize = row ? width : inner;
        int crossSize = row ? inner : width;
        int gap = style.resolve(ElementStyle.GAP, row ? width : Math.max(0, inner), viewWidth, viewHeight, 0);

        List<FlexItem> items = new ArrayList<>();
        for (DOMTreeElement child : inFlow(element)) {
            ElementStyle childStyle = child.getStyle().inheritFrom(style);
            if (childStyle.get(ElementStyle.DISPLAY) == ElementStyle.Display.NONE) continue;
            items.add(new FlexItem(child, childStyle, marginEdges(childStyle, width)));
        }
        if (items.isEmpty()) return new Flow(textHeight, baseline);

        // order only moves items about; equal orders keep the order they were written in
        items.sort(Comparator.comparingInt(item -> item.style.get(ElementStyle.ORDER, 0)));

        for (FlexItem item : items) sizeItem(item, style, contentX, originY, width, cbHeight, mainSize, row);

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
                item.box = layoutItem(item, style, contentX, originY, width, cbHeight, row, item.main, UNFORCED);
                cross = Math.max(cross, item.outerCross(row));
            }
            lineCross[i] = cross;
        }

        // a single line is as thick as the container itself, which is what lets stretch fill it
        if (flexLines.size() == 1 && crossSize >= 0) lineCross[0] = Math.max(lineCross[0], crossSize);

        int crossUsed = gap * Math.max(0, flexLines.size() - 1);
        for (int cross : lineCross) crossUsed += cross;

        // align-content spreads the lines themselves once there is room left over
        ElementStyle.AlignContent alignContent = style.get(ElementStyle.ALIGN_CONTENT, ElementStyle.AlignContent.START);
        int crossFree = crossSize < 0 ? 0 : Math.max(0, crossSize - crossUsed);
        int crossLead = switch (alignContent) {
            case CENTER -> crossFree / 2;
            case END -> crossFree;
            case SPACE_AROUND -> crossFree / (flexLines.size() * 2);
            case SPACE_EVENLY -> crossFree / (flexLines.size() + 1);
            default -> 0;
        };
        int crossSpacing = gap + switch (alignContent) {
            case SPACE_BETWEEN -> flexLines.size() > 1 ? crossFree / (flexLines.size() - 1) : 0;
            case SPACE_AROUND -> crossFree / flexLines.size();
            case SPACE_EVENLY -> crossFree / (flexLines.size() + 1);
            default -> 0;
        };
        if (alignContent == ElementStyle.AlignContent.STRETCH && crossFree > 0) {
            int share = crossFree / flexLines.size();
            for (int i = 0; i < lineCross.length; i++) lineCross[i] += share;
        }

        ElementStyle.AlignItems align = style.get(ElementStyle.ALIGN_ITEMS, ElementStyle.AlignItems.START);
        int crossCursor = (row ? originY : contentX) + crossLead;
        int mainEnd = row ? contentX : originY;

        for (int i = 0; i < flexLines.size(); i++) {
            List<FlexItem> flexLine = flexLines.get(i);
            int cross = lineCross[i];

            // stretch is the one alignment that changes a size, so it is settled before anything moves
            for (FlexItem item : flexLine) {
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
            int lead = switch (justify) {
                case CENTER -> free / 2;
                case END -> free;
                case SPACE_AROUND -> free / (flexLine.size() * 2);
                case SPACE_EVENLY -> free / (flexLine.size() + 1);
                default -> 0;
            };
            int spacing = gap + switch (justify) {
                case SPACE_BETWEEN -> flexLine.size() > 1 ? free / (flexLine.size() - 1) : 0;
                case SPACE_AROUND -> free / flexLine.size();
                case SPACE_EVENLY -> free / (flexLine.size() + 1);
                default -> 0;
            };

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
            int main = mainStart + lead;

            for (FlexItem item : flexLine) {
                int offset = switch (alignOf(item, align)) {
                    case CENTER -> Math.max(0, cross - item.outerCross(row)) / 2;
                    case END -> Math.max(0, cross - item.outerCross(row));
                    case BASELINE -> Math.max(0, lineBaseline - item.margin.top - itemBaseline(item, row));
                    default -> 0;
                };
                int crossStart = crossCursor + offset;
                int mainStop = reverse
                        ? mainStart + span - (main - mainStart) - item.outerMain(row)
                        : main;

                int targetX = row ? mainStop + item.margin.left : crossStart + item.margin.left;
                int targetY = row ? crossStart + item.margin.top : mainStop + item.margin.top;
                Box placed = shift(item.box, targetX - item.box.flowX(), targetY - item.box.flowY());
                boxes.add(placed);

                if (baseline == NO_BASELINE && placed.baseline() != NO_BASELINE) {
                    baseline = placed.flowY() + placed.baseline() - contentY;
                }

                main += item.outerMain(row) + spacing;
            }

            mainEnd = Math.max(mainEnd, main - spacing);
            crossCursor += cross + crossSpacing;
        }

        int crossEnd = crossCursor - crossSpacing;
        return new Flow(row
                ? Math.max(textHeight, crossEnd - contentY)
                : Math.max(textHeight, mainEnd - contentY), baseline);
    }

    /** The size a flex item asks for along the main axis, before any of the free space is handed out. */
    private void sizeItem(FlexItem item, ElementStyle parentStyle, int contentX, int originY,
                          int width, int cbHeight, int mainSize, boolean row){
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
            item.box = layoutItem(item, parentStyle, contentX, originY, width, cbHeight, row, UNFORCED, UNFORCED);
            item.base = item.box == null ? 0 : item.box.height();
        }

        // css gives a flex item a minimum of its own content, so it cannot be squashed away to nothing.
        // It is what the content needs rather than what the width asked for, and never more than the
        // size the item was going to be anyway, or a wide empty box could never give any of it back.
        int autoMin = row && !CTMLScroll.clips(CTMLScroll.overflowX(item.style))
                ? Math.min(item.base, contentMinimum(item.element, item.style, width))
                : 0;
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
        return layout(item.element, parentStyle, contentX, originY, width, cbHeight, false,
                row ? main : cross, row ? cross : main);
    }

    /** Where an item's text sits, which is what baseline alignment lines up. */
    private int itemBaseline(FlexItem item, boolean row){
        if (!row || item.box == null) return 0; // down a column there is nothing to line up sideways
        return item.box.baseline() == NO_BASELINE ? item.box.height() : item.box.baseline();
    }

    private static ElementStyle.AlignItems alignOf(FlexItem item, ElementStyle.AlignItems fallback){
        return item.style.get(ElementStyle.ALIGN_SELF, fallback);
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
            if (child.isFixed()) continue;
            int dragX = child.element().getDragX();
            int dragY = child.element().getDragY();
            if (dragX == 0 && dragY == 0) continue;

            int flowX = child.flowX();
            int flowY = child.flowY();
            int minX = contentX - flowX;
            int minY = contentY - flowY;
            int clampedX = clamp(dragX, minX, Math.max(minX, contentX + roomWidth - child.width() - flowX));
            int clampedY = clamp(dragY, minY, Math.max(minY, contentY + roomHeight - child.height() - flowY));
            if (clampedX == dragX && clampedY == dragY) continue;

            child.element().setDragOffset(clampedX, clampedY);
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

    private static String text(DOMTreeElement element){
        return element.getProperty("text") instanceof Text t && t.hasValue() ? t.getStringValue() : null;
    }

    private int textWidth(String text, ElementStyle style){
        if (text == null || text.isBlank()) return 0;
        return Math.round(metrics.width(sequence(text.strip(), style)) * fontScale(style));
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
                style.get(ElementStyle.BORDER_TOP_WIDTH, 0), style.get(ElementStyle.BORDER_RIGHT_WIDTH, 0),
                style.get(ElementStyle.BORDER_BOTTOM_WIDTH, 0), style.get(ElementStyle.BORDER_LEFT_WIDTH, 0));
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
