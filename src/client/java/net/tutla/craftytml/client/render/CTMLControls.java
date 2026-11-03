package net.tutla.craftytml.client.render;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.element.input.FormControl;
import net.tutla.craftytml.client.ctml.element.input.Input;
import net.tutla.craftytml.client.ctml.element.input.Option;
import net.tutla.craftytml.client.ctml.element.input.Select;
import net.tutla.craftytml.client.dom.CTMLCallback;
import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;

import java.util.List;

/**
 * Everything the things a user fills in need from the screen: drawing the part of a control that is
 * not a box (the tick, the dot, the slider's thumb, the caret, the arrow and the list of a select), and
 * hearing the mouse and the keyboard for them.
 *
 * <p>The state itself lives in the elements, in {@link FormControl} and what extends it; this only
 * says what the user did to it. Whenever something does change, the callback the element names is
 * called with an INPUT, the same one a press on a button is, and reads the new state off the element:
 * its value property, or its checked one. A press on a control is taken by it and does not reach
 * the click callbacks as well, so one change is told about once.
 */
public final class CTMLControls {
    private CTMLControls(){}

    private static final int THUMB = 4;
    private static final int POPUP_BACKGROUND = 0xFF111827;
    private static final int POPUP_BORDER = 0xFF6B7280;
    private static final int POPUP_HIGHLIGHT = 0xFF1D4ED8;
    private static final int POPUP_CHOSEN = 0xFF7DD3FC;
    private static final int TRACK = 0xFF6B7280;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final long BLINK = 530;
    private static final int SELECTION = 0x883B82F6;

    // ---- drawing ----

    /** Whether what a box says is to be drawn dimmed: a placeholder, or the words of something out of use. */
    static boolean dimmed(CTMLLayout.Box box){
        return box.element().getElement() instanceof FormControl control
                && (control.isDisabled() || control.showsPlaceholder());
    }

    /** The colour the placeholder is drawn in, or null where the box is not showing one or has none of its own. */
    static Integer hint(CTMLLayout.Box box){
        return box.element().getElement() instanceof FormControl control && control.showsPlaceholder()
                ? box.style().get(ElementStyle.PLACEHOLDER_COLOR)
                : null;
    }

    /** The part of a control that is drawn over its box: the tick, the dot, the slider and the arrow. */
    static void paint(CTMLLayout.Box box, GuiGraphicsExtractor graphics, float alpha){
        if (!(box.element().getElement() instanceof FormControl control)) return;

        float faded = control.isDisabled() ? alpha * 0.5f : alpha;
        int color = CTMLRenderer.fade(box.style().get(ElementStyle.COLOR, TEXT_COLOR), faded);

        if (control instanceof Select) {
            int middle = box.right() - 8;
            int top = box.y() + box.height() / 2 - 1;
            for (int row = 0; row < 4; row++) {
                graphics.fill(middle - (3 - row), top + row, middle + (3 - row) + 1, top + row + 1, color);
            }
            return;
        }
        if (!(control instanceof Input input)) return;

        switch (input.kind()) {
            case CHECKBOX -> {
                if (!input.isChecked()) return;
                graphics.fill(box.contentX() + 1, box.contentY() + 1,
                        box.contentRight() - 1, box.contentBottom() - 1, color);
            }
            case RADIO -> {
                if (!input.isChecked()) return;
                int size = Math.min(box.contentWidth(), box.contentHeight()) - 2;
                CTMLRenderer.rounded(graphics, box.contentX() + 1, box.contentY() + 1, size, size, size / 2, color);
            }
            case COLOR -> graphics.fill(box.contentX(), box.contentY(), box.contentRight(), box.contentBottom(),
                    CTMLRenderer.fade(0xFF000000 | input.rgb(), faded));
            case DATE -> {
                int left = box.right() - 12;
                int top = box.y() + box.height() / 2 - 4;
                graphics.fill(left, top, left + 9, top + 9, color);
                graphics.fill(left + 1, top + 3, left + 8, top + 8, CTMLRenderer.fade(0xFF111827, faded));
                graphics.fill(left + 2, top + 4, left + 3, top + 5, color);
                graphics.fill(left + 4, top + 4, left + 5, top + 5, color);
                graphics.fill(left + 6, top + 4, left + 7, top + 5, color);
                graphics.fill(left + 2, top + 6, left + 3, top + 7, color);
                graphics.fill(left + 4, top + 6, left + 5, top + 7, color);
            }
            case RANGE -> {
                int middle = box.y() + box.height() / 2;
                graphics.fill(box.x(), middle - 1, box.right(), middle + 1, CTMLRenderer.fade(TRACK, faded));
                int reach = Math.max(0, box.width() - THUMB);
                int left = box.x() + Math.round(input.share() * reach);
                graphics.fill(left, box.y(), left + THUMB, box.bottom(), color);
            }
            default -> {}
        }
    }

