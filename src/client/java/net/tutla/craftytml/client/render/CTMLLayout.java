package net.tutla.craftytml.client.render;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.property.Text;
import net.tutla.craftytml.client.ctml.style.Length;
import net.tutla.craftytml.client.dom.DOMTreeElement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Works out where everything sits before anything gets drawn: boxes, text lines and all.
 * A cut down css box model, so margin / border / padding / width / height / flex, with percentages
 * measured against whatever contains the element and the viewport behind the vw and vh units.
 */
public final class CTMLLayout {
    /** What minecraft's font counts as, so font-size can be read as a scale against it. */
    public static final int BASE_FONT_SIZE = 16;

    /** A laid out element: the border box, the content box inside it, and what goes in there. */
    public record Box(
            DOMTreeElement element,
            ElementStyle style,
            int x, int y, int width, int height,
            int contentX, int contentY, int contentWidth, int contentHeight,
            List<TextLine> lines,
            List<Box> children
    ) {
        public int right(){ return x + width; }
        public int bottom(){ return y + height; }
        public int contentRight(){ return contentX + contentWidth; }
        public int contentBottom(){ return contentY + contentHeight; }
    }

    /** One drawn line of text, already placed and already scaled for its font size. */
    public record TextLine(FormattedCharSequence text, int x, int y, float scale) {}

    /** Everything layout needs to ask about text, so the measuring can be swapped out. */
    public interface TextMetrics {
        int width(String text);
        int width(FormattedCharSequence text);
        int lineHeight();
        List<FormattedCharSequence> split(FormattedText text, int width);
    }

    public record FontMetrics(Font font) implements TextMetrics {
        @Override public int width(String text){ return font.width(text); }
        @Override public int width(FormattedCharSequence text){ return font.width(text); }
        @Override public int lineHeight(){ return font.lineHeight; }
        @Override public List<FormattedCharSequence> split(FormattedText text, int width){ return font.split(text, width); }
    }

