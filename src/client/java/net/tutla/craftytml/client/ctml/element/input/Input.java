package net.tutla.craftytml.client.ctml.element.input;

import net.tutla.craftytml.client.ctml.CTMLDiagnostics;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.property.Checked;
import net.tutla.craftytml.client.ctml.property.Max;
import net.tutla.craftytml.client.ctml.property.Min;
import net.tutla.craftytml.client.ctml.property.Size;
import net.tutla.craftytml.client.ctml.property.Step;
import net.tutla.craftytml.client.ctml.property.TabIndex;
import net.tutla.craftytml.client.ctml.property.Type;
import net.tutla.craftytml.client.ctml.style.Length;
import net.tutla.craftytml.client.tusan.CTMLTokenType;
import net.tutla.tums.tusan.TusanContext;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;

/**
 * Something to fill in, which is whatever the type property says it is: a line of text (text,
 * password, email, search, url or tel), a number, a checkbox, a radio button, a range slider, a button (button, submit
 * or reset, none of which does more than a button does), a date, a colour or nothing at all (hidden).
 *
 * <p>A date holds yyyy-mm-dd and shows it in a box; a press opens a calendar under it to pick a day from,
 * with arrows for the month and the year and buttons for today and for clearing it. The keyboard walks the
 * calendar too: arrows for days and weeks, page up and down for months, enter to pick. A colour holds
 * #rrggbb and shows a swatch; a press opens a picker with four ways to choose: a hue wheel with a
 * saturation and brightness square inside it, a saturation and brightness box with a hue bar, red, green,
 * blue, hue, saturation and brightness sliders, and a grid of swatches. Hex can be typed in while it is open.
 * Both fire an INPUT for each change, a drag in the picker included. min, max and step do not apply to either. A type it does not know is read as text.
 *
 * <p>What it holds is kept in its own properties, rewritten as the user changes it: value for the text,
 * the number or the position of the slider, and checked for a checkbox or a radio button. A function
 * the callback property names is called with a {@link net.tutla.craftytml.client.dom.CTMLCallback} of
 * type INPUT each time the user changes one, and reads the new state off the element it is handed.
 * A press on a button input calls it the same way.
 *
 * <p>The text types are one line and scroll sideways to keep the caret in sight. They take typing,
 * pasting, backspace, delete, the arrow keys, home and end, and selecting, copying, cutting and undoing:
 * shift with the arrows, home and end, or a drag, or ctrl and A, selects; two quick presses select a word and
 * three the whole line; ctrl with C, X and V copies, cuts and pastes (a password gives nothing away); ctrl with
 * Z undoes, and ctrl with Y or shift Z redoes. Typing and pasting replace what is selected.
 * A number takes only digits, a minus sign and a point, and the up and down keys step it. Ticking a
 * radio button unticks the others with the same name.
 *
 * <p>isValid says whether what it holds is acceptable: filled in where required, long enough, short
 * enough, matching its pattern, and a number, email or url that reads as one. Nothing is refused on
 * account of it, there being no forms. The attributes that only a form, a file picker or a keyboard on
 * screen would read are accepted and kept but do nothing.
 *
 * <p>They all start out as a small dark box and are dressed with classes like any other element. All
 * of them are in the tab order unless disabled, except hidden, which takes up no room at all.
 *
 * @ctml.group input
 * @ctml.properties type, value, placeholder, checked, name, min, max, step, maxlength, minlength, size, pattern, required, autofocus, disabled, readonly, callback, tabindex
 * @ctml.example <input type="text" class="w-32" placeholder="your name" callback="nameChanged">
 */
public class Input extends FormControl {
    public enum Kind {
        TEXT, PASSWORD, EMAIL, SEARCH, URL, TEL, NUMBER, CHECKBOX, RADIO, RANGE, HIDDEN, BUTTON, SUBMIT, RESET, DATE, COLOR
    }

    private static final int MARK_SIZE = 10;
    private static final int RANGE_LENGTH = 100;

    private static final ElementStyle TEXT_DEFAULTS = field()
            .set(ElementStyle.WIDTH, Length.px(120))
            .set(ElementStyle.OVERFLOW_X, ElementStyle.Overflow.HIDDEN)
            .set(ElementStyle.WHITE_SPACE, ElementStyle.WhiteSpace.PRE) // spaces typed are spaces kept
            .set(ElementStyle.TEXT_ALIGN, ElementStyle.TextAlign.LEFT)
            .build();

    private static final ElementStyle BUTTON_DEFAULTS = field()
            .set(ElementStyle.BACKGROUND_COLOR, 0xFF374151)
            .set(ElementStyle.PADDING_LEFT, Length.px(6))
            .set(ElementStyle.PADDING_RIGHT, Length.px(6))
            .set(ElementStyle.TEXT_ALIGN, ElementStyle.TextAlign.CENTER)
            .set(ElementStyle.WHITE_SPACE, ElementStyle.WhiteSpace.PRE)
            .build();