    /**
     * Highlights what is selected in the text field that has the keyboard, a stretch to a line. It is drawn
     * under the text, so the words stay readable, and a selection that runs over the end of a line takes
     * a little extra width there, the way an editor shows the line break being selected.
     */
    static void selection(CTMLLayout.Box box, GuiGraphicsExtractor graphics, Font font, float alpha){
        if (!(box.element().getElement() instanceof FormControl control) || !control.isTextField()
                || !control.isFocused() || !control.hasSelection() || control.showsPlaceholder()) return;

        List<CTMLLayout.TextLine> lines = box.lines();
        String value = control.value();
        int from = control.selectionStart();
        int to = control.selectionEnd();
        int color = CTMLRenderer.fade(box.style().get(ElementStyle.SELECTION_COLOR, SELECTION), alpha);

        int start = 0;
        for (int row = 0; row < lines.size() && start <= value.length(); row++) {
            int end = value.indexOf('\n', start);
            int stop = end < 0 ? value.length() : end;
            {
                int a = Math.max(from, start);
                int b = Math.min(to, stop);
                boolean breaks = to > stop && end >= 0; // the line break itself is inside the selection
                if (b > a || (breaks && from <= stop)) {
                    CTMLLayout.TextLine line = lines.get(row);
                    int left = line.x() + measure(font, line, shown(control, value.substring(start, Math.max(a, start))));
                    int right = line.x() + measure(font, line, shown(control, value.substring(start, Math.max(b, a))))
                            + (breaks ? 3 : 0);
                    int x = box.contentX() - box.port().scrollX();
                    int y = box.contentY() + line.y() - box.port().scrollY();
                    graphics.fill(x + left, y, x + right, y + Math.max(1, Math.round(font.lineHeight * line.scale())), color);
                }
            }
            if (end < 0 || end >= to) break;
            start = end + 1;
        }
    }

    /** The caret of whatever text field has the keyboard, blinking, and cut to the box the way its text is. */
    static void caret(CTMLLayout.Box box, GuiGraphicsExtractor graphics, Font font, float alpha){
        if (!(box.element().getElement() instanceof FormControl control)
                || !control.isTextField() || !control.isFocused() || !control.isEditable()) return;
        if ((System.currentTimeMillis() / BLINK) % 2 != 0) return;

        int[] at = caretPoint(box, font, control);
        if (at == null) return;
        int x = box.contentX() + at[0] - box.port().scrollX();
        int y = box.contentY() + at[1] - box.port().scrollY();
        int own = box.style().get(ElementStyle.COLOR, TEXT_COLOR);
        graphics.fill(x, y, x + 1, y + at[2], CTMLRenderer.fade(box.style().get(ElementStyle.CARET_COLOR, own), alpha));
    }

    /**
     * Where the caret is in its box before any scrolling, as { x, y, height } from the content corner, or
     * null where there is no line to put it on. A line is as long as the text in front of the caret
     * measures, the way the line itself was measured.
     */
    private static int[] caretPoint(CTMLLayout.Box box, Font font, FormControl control){
        List<CTMLLayout.TextLine> lines = box.lines();
        if (lines.isEmpty()) return null;

        String value = control.value();
        int caret = control.caret();
        int row = 0;
        for (int i = 0; i < caret; i++) if (value.charAt(i) == '\n') row++;
        CTMLLayout.TextLine line = lines.get(Math.min(row, lines.size() - 1));

        int start = value.lastIndexOf('\n', caret - 1) + 1;
        String before = shown(control, value.substring(start, caret));
        int x = line.x() + (control.showsPlaceholder() ? 0 : measure(font, line, before));
        return new int[]{ x, line.y(), Math.max(1, Math.round(font.lineHeight * line.scale())) };
    }