    private final TextMetrics metrics;
    private final int viewWidth;
    private final int viewHeight;

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
        return layout(element, null, x, y, availableWidth, availableHeight, true);
    }

    private Box layout(DOMTreeElement element, ElementStyle parentStyle, int x, int y, int cbWidth, int cbHeight){
        return layout(element, parentStyle, x, y, cbWidth, cbHeight, false);
    }

    private Box layout(DOMTreeElement element, ElementStyle parentStyle, int x, int y, int cbWidth, int cbHeight, boolean root){
        ElementStyle style = element.getStyle().inheritFrom(parentStyle);
        if (root) style = style.inherit(ROOT_DEFAULTS);
        if (style.get(ElementStyle.DISPLAY) == ElementStyle.Display.NONE) return null;

        Edges margin = edges(style, ElementStyle.MARGIN_TOP, ElementStyle.MARGIN_RIGHT, ElementStyle.MARGIN_BOTTOM, ElementStyle.MARGIN_LEFT, cbWidth);
        Edges padding = edges(style, ElementStyle.PADDING_TOP, ElementStyle.PADDING_RIGHT, ElementStyle.PADDING_BOTTOM, ElementStyle.PADDING_LEFT, cbWidth);
        Edges border = new Edges(
                style.get(ElementStyle.BORDER_TOP_WIDTH, 0), style.get(ElementStyle.BORDER_RIGHT_WIDTH, 0),
                style.get(ElementStyle.BORDER_BOTTOM_WIDTH, 0), style.get(ElementStyle.BORDER_LEFT_WIDTH, 0));
        Edges frame = padding.plus(border);

        int outerSpace = margin.horizontal() + frame.horizontal();
        int contentWidth = contentWidth(element, style, cbWidth, Math.max(0, cbWidth - outerSpace));

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

        List<TextLine> lines = textLines(element, style, contentWidth);
        int textHeight = textHeight(style, lines);

        // whatever the children measure their own percentages against: this box when it has a definite
        // height of its own, otherwise nothing, since its height is still waiting on them
        int innerHeight = contentHeight(style, cbWidth, cbHeight, Length.UNRESOLVED);
        int childBasis = innerHeight == Length.UNRESOLVED ? -1 : Math.max(0, innerHeight - textHeight);

        List<Box> children = new ArrayList<>();
        int flowHeight = style.get(ElementStyle.DISPLAY) == ElementStyle.Display.FLEX
                ? layoutFlex(element, style, contentX, contentY + textHeight, contentWidth, childBasis, children)
                : layoutFlow(element, style, contentX, contentY + textHeight, contentWidth, childBasis, children);

        int contentHeight = contentHeight(style, cbWidth, cbHeight, textHeight + flowHeight);

        // anything taken out of the flow gets placed against this box once it has a size
        layoutOutOfFlow(element, style, contentX, contentY, contentWidth, contentHeight, children);

        int width = contentWidth + frame.horizontal();
        int height = contentHeight + frame.vertical();
        int boxX = x + leftMargin;
        int boxY = y + margin.top;

        // relative nudges the box without the flow around it noticing
        if (style.get(ElementStyle.POSITION) == ElementStyle.Position.RELATIVE) {
            int offsetX = style.resolve(ElementStyle.LEFT, cbWidth, viewWidth, viewHeight, 0)
                    - style.resolve(ElementStyle.RIGHT, cbWidth, viewWidth, viewHeight, 0);
            int offsetY = style.resolve(ElementStyle.TOP, cbHeight, viewWidth, viewHeight, 0)
                    - style.resolve(ElementStyle.BOTTOM, cbHeight, viewWidth, viewHeight, 0);
            if (offsetX != 0 || offsetY != 0) return shift(
                    new Box(element, style, boxX, boxY, width, height, contentX, contentY, contentWidth, contentHeight, lines, children),
                    offsetX, offsetY);
        }

        return new Box(element, style, boxX, boxY, width, height, contentX, contentY, contentWidth, contentHeight, lines, children);
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
                case MIN_CONTENT -> minContentWidth(element, style);
                case MAX_CONTENT -> maxContentWidth(element, style);
                default -> Math.min(maxContentWidth(element, style), available); // fit-content
            };
        } else if (isShrinkToFit(style)) { // inline-ish boxes are only as wide as they need to be
            resolved = Math.min(maxContentWidth(element, style), available);
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

    /** Widest it can get with nothing wrapping. */
    private int maxContentWidth(DOMTreeElement element, ElementStyle style){
        int widest = textWidth(element, style);
        for (DOMTreeElement child : inFlow(element)) {
            ElementStyle childStyle = child.getStyle().inheritFrom(style);
            widest = Math.max(widest, maxContentWidth(child, childStyle) + frame(childStyle, 0).horizontal()
                    + childStyle.resolve(ElementStyle.MARGIN_LEFT, 0, viewWidth, viewHeight, 0)
                    + childStyle.resolve(ElementStyle.MARGIN_RIGHT, 0, viewWidth, viewHeight, 0));
        }
        return widest;
    }

    /** Narrowest it can get, so the longest single word plus whatever the children need. */
    private int minContentWidth(DOMTreeElement element, ElementStyle style){
        int widest = 0;
        String text = text(element);
        if (text != null) {
            float scale = fontScale(style);
            for (String word : text.strip().split("\\s+")) widest = Math.max(widest, Math.round(metrics.width(word) * scale));
        }
        for (DOMTreeElement child : inFlow(element)) widest = Math.max(widest, minContentWidth(child, child.getStyle().inheritFrom(style)));
        return widest;
    }

    // ---- children ----

    /** Block children stack downwards, inline ones run along a line and wrap when they run out of room. */
    private int layoutFlow(DOMTreeElement element, ElementStyle style, int x, int y, int width, int cbHeight, List<Box> boxes){
        int cursorY = y;
        int lineX = x;
        int lineHeight = 0;

        for (DOMTreeElement child : inFlow(element)) {
            ElementStyle childStyle = child.getStyle().inheritFrom(style);
            boolean inline = isShrinkToFit(childStyle);

            if (!inline && lineHeight > 0) { // a block closes whatever line was open
                cursorY += lineHeight;
                lineX = x;
                lineHeight = 0;
            }

            Box box = layout(child, style, inline ? lineX : x, cursorY, width, cbHeight);
            if (box == null) continue;

            if (inline && lineX > x && box.right() > x + width) { // does not fit, so wrap it onto the next line
                cursorY += lineHeight;
                lineX = x;
                lineHeight = 0;
                box = layout(child, style, lineX, cursorY, width, cbHeight);
                if (box == null) continue;
            }

            boxes.add(box);
            int outerHeight = box.height + marginEdges(childStyle, width).vertical();

            if (inline) {
                lineX = box.right() + marginEdges(childStyle, width).right;
                lineHeight = Math.max(lineHeight, outerHeight);
            } else {
                cursorY += outerHeight;
            }
        }

        return cursorY + lineHeight - y;
    }

    /** Flex, enough of it: a main axis with gap and justify-content, a cross axis with align-items. */
    private int layoutFlex(DOMTreeElement element, ElementStyle style, int x, int y, int width, int cbHeight, List<Box> boxes){
        ElementStyle.FlexDirection direction = style.get(ElementStyle.FLEX_DIRECTION, ElementStyle.FlexDirection.ROW);
        boolean row = direction == ElementStyle.FlexDirection.ROW || direction == ElementStyle.FlexDirection.ROW_REVERSE;
        boolean reverse = direction == ElementStyle.FlexDirection.ROW_REVERSE || direction == ElementStyle.FlexDirection.COLUMN_REVERSE;
        int gap = style.resolve(ElementStyle.GAP, row ? width : cbHeight, viewWidth, viewHeight, 0);

        List<DOMTreeElement> items = inFlow(element);
        if (reverse) items = items.reversed();

        // lay every item out on its own first, then move them into place
        List<Box> laid = new ArrayList<>();
        List<Edges> margins = new ArrayList<>();
        int mainUsed = 0;
        int crossUsed = 0;

        for (DOMTreeElement item : items) {
            ElementStyle itemStyle = item.getStyle().inheritFrom(style);
            Box box = layout(item, style, x, y, row ? shrinkToFitWidth(item, itemStyle, width) : width, cbHeight);
            if (box == null) continue;

            Edges itemMargin = marginEdges(itemStyle, width);
            laid.add(box);
            margins.add(itemMargin);
            mainUsed += row ? box.width + itemMargin.horizontal() : box.height + itemMargin.vertical();
            crossUsed = Math.max(crossUsed, row ? box.height + itemMargin.vertical() : box.width + itemMargin.horizontal());
        }

        if (laid.isEmpty()) return 0;

        int mainSize = row ? width : Math.max(crossUsed, mainUsed);
        int totalGap = gap * (laid.size() - 1);
        int free = Math.max(0, (row ? width : mainUsed + totalGap) - mainUsed - totalGap);
        ElementStyle.JustifyContent justify = style.get(ElementStyle.JUSTIFY_CONTENT, ElementStyle.JustifyContent.START);

        int main = (row ? x : y) + switch (justify) {
            case CENTER -> free / 2;
            case END -> free;
            case SPACE_AROUND -> free / (laid.size() * 2);
            case SPACE_EVENLY -> free / (laid.size() + 1);
            default -> 0;
        };
        int spacing = gap + switch (justify) {
            case SPACE_BETWEEN -> laid.size() > 1 ? free / (laid.size() - 1) : 0;
            case SPACE_AROUND -> free / laid.size();
            case SPACE_EVENLY -> free / (laid.size() + 1);
            default -> 0;
        };

        int crossSize = row
                ? Math.max(crossUsed, cbHeight < 0 ? crossUsed : style.resolve(ElementStyle.HEIGHT, cbHeight, viewWidth, viewHeight, crossUsed))
                : width;
        ElementStyle.AlignItems align = style.get(ElementStyle.ALIGN_ITEMS, ElementStyle.AlignItems.START);

        for (int i = 0; i < laid.size(); i++) {
            Box box = laid.get(i);
            Edges itemMargin = margins.get(i);
            int itemMain = row ? box.width + itemMargin.horizontal() : box.height + itemMargin.vertical();
            int itemCross = row ? box.height + itemMargin.vertical() : box.width + itemMargin.horizontal();

            int cross = (row ? y : x) + switch (align) {
                case CENTER -> Math.max(0, crossSize - itemCross) / 2;
                case END -> Math.max(0, crossSize - itemCross);
                default -> 0;
            };

            boxes.add(shift(box, row ? main - box.x : cross - box.x, row ? cross - box.y : main - box.y));
            main += itemMain + spacing;
        }

        return row ? crossSize : main - spacing - y;
    }

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
            else if (Length.isDefinite(right)) offsetX = boxWidth - box.width - right.resolve(boxWidth, viewWidth, viewHeight, 0);
            if (Length.isDefinite(top)) offsetY = top.resolve(boxHeight, viewWidth, viewHeight, 0);
            else if (Length.isDefinite(bottom)) offsetY = boxHeight - box.height - bottom.resolve(boxHeight, viewWidth, viewHeight, 0);

            boxes.add(shift(box, offsetX, offsetY));
        }
    }

    // ---- text ----

    private List<TextLine> textLines(DOMTreeElement element, ElementStyle style, int contentWidth){
        String text = text(element);
        if (text == null || text.isEmpty()) return List.of();

        float scale = fontScale(style);
        Component component = Component.literal(text).setStyle(textStyle(style));
        ElementStyle.WhiteSpace whiteSpace = style.get(ElementStyle.WHITE_SPACE, ElementStyle.WhiteSpace.NORMAL);

        List<FormattedCharSequence> wrapped = whiteSpace == ElementStyle.WhiteSpace.NORMAL && contentWidth > 0
                ? metrics.split(component, Math.max(1, Math.round(contentWidth / scale)))
                : List.of(component.getVisualOrderText());

        int lineHeight = lineHeight(style);
        ElementStyle.TextAlign align = style.get(ElementStyle.TEXT_ALIGN, ElementStyle.TextAlign.LEFT);
        List<TextLine> lines = new ArrayList<>(wrapped.size());

        for (int i = 0; i < wrapped.size(); i++) {
            FormattedCharSequence line = wrapped.get(i);
            int lineWidth = Math.round(metrics.width(line) * scale);
            int offsetX = switch (align) {
                case CENTER -> Math.max(0, contentWidth - lineWidth) / 2;
                case RIGHT -> Math.max(0, contentWidth - lineWidth);
                case LEFT -> 0;
            };
            lines.add(new TextLine(line, offsetX, i * lineHeight, scale));
        }

        return lines;
    }

    private int textHeight(ElementStyle style, List<TextLine> lines){
        return lines.isEmpty() ? 0 : lines.size() * lineHeight(style);
    }

    private int textWidth(DOMTreeElement element, ElementStyle style){
        String text = text(element);
        return text == null ? 0 : Math.round(metrics.width(text) * fontScale(style));
    }

    private int lineHeight(ElementStyle style){
        return style.get(ElementStyle.LINE_HEIGHT, Math.round(metrics.lineHeight() * fontScale(style)));
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

    private static String text(DOMTreeElement element){
        return element.getProperty("text") instanceof Text t && t.hasValue() ? t.getStringValue() : null;
    }

    /** Children that take part in the flow, so everything bar the absolute and fixed ones. */
    private static List<DOMTreeElement> inFlow(DOMTreeElement element){
        List<DOMTreeElement> children = new ArrayList<>();
        for (DOMTreeElement child : element.getChildElements()) {
            ElementStyle.Position position = child.getStyle().get(ElementStyle.POSITION);
            if (position == ElementStyle.Position.ABSOLUTE || position == ElementStyle.Position.FIXED) continue;
            children.add(child);
        }
        children.sort(Comparator.comparingInt(child -> child.getStyle().get(ElementStyle.Z_INDEX, 0)));
        return children;
    }

    /** Tailwind puts everything in border-box, so that is what an unset box-sizing means here. */
    private static boolean isBorderBox(ElementStyle style){
        return style.get(ElementStyle.BOX_SIZING, ElementStyle.BoxSizing.BORDER_BOX) == ElementStyle.BoxSizing.BORDER_BOX;
    }

    private static boolean isShrinkToFit(ElementStyle style){
        ElementStyle.Display display = style.get(ElementStyle.DISPLAY, ElementStyle.Display.BLOCK);
        return display == ElementStyle.Display.INLINE || display == ElementStyle.Display.INLINE_BLOCK;
    }

    private int shrinkToFitWidth(DOMTreeElement element, ElementStyle style, int available){
        return Length.isDefinite(style.get(ElementStyle.WIDTH))
                ? available
                : Math.min(available, maxContentWidth(element, style) + frame(style, available).horizontal());
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

    /** Padding and border together, which is what sits between the border box and the content. */
    private Edges frame(ElementStyle style, int basis){
        return edges(style, ElementStyle.PADDING_TOP, ElementStyle.PADDING_RIGHT, ElementStyle.PADDING_BOTTOM, ElementStyle.PADDING_LEFT, basis)
                .plus(new Edges(
                        style.get(ElementStyle.BORDER_TOP_WIDTH, 0), style.get(ElementStyle.BORDER_RIGHT_WIDTH, 0),
                        style.get(ElementStyle.BORDER_BOTTOM_WIDTH, 0), style.get(ElementStyle.BORDER_LEFT_WIDTH, 0)));
    }

    private static Box shift(Box box, int dx, int dy){
        if (dx == 0 && dy == 0) return box;
        List<Box> children = new ArrayList<>(box.children.size());
        for (Box child : box.children) children.add(shift(child, dx, dy));
        return new Box(box.element, box.style,
                box.x + dx, box.y + dy, box.width, box.height,
                box.contentX + dx, box.contentY + dy, box.contentWidth, box.contentHeight,
                box.lines, children);
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
