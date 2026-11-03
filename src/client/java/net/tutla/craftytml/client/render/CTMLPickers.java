package net.tutla.craftytml.client.render;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.tutla.craftytml.client.ctml.element.input.ColorMath;
import net.tutla.craftytml.client.ctml.element.input.Input;
import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * The two pickers that open under an input: a calendar under a date, and a colour picker under a colour.
 * They are drawn over everything else, the way a select's list is, and are not styled by classes.
 *
 * <p>The calendar is a month of days with arrows to walk months and years and a row to pick today or
 * clear the date. The colour picker has four ways to choose, switched by tabs along its top: a hue wheel
 * with a saturation and brightness square inside it, a saturation and brightness box with a hue bar
 * under it, a slider each for red, green, blue, hue, saturation and brightness, and a grid of swatches.
 * Under all of them is the colour as it stands and its hex, which can be typed over while it is open.
 *
 * <p>Every geometry in here is worked out from the popup's corner by the same few numbers, so what is
 * drawn, what a press lands on and what a drag follows cannot drift apart.
 */
final class CTMLPickers {
    private CTMLPickers(){}

    private static final int BACKGROUND = 0xFF111827;
    private static final int BORDER = 0xFF6B7280;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFF6B7280;
    private static final int ACTIVE = 0xFF1D4ED8;
    private static final int HOVER = 0xFF374151;
    private static final int TAB_OFF = 0xFF1F2937;
    private static final int RING = 0xFF38BDF8;

    // the calendar
    private static final int DATE_W = 100;
    private static final int DATE_H = 102;
    private static final int DAY_W = 14;
    private static final int DAY_H = 11;
    private static final int HEADER_H = 12;
    private static final int WEEK_H = 10;
    private static final int GRID_Y = HEADER_H + WEEK_H + 1;
    private static final int FOOT_Y = GRID_Y + DAY_H * 6;

    // the colour picker
    private static final int COLOR_W = 140;
    private static final int COLOR_H = 118;
    private static final int TAB_W = 35;
    private static final int TAB_H = 11;
    private static final int BODY_Y = 12;
    private static final int BODY_H = 90;
    private static final int WHEEL_OUT = 42;
    private static final int WHEEL_IN = 31;
    private static final int WHEEL_HALF = 20;
    private static final int BOX_W = 120;
    private static final int BOX_H = 70;
    private static final int BAR_X = 14;
    private static final int BAR_W = 90;
    private static final int BAR_STEP = 14;
    private static final int CELL_W = 11;
    private static final int CELL_H = 12;
    private static final String[] TABS = { "wheel", "box", "bars", "swatch" };
    private static final String[] CHANNELS = { "R", "G", "B", "H", "S", "V" };

    /** Where the popup sits as { x, y, width, height }: under its box, or over it where there is no room. */
    static int[] area(CTMLLayout.Box box, Input input, int screenWidth, int screenHeight){
        boolean date = input.kind() == Input.Kind.DATE;
        int w = date ? DATE_W : COLOR_W;
        int h = date ? DATE_H : COLOR_H;
        int x = Math.clamp(box.x(), 0, Math.max(0, screenWidth - w));
        int y = box.bottom();
        if (y + h > screenHeight && box.y() - h >= 0) y = box.y() - h;
        return new int[]{ x, y, w, h };
    }

    private static boolean inside(int[] a, double x, double y){
        return x >= a[0] && x < a[0] + a[2] && y >= a[1] && y < a[1] + a[3];
    }

    // ---- drawing, shared ----

    private static void frame(GuiGraphicsExtractor g, int[] a){
        g.fill(a[0], a[1], a[0] + a[2], a[1] + a[3], BORDER);
        g.fill(a[0] + 1, a[1] + 1, a[0] + a[2] - 1, a[1] + a[3] - 1, BACKGROUND);
    }