    /** What a stretch of the value looks like on screen, which is stars for a password. */
    private static String shown(FormControl control, String text){
        if (control instanceof Input input && input.kind() == Input.Kind.PASSWORD) {
            return "*".repeat(text.codePointCount(0, text.length()));
        }
        return text;
    }

    private static int measure(Font font, CTMLLayout.TextLine line, String text){
        return Math.round(font.width(text) * line.scale()) + line.spacing() * text.length();
    }

    // ---- the list of a select ----

    /** Whether a select has its list open, which is then what a press has to be told to first. */
    public static boolean listOpen(CTMLLayout.Box root){
        return root != null && open(root) != null;
    }

    /** The open select or picker under the root, or null: only one popup is ever open at a time. */
    private static CTMLLayout.Box open(CTMLLayout.Box box){
        if (box.element().getElement() instanceof FormControl control && control.isOpen()) return box;
        for (CTMLLayout.Box child : box.children()) {
            CTMLLayout.Box found = open(child);
            if (found != null) return found;
        }
        return null;
    }

    /** Where the list sits as { x, y, width, height, row height }: under its box, or over it where there is no room. */
    private static int[] popup(CTMLLayout.Box box, Select select, Font font, int screenHeight){
        int row = font.lineHeight + 4;
        int height = select.options().size() * row + 2;
        int y = box.bottom();
        if (y + height > screenHeight && box.y() - height >= 0) y = box.y() - height;
        return new int[]{ box.x(), y, box.width(), height, row };
    }

    /** Draws the open list over everything else, and lets the cursor walk the highlight over its rows. */
    static void paintPopup(CTMLLayout.Box root, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY){
        CTMLLayout.Box box = open(root);
        if (box == null) return;
        if (box.element().getElement() instanceof Input input) {
            int[] where = CTMLPickers.area(box, input, graphics.guiWidth(), graphics.guiHeight());
            if (input.kind() == Input.Kind.DATE) CTMLPickers.paintDate(input, where, font, graphics, mouseX, mouseY);
            else CTMLPickers.paintColor(input, where, font, graphics, mouseX, mouseY);
            return;
        }
        Select select = (Select) box.element().getElement();

        int[] area = popup(box, select, font, graphics.guiHeight());
        List<Option> options = select.options();
        int hovered = rowAt(area, mouseX, mouseY, options.size());
        if (hovered >= 0) select.highlight(hovered);

        graphics.fill(area[0], area[1], area[0] + area[2], area[1] + area[3], POPUP_BORDER);
        graphics.fill(area[0] + 1, area[1] + 1, area[0] + area[2] - 1, area[1] + area[3] - 1, POPUP_BACKGROUND);
        for (int i = 0; i < options.size(); i++) {
            int top = area[1] + 1 + i * area[4];
            if (i == select.highlighted()) graphics.fill(area[0] + 1, top, area[0] + area[2] - 1, top + area[4], POPUP_HIGHLIGHT);
            int color = options.get(i).isDisabled() ? POPUP_BORDER
                    : i == select.selectedIndex() ? POPUP_CHOSEN : TEXT_COLOR;
            graphics.text(font, options.get(i).label(), area[0] + 4, top + 2, color, true);
        }
    }

    private static int rowAt(int[] area, double x, double y, int rows){
        if (x < area[0] || x >= area[0] + area[2] || y < area[1] + 1) return -1;
        int row = (int) ((y - area[1] - 1) / area[4]);
        return row < rows ? row : -1;
    }

    // ---- the mouse ----