    private static final ElementStyle CHECKBOX_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.DISPLAY, ElementStyle.Display.INLINE_BLOCK)
            .set(ElementStyle.WIDTH, Length.px(MARK_SIZE))
            .set(ElementStyle.HEIGHT, Length.px(MARK_SIZE))
            .set(ElementStyle.BACKGROUND_COLOR, FIELD_BACKGROUND)
            .set(ElementStyle.BORDER_WIDTH, 1)
            .set(ElementStyle.BORDER_COLOR, FIELD_BORDER)
            .build();

    private static final ElementStyle RADIO_DEFAULTS = CHECKBOX_DEFAULTS.toBuilder()
            .set(ElementStyle.BORDER_RADIUS, MARK_SIZE / 2)
            .build();

    private static final ElementStyle RANGE_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.DISPLAY, ElementStyle.Display.INLINE_BLOCK)
            .set(ElementStyle.WIDTH, Length.px(RANGE_LENGTH))
            .set(ElementStyle.HEIGHT, Length.px(MARK_SIZE))
            .build();

    /** A box that shows the date and keeps room on the right for the calendar mark. */
    private static final ElementStyle DATE_DEFAULTS = field()
            .set(ElementStyle.WIDTH, Length.px(92))
            .set(ElementStyle.PADDING_RIGHT, Length.px(14))
            .set(ElementStyle.OVERFLOW_X, ElementStyle.Overflow.HIDDEN)
            .set(ElementStyle.WHITE_SPACE, ElementStyle.WhiteSpace.PRE)
            .set(ElementStyle.TEXT_ALIGN, ElementStyle.TextAlign.LEFT)
            .build();

    /** Just a swatch of the colour, with nothing inside to pad. */
    private static final ElementStyle COLOR_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.DISPLAY, ElementStyle.Display.INLINE_BLOCK)
            .set(ElementStyle.WIDTH, Length.px(28))
            .set(ElementStyle.HEIGHT, Length.px(14))
            .set(ElementStyle.BORDER_WIDTH, 1)
            .set(ElementStyle.BORDER_COLOR, FIELD_BORDER)
            .build();

    private static final ElementStyle HIDDEN_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.DISPLAY, ElementStyle.Display.NONE)
            .build();

    public Input(){
        super("input", false, CTMLTokenType.INPUT, CTMLTokenType.INPUT_CLOSING);
    }

    /** What the type property names, or text for one it does not name or does not know. */
    public Kind kind(){
        String written = propertyList.propertyFromName("type") instanceof Type type ? type.getKind() : "text";
        try {
            return Kind.valueOf(written.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Kind.TEXT;
        }
    }

    @Override
    public void updateProperty(String name, Object value, TusanContext ctx){
        super.updateProperty(name, value, ctx);
        if (name.equals("type") && propertyList.propertyFromName("type") instanceof Type type) {
            String written = type.getKind();
            boolean known = false;
            for (Kind each : Kind.values()) known |= each.name().equalsIgnoreCase(written);
            if (!known) {
                CTMLDiagnostics.warn("UnknownInputType", "<input> has the type \"" + written
                        + "\", which is not one it has: it is read as text", ctx);
            }
        }
    }

    @Override
    public ElementStyle defaultStyle(){
        return switch (kind()) {
            case CHECKBOX -> CHECKBOX_DEFAULTS;
            case RADIO -> RADIO_DEFAULTS;
            case RANGE -> RANGE_DEFAULTS;
            case HIDDEN -> HIDDEN_DEFAULTS;
            case BUTTON, SUBMIT, RESET -> BUTTON_DEFAULTS;
            case DATE -> DATE_DEFAULTS;
            case COLOR -> COLOR_DEFAULTS;
            default -> sized();
        };
    }

    /** A text field, as wide as its size says where it has one: a character is about six pixels across. */
    private ElementStyle sized(){
        int chars = propertyList.propertyFromName("size") instanceof Size size ? size.getChars() : 0;
        if (chars <= 0) return TEXT_DEFAULTS;
        return TEXT_DEFAULTS.toBuilder().set(ElementStyle.WIDTH, Length.px(chars * 6 + 8)).build();
    }

    @Override
    public boolean isTextField(){
        return switch (kind()) {
            case TEXT, PASSWORD, EMAIL, SEARCH, URL, TEL, NUMBER -> true;
            default -> false;
        };
    }

    /** A button input is pressed rather than filled in, so a press on it is an input. */
    @Override
    public boolean firesInput(){
        return isPressed() && !isDisabled();
    }

    @Override
    public int defaultTabIndex(){
        return kind() == Kind.HIDDEN ? TabIndex.UNSET : super.defaultTabIndex();
    }

    @Override
    protected boolean accepts(int codePoint){
        if (kind() != Kind.NUMBER) return super.accepts(codePoint);
        return (codePoint >= '0' && codePoint <= '9') || codePoint == '-' || codePoint == '.';
    }

    @Override
    public boolean showsPlaceholder(){
        if (kind() == Kind.DATE) return value().isEmpty();
        return isTextField() && value().isEmpty() && !placeholder().isEmpty();
    }

    /**
     * What the box has to say: the text, or the dimmed words where there is none, or stars for a
     * password. A box with nothing in it would be no taller than its padding, so an empty one says a
     * space, which keeps the height of a line.
     */
    @Override
    public String shownText(){
        Kind kind = kind();
        if (isPressed()) return value();
        if (kind == Kind.DATE) return value().isEmpty() ? (placeholder().isEmpty() ? "yyyy-mm-dd" : placeholder()) : value();
        if (!isTextField()) return null;

        String held = value();
        if (held.isEmpty()) return showsPlaceholder() ? placeholder() : " ";
        return kind == Kind.PASSWORD ? "*".repeat(held.codePointCount(0, held.length())) : held;
    }

    /** Whether it is something to press, which is a button and the two that would send or clear a form. */
    public boolean isPressed(){
        Kind kind = kind();
        return kind == Kind.BUTTON || kind == Kind.SUBMIT || kind == Kind.RESET;
    }

    @Override
    public boolean isValid(){
        if (isDisabled()) return true;
        Kind kind = kind();
        if (isRequired() && (kind == Kind.CHECKBOX || kind == Kind.RADIO)) return isChecked();

        String held = value();
        if (!super.isValid()) return false;
        if (held.isEmpty()) return true;

        try {
            switch (kind) {
                case NUMBER -> {
                    float number = Float.parseFloat(held.strip());
                    if (bounded("min") && number < lowest()) return false;
                    if (bounded("max") && number > highest()) return false;
                }
                case EMAIL -> {
                    int at = held.indexOf('@');
                    if (at < 1 || at == held.length() - 1 || held.indexOf('@', at + 1) >= 0) return false;
                }
                case URL -> {
                    if (!held.matches("[A-Za-z][A-Za-z0-9+.-]*:.+")) return false;
                }
                case DATE -> {
                    if (date() == null) return false;
                }
                case COLOR -> {
                    if (ColorMath.parse(held) < 0) return false;
                }
                default -> {}
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    // ---- date ----

    private YearMonth viewing;
    private LocalDate cursor;

    /** The date it holds, or null where it holds none or one that is not written yyyy-mm-dd. */
    public LocalDate date(){
        try {
            return LocalDate.parse(value().strip());
        } catch (DateTimeException e) {
            return null;
        }
    }

    /** Puts a date in, or empties it for null, and says whether that was a change. */
    public boolean setDate(LocalDate date){
        return setValue(date == null ? "" : date.toString());
    }

    /** The month the calendar is showing while it is open. */
    public YearMonth viewing(){
        return viewing == null ? YearMonth.now() : viewing;
    }

    public void view(YearMonth month){
        viewing = month;
    }

    /** The day the keyboard is on while the calendar is open. */
    public LocalDate cursor(){
        return cursor == null ? LocalDate.now() : cursor;
    }

    /** Moves the keyboard to a day, and the calendar to the month it is in. */
    public void moveCursor(LocalDate day){
        cursor = day;
        viewing = YearMonth.from(day);
    }

    // ---- colour ----

    private float hue;
    private float saturation;
    private float brightness;
    private int mode; // which way of picking the open picker is showing
    private String typedHex; // what has been typed into the picker's hex field, or null while nothing has

    /** The colour it holds as 0xRRGGBB; black where it holds none, which is what a colour input starts as. */
    public int rgb(){
        int read = ColorMath.parse(value());
        return read < 0 ? 0 : read;
    }

    /** Puts a colour in and says whether that was a change. */
    public boolean setRgb(int rgb){
        boolean moved = setValue(ColorMath.hex(rgb));
        syncHsv();
        typedHex = null;
        return moved;
    }

    /** Works the hue, saturation and brightness back out of the colour, keeping the hue of a grey, which has none. */
    private void syncHsv(){
        float[] hsv = ColorMath.rgbToHsv(rgb());
        if (hsv[1] > 0.0f && hsv[2] > 0.0f) hue = hsv[0];
        saturation = hsv[1];
        brightness = hsv[2];
    }

    public float hue(){ return hue; }
    public float saturation(){ return saturation; }
    public float brightness(){ return brightness; }

    /** Picks by hue, saturation and brightness, each a share from nothing to one, and says whether the colour changed. */
    public boolean setHsv(float hue, float saturation, float brightness){
        this.hue = Math.clamp(hue, 0.0f, 1.0f);
        this.saturation = Math.clamp(saturation, 0.0f, 1.0f);
        this.brightness = Math.clamp(brightness, 0.0f, 1.0f);
        typedHex = null;
        return setValue(ColorMath.hex(ColorMath.hsvToRgb(this.hue, this.saturation, this.brightness)));
    }

    public int mode(){ return mode; }

    public void setMode(int mode){
        this.mode = Math.floorMod(mode, 4);
    }

    /** What has been typed into the hex field since it was last applied, or null. */
    public String typedHex(){
        return typedHex;
    }

    /** Types a hex digit into the picker's field. Says whether it took it. */
    public boolean typeHex(int codePoint){
        if (!Character.isLetterOrDigit(codePoint) && codePoint != '#') return false;
        if (codePoint == '#') return true;
        if (Character.digit(codePoint, 16) < 0) return false;
        String held = typedHex == null ? "" : typedHex;
        if (held.length() >= 6) return true;
        typedHex = held + Character.toLowerCase((char) codePoint);
        return true;
    }

    public void backspaceHex(){
        if (typedHex == null) return;
        typedHex = typedHex.length() <= 1 ? null : typedHex.substring(0, typedHex.length() - 1);
    }

    /** Takes what was typed as the colour, if it reads as one. Says whether the colour changed. */
    public boolean applyHex(){
        int read = ColorMath.parse(typedHex);
        typedHex = null;
        return read >= 0 && setRgb(read);
    }

    // ---- the popups ----

    /** Opens the calendar or the colour picker, starting from what it holds. */
    public void openPicker(){
        if (!isEditable()) return;
        LocalDate day = date();
        cursor = day != null ? day : LocalDate.now();
        viewing = YearMonth.from(cursor);
        syncHsv();
        typedHex = null;
        raise();
    }

    // ---- checkbox and radio ----

    public boolean isChecked(){
        return propertyList.propertyFromName("checked") instanceof Checked checked && checked.isOn();
    }

    /** Ticks or unticks it, and says whether that was a change. */
    public boolean setChecked(boolean on){
        if (on == isChecked()) return false;
        updateProperty("checked", on, null);
        return true;
    }

    // ---- number and range ----

    public int lowest(){
        return propertyList.propertyFromName("min") instanceof Min min ? min.getLimit(0) : 0;
    }

    public int highest(){
        return propertyList.propertyFromName("max") instanceof Max max ? max.getLimit(RANGE_LENGTH) : RANGE_LENGTH;
    }

    public int amount(){
        return propertyList.propertyFromName("step") instanceof Step step ? step.getAmount() : 1;
    }

    /** Whether a number was given a lowest or a highest of its own, since it has neither otherwise. */
    private boolean bounded(String name){
        return propertyList.propertyFromName(name) != null && propertyList.propertyFromName(name).hasValue();
    }

    /** The value of a range as a whole number inside its limits, which is half way where it holds none. */
    public int position(){
        int low = lowest();
        int high = Math.max(low, highest());
        try {
            return Math.clamp(Math.round(Float.parseFloat(value().strip())), low, high);
        } catch (NumberFormatException e) {
            return low + (high - low) / 2;
        }
    }

    /** How far along its track a range is, from nothing to all of it. */
    public float share(){
        int low = lowest();
        int high = Math.max(low, highest());
        return high == low ? 0.0f : (position() - low) / (float) (high - low);
    }

    /** Puts a range on a share of its track, landing on the nearest step. Says whether it moved. */
    public boolean slideTo(float share){
        int low = lowest();
        int high = Math.max(low, highest());
        int step = amount();
        int raw = low + Math.round(Math.clamp(share, 0.0f, 1.0f) * (high - low));
        int snapped = low + Math.round((raw - low) / (float) step) * step;
        return setValue(String.valueOf(Math.clamp(snapped, low, high)));
    }

    /** Moves a range or a number by whole steps, up for positive, and says whether it moved. */
    public boolean nudge(int steps){
        if (!isEditable()) return false;
        Kind kind = kind();
        if (kind != Kind.RANGE && kind != Kind.NUMBER) return false;

        int now;
        if (kind == Kind.RANGE) {
            now = position();
        } else {
            try {
                now = Math.round(Float.parseFloat(value().strip()));
            } catch (NumberFormatException e) {
                now = bounded("min") ? lowest() : 0;
            }
        }

        long next = (long) now + (long) steps * amount();
        if (kind == Kind.RANGE || bounded("min")) next = Math.max(next, lowest());
        if (kind == Kind.RANGE || bounded("max")) next = Math.min(next, Math.max(lowest(), highest()));
        boolean moved = setValue(String.valueOf(next));
        if (moved) setCaret(value().length());
        return moved;
    }
}