    private static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color){
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static void centered(GuiGraphicsExtractor g, Font font, String text, int x, int y, int w, int h, int color){
        g.text(font, text, x + (w - font.width(text)) / 2, y + (h - font.lineHeight) / 2 + 1, color, false);
    }

    /** A small marker on a picker: a white square with a dark edge, so it shows on any colour. */
    private static void marker(GuiGraphicsExtractor g, int x, int y){
        g.fill(x - 3, y - 3, x + 4, y + 4, 0xFF000000);
        g.fill(x - 2, y - 2, x + 3, y + 3, TEXT);
        g.fill(x - 1, y - 1, x + 2, y + 2, 0xFF000000);
    }

    // ---- the calendar ----

    /** The first day of the grid: the Monday on or before the first of the month. */
    private static LocalDate gridStart(YearMonth month){
        LocalDate first = month.atDay(1);
        return first.minusDays(first.getDayOfWeek().getValue() - 1);
    }

    static void paintDate(Input input, int[] a, Font font, GuiGraphicsExtractor g, int mx, int my){
        frame(g, a);
        YearMonth month = input.viewing();
        LocalDate chosen = input.date();
        LocalDate cursor = input.cursor();
        LocalDate today = LocalDate.now();
        int x = a[0] + 1;
        int y = a[1] + 1;

        String[] nav = { "<<", "<", null, null, null, ">", ">>" };
        for (int col = 0; col < 7; col++) {
            int cx = x + col * DAY_W;
            if (nav[col] == null) continue;
            if (hit(mx, my, cx, y, DAY_W, HEADER_H)) g.fill(cx, y, cx + DAY_W, y + HEADER_H, HOVER);
            centered(g, font, nav[col], cx, y, DAY_W, HEADER_H, TEXT);
        }
        String title = month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + month.getYear();
        centered(g, font, title, x + 2 * DAY_W, y, 3 * DAY_W, HEADER_H, TEXT);

        String week = "MTWTFSS";
        for (int col = 0; col < 7; col++) {
            centered(g, font, week.substring(col, col + 1), x + col * DAY_W, y + HEADER_H, DAY_W, WEEK_H, DIM);
        }

        LocalDate day = gridStart(month);
        for (int i = 0; i < 42; i++, day = day.plusDays(1)) {
            int cx = x + (i % 7) * DAY_W;
            int cy = y + GRID_Y + (i / 7) * DAY_H;
            boolean here = day.getMonth() == month.getMonth();
            if (day.equals(chosen)) g.fill(cx, cy, cx + DAY_W, cy + DAY_H, ACTIVE);
            else if (hit(mx, my, cx, cy, DAY_W, DAY_H)) g.fill(cx, cy, cx + DAY_W, cy + DAY_H, HOVER);
            if (day.equals(cursor)) outline(g, cx, cy, DAY_W, DAY_H, RING);
            if (day.equals(today)) g.fill(cx + 2, cy + DAY_H - 2, cx + DAY_W - 2, cy + DAY_H - 1, RING);
            centered(g, font, String.valueOf(day.getDayOfMonth()), cx, cy, DAY_W, DAY_H, here ? TEXT : DIM);
        }

        int fy = y + FOOT_Y;
        int half = 7 * DAY_W / 2;
        g.fill(x, fy, x + 7 * DAY_W, fy + 1, BORDER);
        if (hit(mx, my, x, fy + 1, half, 12)) g.fill(x, fy + 1, x + half, fy + 13, HOVER);
        if (hit(mx, my, x + half, fy + 1, half, 12)) g.fill(x + half, fy + 1, x + 2 * half, fy + 13, HOVER);
        centered(g, font, "today", x, fy + 1, half, 11, TEXT);
        centered(g, font, "clear", x + half, fy + 1, half, 11, TEXT);
    }

    private static boolean hit(double mx, double my, int x, int y, int w, int h){
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** A press anywhere in the calendar. Says whether it was inside, which is whether it was taken. */
    static boolean pressDate(Input input, DOMContext ctx, DOMTreeElement element, int[] a, double px, double py){
        if (!inside(a, px, py)) return false;
        int x = (int) px - a[0] - 1;
        int y = (int) py - a[1] - 1;
        if (x < 0 || y < 0 || x >= 7 * DAY_W) return true;

        if (y < HEADER_H) {
            YearMonth month = input.viewing();
            switch (x / DAY_W) {
                case 0 -> input.view(month.minusYears(1));
                case 1 -> input.view(month.minusMonths(1));
                case 5 -> input.view(month.plusMonths(1));
                case 6 -> input.view(month.plusYears(1));
                default -> {}
            }
        } else if (y >= GRID_Y && y < FOOT_Y) {
            LocalDate day = gridStart(input.viewing()).plusDays((long) ((y - GRID_Y) / DAY_H) * 7 + x / DAY_W);
            if (input.setDate(day)) CTMLControls.fire(ctx, element, 0);
            input.close();
        } else if (y >= FOOT_Y) {
            boolean clear = x >= 7 * DAY_W / 2;
            if (input.setDate(clear ? null : LocalDate.now())) CTMLControls.fire(ctx, element, 0);
            input.close();
        }
        return true;
    }

    /** The keys of a date: they walk the calendar while it is open and step the date while it is not. */
    static boolean dateKey(DOMContext ctx, DOMTreeElement focus, Input input, int key){
        boolean confirm = key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER
                || key == InputConstants.KEY_SPACE;

        if (input.isOpen()) {
            LocalDate at = input.cursor();
            switch (key) {
                case InputConstants.KEY_LEFT -> input.moveCursor(at.minusDays(1));
                case InputConstants.KEY_RIGHT -> input.moveCursor(at.plusDays(1));
                case InputConstants.KEY_UP -> input.moveCursor(at.minusDays(7));
                case InputConstants.KEY_DOWN -> input.moveCursor(at.plusDays(7));
                case InputConstants.KEY_PAGEUP -> input.moveCursor(at.minusMonths(1));
                case InputConstants.KEY_PAGEDOWN -> input.moveCursor(at.plusMonths(1));
                case InputConstants.KEY_ESCAPE -> input.close();
                default -> {
                    if (!confirm) return false;
                    if (input.setDate(at)) CTMLControls.fire(ctx, focus, key);
                    input.close();
                }
            }
            return true;
        }

        if (confirm) {
            input.openPicker();
            return true;
        }
        if (!input.isEditable()) return false;

        LocalDate now = input.date() != null ? input.date() : LocalDate.now();
        LocalDate next;
        switch (key) {
            case InputConstants.KEY_UP -> next = now.plusDays(1);
            case InputConstants.KEY_DOWN -> next = now.minusDays(1);
            case InputConstants.KEY_PAGEUP -> next = now.plusMonths(1);
            case InputConstants.KEY_PAGEDOWN -> next = now.minusMonths(1);
            case InputConstants.KEY_DELETE, InputConstants.KEY_BACKSPACE -> next = null;
            default -> {
                return false;
            }
        }
        if (input.setDate(next)) CTMLControls.fire(ctx, focus, key);
        return true;
    }

    // ---- the colour picker ----

    static void paintColor(Input input, int[] a, Font font, GuiGraphicsExtractor g, int mx, int my){
        frame(g, a);
        int x = a[0] + 1;
        int y = a[1] + 1;
        int mode = input.mode();

        for (int t = 0; t < 4; t++) {
            int tx = x + t * TAB_W;
            g.fill(tx, y, tx + TAB_W - 1, y + TAB_H, t == mode ? ACTIVE : hit(mx, my, tx, y, TAB_W, TAB_H) ? HOVER : TAB_OFF);
            centered(g, font, TABS[t], tx, y, TAB_W - 1, TAB_H, TEXT);
        }

        int bx = x;
        int by = y + BODY_Y;
        switch (mode) {
            case 0 -> paintWheel(input, g, bx + 69, by + 45);
            case 1 -> paintBox(input, g, bx + 9, by + 2, bx + 9, by + 76);
            case 2 -> paintBars(input, font, g, bx, by);
            default -> paintSwatches(input, g, bx + 3, by + 3);
        }

        int fy = y + BODY_Y + BODY_H;
        int current = input.rgb();
        g.fill(x, fy, x + 138, fy + 1, BORDER);
        g.fill(x + 3, fy + 2, x + 25, fy + 12, 0xFF000000 | current);
        outline(g, x + 3, fy + 2, 22, 10, BORDER);
        String typed = input.typedHex();
        g.text(font, typed != null ? "#" + typed + "_" : ColorMath.hex(current), x + 30, fy + 3, TEXT, false);
    }

    private static void paintWheel(Input input, GuiGraphicsExtractor g, int cx, int cy){
        for (int py = -WHEEL_OUT; py < WHEEL_OUT; py += 2) {
            for (int px = -WHEEL_OUT; px < WHEEL_OUT; px += 2) {
                double dx = px + 1;
                double dy = py + 1;
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d < WHEEL_IN || d > WHEEL_OUT) continue;
                g.fill(cx + px, cy + py, cx + px + 2, cy + py + 2, 0xFF000000 | ColorMath.hsvToRgb(angle(dx, dy), 1, 1));
            }
        }
        int side = WHEEL_HALF * 2;
        for (int sy = 0; sy < side; sy += 2) {
            for (int sx = 0; sx < side; sx += 2) {
                int rgb = ColorMath.hsvToRgb(input.hue(), (sx + 1) / (float) side, 1 - (sy + 1) / (float) side);
                g.fill(cx - WHEEL_HALF + sx, cy - WHEEL_HALF + sy, cx - WHEEL_HALF + sx + 2, cy - WHEEL_HALF + sy + 2,
                        0xFF000000 | rgb);
            }
        }
        double theta = input.hue() * 2 * Math.PI;
        double middle = (WHEEL_IN + WHEEL_OUT) / 2.0;
        marker(g, cx + (int) Math.round(Math.cos(theta) * middle), cy + (int) Math.round(Math.sin(theta) * middle));
        marker(g, cx - WHEEL_HALF + Math.round(input.saturation() * side),
                cy - WHEEL_HALF + Math.round((1 - input.brightness()) * side));
    }

    /** A hue from the direction of a point from the middle: red to the right, going clockwise on the screen. */
    private static float angle(double dx, double dy){
        double turns = Math.atan2(dy, dx) / (2 * Math.PI);
        return (float) (turns < 0 ? turns + 1 : turns);
    }

    private static void paintBox(Input input, GuiGraphicsExtractor g, int x, int y, int hx, int hy){
        for (int sy = 0; sy < BOX_H; sy += 3) {
            int rows = Math.min(3, BOX_H - sy);
            for (int sx = 0; sx < BOX_W; sx += 3) {
                int cols = Math.min(3, BOX_W - sx);
                int rgb = ColorMath.hsvToRgb(input.hue(), (sx + 1) / (float) BOX_W, 1 - (sy + 1) / (float) BOX_H);
                g.fill(x + sx, y + sy, x + sx + cols, y + sy + rows, 0xFF000000 | rgb);
            }
        }
        marker(g, x + Math.round(input.saturation() * (BOX_W - 1)), y + Math.round((1 - input.brightness()) * (BOX_H - 1)));

        for (int i = 0; i < BOX_W; i++) {
            g.fill(hx + i, hy, hx + i + 1, hy + 10, 0xFF000000 | ColorMath.hsvToRgb(i / (float) (BOX_W - 1), 1, 1));
        }
        int at = hx + Math.round(input.hue() * (BOX_W - 1));
        g.fill(at - 1, hy - 1, at + 2, hy + 11, 0xFF000000);
        g.fill(at, hy - 1, at + 1, hy + 11, TEXT);
    }

    /** The colour one channel's slider would have if it were at a share of its length, the others as they stand. */
    private static int withChannel(Input input, int channel, float share){
        int rgb = input.rgb();
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int value = Math.round(share * 255);
        return switch (channel) {
            case 0 -> (value << 16) | (g << 8) | b;
            case 1 -> (r << 16) | (value << 8) | b;
            case 2 -> (r << 16) | (g << 8) | value;
            case 3 -> ColorMath.hsvToRgb(share, input.saturation(), input.brightness());
            case 4 -> ColorMath.hsvToRgb(input.hue(), share, input.brightness());
            default -> ColorMath.hsvToRgb(input.hue(), input.saturation(), share);
        };
    }

    private static float channelShare(Input input, int channel){
        int rgb = input.rgb();
        return switch (channel) {
            case 0 -> ((rgb >> 16) & 0xFF) / 255.0f;
            case 1 -> ((rgb >> 8) & 0xFF) / 255.0f;
            case 2 -> (rgb & 0xFF) / 255.0f;
            case 3 -> input.hue();
            case 4 -> input.saturation();
            default -> input.brightness();
        };
    }

    private static String channelText(Input input, int channel){
        float share = channelShare(input, channel);
        return String.valueOf(channel < 3 ? Math.round(share * 255) : channel == 3 ? Math.round(share * 360) : Math.round(share * 100));
    }

    private static void paintBars(Input input, Font font, GuiGraphicsExtractor g, int bx, int by){
        for (int c = 0; c < 6; c++) {
            int ry = by + 3 + c * BAR_STEP;
            g.text(font, CHANNELS[c], bx + 4, ry + 2, TEXT, false);
            for (int i = 0; i < BAR_W; i++) {
                g.fill(bx + BAR_X + i, ry + 1, bx + BAR_X + i + 1, ry + 11,
                        0xFF000000 | withChannel(input, c, i / (float) (BAR_W - 1)));
            }
            int at = bx + BAR_X + Math.round(channelShare(input, c) * (BAR_W - 1));
            g.fill(at - 1, ry, at + 2, ry + 12, 0xFF000000);
            g.fill(at, ry, at + 1, ry + 12, TEXT);
            g.text(font, channelText(input, c), bx + BAR_X + BAR_W + 4, ry + 2, TEXT, false);
        }
    }

    /** The swatch at a row and column: a row of greys, then six rows of the hues going from pale to dark. */
    private static int swatch(int row, int col){
        if (row == 0) return ColorMath.hsvToRgb(0, 0, col / 11.0f);
        float[] saturation = { 0.25f, 0.5f, 0.75f, 1.0f, 1.0f, 1.0f };
        float[] brightness = { 1.0f, 1.0f, 1.0f, 1.0f, 0.75f, 0.5f };
        return ColorMath.hsvToRgb(col / 12.0f, saturation[row - 1], brightness[row - 1]);
    }

    private static void paintSwatches(Input input, GuiGraphicsExtractor g, int x, int y){
        int current = input.rgb();
        for (int row = 0; row < 7; row++) {
            for (int col = 0; col < 12; col++) {
                int rgb = swatch(row, col);
                g.fill(x + col * CELL_W, y + row * CELL_H, x + (col + 1) * CELL_W - 1, y + (row + 1) * CELL_H - 1, 0xFF000000 | rgb);
                if (rgb == current) outline(g, x + col * CELL_W - 1, y + row * CELL_H - 1, CELL_W + 1, CELL_H + 1, TEXT);
            }
        }
    }

    /** Applies whatever a picked point means to the colour and tells the callback if the colour moved. */
    private static void apply(DOMContext ctx, DOMTreeElement element, boolean moved){
        if (moved) CTMLControls.fire(ctx, element, 0);
    }

    /**
     * What a press at this spot starts following while the button is held, or null where nothing in the
     * picker can be dragged there. The first value is taken by the press itself, so a click and the start of
     * a drag agree.
     */
    static CTMLControls.Hold holdColor(Input input, DOMContext ctx, DOMTreeElement element, int[] a, double px, double py){
        int x = a[0] + 1;
        int y = a[1] + 1 + BODY_Y;
        if (py < y || py >= y + BODY_H) return null;

        switch (input.mode()) {
            case 0 -> {
                int cx = x + 69;
                int cy = y + 45;
                double dx = px - cx;
                double dy = py - cy;
                double d = Math.sqrt(dx * dx + dy * dy);
                if (Math.abs(dx) <= WHEEL_HALF && Math.abs(dy) <= WHEEL_HALF) {
                    return (fx, fy) -> apply(ctx, element, input.setHsv(input.hue(),
                            (float) ((fx - (cx - WHEEL_HALF)) / (WHEEL_HALF * 2)),
                            (float) (1 - (fy - (cy - WHEEL_HALF)) / (WHEEL_HALF * 2))));
                }
                if (d >= WHEEL_IN - 2 && d <= WHEEL_OUT + 2) {
                    return (fx, fy) -> apply(ctx, element, input.setHsv(angle(fx - cx, fy - cy),
                            input.saturation(), input.brightness()));
                }
            }
            case 1 -> {
                int bx = x + 9;
                if (px >= bx && px < bx + BOX_W && py >= y + 2 && py < y + 2 + BOX_H) {
                    int by = y + 2;
                    return (fx, fy) -> apply(ctx, element, input.setHsv(input.hue(),
                            (float) ((fx - bx) / (BOX_W - 1)), (float) (1 - (fy - by) / (BOX_H - 1))));
                }
                if (px >= bx && px < bx + BOX_W && py >= y + 76 && py < y + 86) {
                    return (fx, fy) -> apply(ctx, element, input.setHsv((float) ((fx - bx) / (BOX_W - 1)),
                            input.saturation(), input.brightness()));
                }
            }
            case 2 -> {
                int c = (int) ((py - y - 3) / BAR_STEP);
                int bx = x + BAR_X;
                if (c >= 0 && c < 6 && px >= bx - 2 && px < bx + BAR_W + 2) {
                    return (fx, fy) -> apply(ctx, element, setChannel(input, c, (float) ((fx - bx) / (BAR_W - 1))));
                }
            }
            default -> {}
        }
        return null;
    }

    private static boolean setChannel(Input input, int channel, float share){
        float s = Math.clamp(share, 0.0f, 1.0f);
        if (channel < 3) return input.setRgb(withChannel(input, channel, s));
        return switch (channel) {
            case 3 -> input.setHsv(s, input.saturation(), input.brightness());
            case 4 -> input.setHsv(input.hue(), s, input.brightness());
            default -> input.setHsv(input.hue(), input.saturation(), s);
        };
    }

    /** A press anywhere in the picker. Says whether it was inside, which is whether it was taken. */
    static boolean pressColor(Input input, DOMContext ctx, DOMTreeElement element, int[] a, double px, double py){
        if (!inside(a, px, py)) return false;
        int x = a[0] + 1;
        int y = a[1] + 1;

        if (py < y + TAB_H) {
            input.setMode((int) ((px - x) / TAB_W));
            return true;
        }
        CTMLControls.Hold hold = holdColor(input, ctx, element, a, px, py);
        if (hold != null) {
            hold.follow(px, py);
            return true;
        }
        if (input.mode() == 3) {
            int col = (int) ((px - (x + 3)) / CELL_W);
            int row = (int) ((py - (y + BODY_Y + 3)) / CELL_H);
            if (col >= 0 && col < 12 && row >= 0 && row < 7) apply(ctx, element, input.setRgb(swatch(row, col)));
        }
        return true;
    }

    /** The keys of a colour: they switch the picker's tabs and type its hex while it is open. */
    static boolean colorKey(DOMContext ctx, DOMTreeElement focus, Input input, int key){
        boolean confirm = key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER;
        if (!input.isOpen()) {
            if (confirm || key == InputConstants.KEY_SPACE) {
                input.openPicker();
                return true;
            }
            return false;
        }

        switch (key) {
            case InputConstants.KEY_LEFT, InputConstants.KEY_PAGEUP -> input.setMode(input.mode() - 1);
            case InputConstants.KEY_RIGHT, InputConstants.KEY_PAGEDOWN -> input.setMode(input.mode() + 1);
            case InputConstants.KEY_BACKSPACE -> input.backspaceHex();
            case InputConstants.KEY_ESCAPE -> input.close();
            default -> {
                if (!confirm && key != InputConstants.KEY_SPACE) return false;
                if (input.typedHex() != null) apply(ctx, focus, input.applyHex());
                else input.close();
            }
        }
        return true;
    }
}