    /**
     * A left press at this spot. Returns true where a control took it, which is every press that lands
     * on one: a text field puts its caret there, a tick or a radio button changes, a range goes to that
     * point and a select opens its list or takes the choice pressed in it. A press anywhere while a list
     * is open closes it, and is let through to whatever is under it unless it landed on the list.
     */
    public static boolean press(CTMLLayout.Box root, DOMContext ctx, Font font, int screenWidth, int screenHeight,
                                double x, double y, boolean shift){
        if (root == null) return false;
        CTMLLayout.Box listed = open(root);
        if (listed != null && listed.element().getElement() instanceof Input picker) {
            int[] where = CTMLPickers.area(listed, picker, screenWidth, screenHeight);
            boolean taken = picker.kind() == Input.Kind.DATE
                    ? CTMLPickers.pressDate(picker, ctx, listed.element(), where, x, y)
                    : CTMLPickers.pressColor(picker, ctx, listed.element(), where, x, y);
            if (taken) return true;
            picker.close();
            return CTMLScroll.within(x, y, listed.x(), listed.y(), listed.right(), listed.bottom());
        }
        if (listed != null) {
            Select select = (Select) listed.element().getElement();
            int[] area = popup(listed, select, font, screenHeight);
            int row = rowAt(area, x, y, select.options().size());
            select.close();
            if (row >= 0) {
                if (select.choose(row)) fire(ctx, listed.element(), CTMLCallback.NO_KEY);
                return true;
            }
            // a press on the box that opened it closes it and no more, or it would open straight again
            return CTMLScroll.within(x, y, listed.x(), listed.y(), listed.right(), listed.bottom());
        }

        CTMLLayout.Box box = under(root, x, y);
        if (box == null) return false;
        FormControl control = (FormControl) box.element().getElement();
        if (control.isDisabled()) return true;

        if (control instanceof Select select) {
            select.open();
        } else if (control instanceof Input input) {
            switch (input.kind()) {
                case CHECKBOX -> {
                    if (input.setChecked(!input.isChecked())) fire(ctx, box.element(), CTMLCallback.NO_KEY);
                }
                case RADIO -> {
                    if (tick(ctx, box.element(), input)) fire(ctx, box.element(), CTMLCallback.NO_KEY);
                }
                case RANGE -> {
                    if (input.slideTo(share(box, x))) fire(ctx, box.element(), CTMLCallback.NO_KEY);
                }
                case DATE, COLOR -> input.openPicker();
                case BUTTON, SUBMIT, RESET -> {
                    return false; // pressing it is an input of its own, which the click path already tells of
                }
                case HIDDEN -> {
                    return false;
                }
                default -> place(box, font, control, x, y, shift);
            }
        } else {
            place(box, font, control, x, y, shift); // a textarea
        }
        return true;
    }

    /**
     * What a press on a text field does to the caret: puts it there, or stretches the selection to there
     * with shift held; two quick presses select the word under it and three select the line.
     */
    private static void place(CTMLLayout.Box box, Font font, FormControl control, double x, double y, boolean shift){
        int index = caretAt(box, font, control, x, y);
        int clicks = control.click(System.currentTimeMillis());
        if (shift) control.moveTo(index, true);
        else if (clicks == 2) control.selectWordAt(index);
        else if (clicks >= 3) control.selectLineAt(index);
        else control.setCaret(index);
    }

    /** The text field under the cursor, which is the one a drag has to keep stretching the selection of, or null. */
    public static DOMTreeElement field(CTMLLayout.Box root, double x, double y){
        CTMLLayout.Box box = under(root, x, y);
        if (box == null || !(box.element().getElement() instanceof FormControl control)) return null;
        return control.isTextField() && !control.isDisabled() ? box.element() : null;
    }

    /** Stretches the selection of a text field to the cursor while the button is held on it. */
    public static void extend(CTMLLayout.Box root, Font font, DOMTreeElement element, double x, double y){
        CTMLLayout.Box box = find(root, element);
        if (box == null || !(element.getElement() instanceof FormControl control)) return;
        control.moveTo(caretAt(box, font, control, x, y), true);
    }

    /** Something the cursor keeps steering while the button is held, which is what a drag in a picker is. */
    public interface Hold {
        void follow(double x, double y);
    }

    /** What a press at this spot would start dragging in the open colour picker, or null. */
    public static Hold hold(CTMLLayout.Box root, DOMContext ctx, int screenWidth, int screenHeight, double x, double y){
        CTMLLayout.Box listed = root == null ? null : open(root);
        if (listed == null || !(listed.element().getElement() instanceof Input input)
                || input.kind() != Input.Kind.COLOR) return null;
        return CTMLPickers.holdColor(input, ctx, listed.element(),
                CTMLPickers.area(listed, input, screenWidth, screenHeight), x, y);
    }

    /** The range under the cursor, which is the one a drag has to keep following, or null. */
    public static DOMTreeElement slider(CTMLLayout.Box root, double x, double y){
        CTMLLayout.Box box = under(root, x, y);
        if (box == null || !(box.element().getElement() instanceof Input input)) return null;
        return input.kind() == Input.Kind.RANGE && !input.isDisabled() ? box.element() : null;
    }

    /** Follows the cursor with a range that is being dragged. */
    public static void slide(CTMLLayout.Box root, DOMContext ctx, DOMTreeElement element, double x){
        CTMLLayout.Box box = find(root, element);
        if (box == null || !(element.getElement() instanceof Input input)) return;
        if (input.slideTo(share(box, x))) fire(ctx, element, CTMLCallback.NO_KEY);
    }

    /** How far along its track a cursor is, so a range lands where the middle of its thumb is held. */
    private static float share(CTMLLayout.Box box, double x){
        int reach = Math.max(1, box.width() - THUMB);
        return (float) ((x - box.x() - THUMB / 2.0) / reach);
    }

    /** The innermost box under the cursor that is a control, or null. */
    private static CTMLLayout.Box under(CTMLLayout.Box root, double x, double y){
        List<CTMLLayout.Box> path = CTMLFocus.path(root, x, y);
        for (int i = path.size() - 1; i >= 0; i--) {
            if (path.get(i).element().getElement() instanceof FormControl) return path.get(i);
        }
        return null;
    }

    private static CTMLLayout.Box find(CTMLLayout.Box box, DOMTreeElement element){
        if (box.element() == element) return box;
        for (CTMLLayout.Box child : box.children()) {
            CTMLLayout.Box found = find(child, element);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Ticks a radio button and unticks every other one under the same name in the same screen. Says
     * whether anything changed: a button that is already ticked stays so, the way a browser has it.
     */
    private static boolean tick(DOMContext ctx, DOMTreeElement element, Input radio){
        if (!radio.setChecked(true)) return false;

        String group = element.getString("name");
        if (group == null || group.isEmpty() || ctx == null || ctx.getHeadNode() == null) return true;
        ctx.getHeadNode().walk(node -> {
            if (node != element && node instanceof DOMTreeElement other
                    && other.getElement() instanceof Input input && input.kind() == Input.Kind.RADIO
                    && group.equals(other.getString("name"))) {
                input.setChecked(false);
            }
        });
        return true;
    }

    /** Which index into the value a press at this spot is in front of. */
    private static int caretAt(CTMLLayout.Box box, Font font, FormControl control, double x, double y){
        List<CTMLLayout.TextLine> lines = box.lines();
        String value = control.value();
        if (lines.isEmpty() || control.showsPlaceholder()) return 0;

        // the line the press is on: each hard line of the value is one line of the box, in order
        double down = y - box.contentY() + box.port().scrollY();
        int row = 0;
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).y() <= down) row = i;

        int start = 0;
        for (int seen = 0; seen < row; seen++) {
            int next = value.indexOf('\n', start);
            if (next < 0) return value.length();
            start = next + 1;
        }
        int end = value.indexOf('\n', start);
        String line = value.substring(start, end < 0 ? value.length() : end);

        CTMLLayout.TextLine drawn = lines.get(row);
        double across = x - box.contentX() - drawn.x() + box.port().scrollX();
        int index = 0;
        while (index < line.length()) {
            int next = index + Character.charCount(line.codePointAt(index));
            int reach = measure(font, drawn, shown(control, line.substring(0, next)));
            int was = measure(font, drawn, shown(control, line.substring(0, index)));
            if (across < (was + reach) / 2.0) break; // closer to this edge of the letter than the far one
            index = next;
        }
        return start + index;
    }

    // ---- scrolling to the caret ----

    /**
     * Scrolls a text field just far enough to keep its caret in sight, and says whether it had to. Said
     * once layout is worked out, since it is the lines of the box that say where the caret is.
     */
    static boolean reveal(CTMLLayout.Box root, DOMTreeElement element, Font font){
        CTMLLayout.Box box = find(root, element);
        if (box == null || !(element.getElement() instanceof FormControl control)) return false;
        int[] at = caretPoint(box, font, control);
        if (at == null) return false;

        int scrollX = box.port().scrollX();
        int scrollY = box.port().scrollY();
        int nextX = scrollX;
        int nextY = scrollY;
        if (at[0] < scrollX) nextX = at[0];
        else if (at[0] + 1 > scrollX + box.contentWidth()) nextX = at[0] + 1 - box.contentWidth();
        if (at[1] < scrollY) nextY = at[1];
        else if (at[1] + at[2] > scrollY + box.contentHeight()) nextY = at[1] + at[2] - box.contentHeight();

        nextX = Math.max(0, nextX);
        nextY = Math.max(0, nextY);
        if (nextX == scrollX && nextY == scrollY) return false;
        element.setScrollOffset(nextX, nextY);
        return true;
    }

    // ---- the keyboard ----

    /** Typing: a character goes into the text field that has the keyboard. */
    public static boolean type(DOMContext ctx, DOMTreeElement focus, int codePoint){
        if (focus == null || !(focus.getElement() instanceof FormControl control)) return false;
        if (control instanceof Input color && color.kind() == Input.Kind.COLOR && color.isOpen()) {
            return color.typeHex(codePoint); // the hex field of the open picker
        }
        if (!control.isTextField() || control.isDisabled()) return false;
        if (control.insert(new String(Character.toChars(codePoint)))) fire(ctx, focus, codePoint);
        return true;
    }

    /**
     * A key for the control that has the keyboard, and whether it took it. The keys a text field uses
     * to edit are its own and are taken even where there is nothing to do, so backspace at the start of
     * a field does not fall through to whatever else listens for it.
     */
    public static boolean key(DOMContext ctx, DOMTreeElement focus, KeyEvent event){
        if (focus == null || !(focus.getElement() instanceof FormControl control) || control.isDisabled()) return false;
        int key = event.input();

        if (control instanceof Select select) return selectKey(ctx, focus, select, key);

        if (control instanceof Input input && !input.isTextField()) {
            return switch (input.kind()) {
                case CHECKBOX -> key == InputConstants.KEY_SPACE
                        && input.setChecked(!input.isChecked()) && told(ctx, focus, key);
                case RADIO -> key == InputConstants.KEY_SPACE && tick(ctx, focus, input) && told(ctx, focus, key);
                case RANGE -> range(ctx, focus, input, key);
                case DATE -> CTMLPickers.dateKey(ctx, focus, input, key);
                case COLOR -> CTMLPickers.colorKey(ctx, focus, input, key);
                default -> false;
            };
        }

        // a text field
        boolean word = event.hasControlDown();
        boolean shift = event.hasShiftDown();
        boolean shortcut = event.hasControlDownWithQuirk();
        boolean password = control instanceof Input field && field.kind() == Input.Kind.PASSWORD;

        if (event.isSelectAll()) {
            control.selectAll();
            return true;
        }
        if (event.isCopy() || event.isCut()) {
            // a password gives nothing away, and a selection is what is copied: nothing selected copies nothing
            if (!password && control.hasSelection()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(control.selectedText());
                if (event.isCut() && control.deleteSelection()) fire(ctx, focus, key);
            }
            return true;
        }
        if (event.isPaste()) {
            String text = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (control.insert(text)) fire(ctx, focus, key);
            return true;
        }
        if (shortcut && (key == InputConstants.KEY_Z || key == InputConstants.KEY_Y)) {
            boolean again = key == InputConstants.KEY_Y || shift;
            if (again ? control.redo() : control.undo()) fire(ctx, focus, key);
            return true;
        }

        switch (key) {
            case InputConstants.KEY_BACKSPACE -> {
                if (word ? control.deleteWord(-1) : control.backspace()) fire(ctx, focus, key);
            }
            case InputConstants.KEY_DELETE -> {
                if (shift && control.hasSelection() && !password) { // shift delete is cut, the old way
                    Minecraft.getInstance().keyboardHandler.setClipboard(control.selectedText());
                    if (control.deleteSelection()) fire(ctx, focus, key);
                } else if (word ? control.deleteWord(1) : control.delete()) {
                    fire(ctx, focus, key);
                }
            }
            case InputConstants.KEY_LEFT -> control.step(-1, word, shift);
            case InputConstants.KEY_RIGHT -> control.step(1, word, shift);
            case InputConstants.KEY_HOME -> control.moveTo(control.isMultiline() && !word ? control.lineStart() : 0, shift);
            case InputConstants.KEY_END -> control.moveTo(control.isMultiline() && !word
                    ? control.lineEnd() : control.value().length(), shift);
            case InputConstants.KEY_UP, InputConstants.KEY_DOWN -> {
                int way = key == InputConstants.KEY_UP ? -1 : 1;
                if (control.isMultiline()) control.line(way, shift);
                else if (control instanceof Input input && input.nudge(-way)) fire(ctx, focus, key);
                else if (!(control instanceof Input input2 && input2.kind() == Input.Kind.NUMBER)) return false;
            }
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                if (!control.isMultiline()) return false;
                if (control.insert("\n")) fire(ctx, focus, key);
            }
            default -> {
                return false; // a letter is heard as a character, and everything else is for someone else
            }
        }
        return true;
    }

    private static boolean range(DOMContext ctx, DOMTreeElement focus, Input input, int key){
        boolean moved;
        switch (key) {
            case InputConstants.KEY_LEFT, InputConstants.KEY_DOWN -> moved = input.nudge(-1);
            case InputConstants.KEY_RIGHT, InputConstants.KEY_UP -> moved = input.nudge(1);
            case InputConstants.KEY_HOME -> moved = input.setValue(String.valueOf(input.lowest()));
            case InputConstants.KEY_END -> moved = input.setValue(String.valueOf(input.highest()));
            default -> {
                return false;
            }
        }
        if (moved) fire(ctx, focus, key);
        return true;
    }

    private static boolean selectKey(DOMContext ctx, DOMTreeElement focus, Select select, int key){
        boolean up = key == InputConstants.KEY_UP;
        boolean down = key == InputConstants.KEY_DOWN;
        boolean confirm = key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER
                || key == InputConstants.KEY_SPACE;

        if (select.isOpen()) {
            if (up || down) {
                select.highlight(select.highlighted() + (up ? -1 : 1));
            } else if (confirm) {
                select.close();
                if (select.choose(select.highlighted())) fire(ctx, focus, key);
            } else if (key == InputConstants.KEY_ESCAPE) {
                select.close();
            } else {
                return false;
            }
            return true;
        }

        if (confirm) {
            select.open();
        } else if (up || down) {
            if (select.choose(select.selectedIndex() + (up ? -1 : 1))) fire(ctx, focus, key);
        } else {
            return false;
        }
        return true;
    }

    /** Tells the callback the element names that it changed. */
    static void fire(DOMContext ctx, DOMTreeElement element, int key){
        if (ctx != null) ctx.fire(CTMLCallback.CallbackType.INPUT, element, key);
    }

    private static boolean told(DOMContext ctx, DOMTreeElement element, int key){
        fire(ctx, element, key);
        return true;
    }

    /** Closes whatever list is open, for a screen that is being put away. */
    public static void closeAll(CTMLLayout.Box root){
        CTMLLayout.Box box = root == null ? null : open(root);
        if (box != null) ((FormControl) box.element().getElement()).close();
    }
}
