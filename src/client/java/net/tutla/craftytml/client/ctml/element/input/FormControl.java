package net.tutla.craftytml.client.ctml.element.input;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.property.Disabled;
import net.tutla.craftytml.client.ctml.property.MinLength;
import net.tutla.craftytml.client.ctml.property.Pattern;
import net.tutla.craftytml.client.ctml.property.Required;
import net.tutla.craftytml.client.ctml.property.MaxLength;
import net.tutla.craftytml.client.ctml.property.Placeholder;
import net.tutla.craftytml.client.ctml.property.ReadOnly;
import net.tutla.craftytml.client.ctml.property.TabIndex;
import net.tutla.craftytml.client.ctml.property.Value;
import net.tutla.craftytml.client.ctml.style.Length;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * What the things a user fills in have in common: a value that lives in the value property, so reading
 * the property back always gives what is on screen, a caret for the ones that take text, and the two
 * switches that take the user out of it, disabled and readonly.
 *
 * <p>This class only changes state. The keyboard and the mouse are heard by {@code CTMLControls}, which
 * calls into it and fires the callback when something here reports that it changed.
 */
public abstract class FormControl extends CTMLElement {
    /** The look every control starts from before a class list touches it: a dark box with a thin frame. */
    protected static final int FIELD_BACKGROUND = 0xFF111827;
    protected static final int FIELD_BORDER = 0xFF6B7280;

    private int caret;
    private int anchor = -1; // the other end of the selection, or below zero where nothing is selected
    private boolean reveal;

    /** What the text looked like at one moment, which is what undo and redo put back. */
    private record Snapshot(String value, int caret) {}

    private static final int HISTORY = 100;
    private final java.util.ArrayDeque<Snapshot> undone = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<Snapshot> redone = new java.util.ArrayDeque<>();
    private int typedAt = -1; // where the last plain character went in, so a run of typing is one undo

    private long lastClick;
    private int clicks;

    protected FormControl(String tag, boolean hasChildren, CTMLTokenType open, CTMLTokenType close){
        super(tag, CTMLElementProperties.newElementProperties(), hasChildren, open, close);
    }

    /** The box every field is built on, which the controls then size to what they are. */
    protected static ElementStyle.Builder field(){
        return DEFAULTS.toBuilder()
                .set(ElementStyle.DISPLAY, ElementStyle.Display.INLINE_BLOCK)
                .set(ElementStyle.BACKGROUND_COLOR, FIELD_BACKGROUND)
                .set(ElementStyle.BORDER_WIDTH, 1)
                .set(ElementStyle.BORDER_COLOR, FIELD_BORDER)
                .set(ElementStyle.PADDING_TOP, Length.px(2))
                .set(ElementStyle.PADDING_BOTTOM, Length.px(2))
                .set(ElementStyle.PADDING_LEFT, Length.px(3))
                .set(ElementStyle.PADDING_RIGHT, Length.px(3));
    }

    // ---- state ----

    public boolean isDisabled(){
        return propertyList.propertyFromName("disabled") instanceof Disabled disabled && disabled.isOn();
    }

    public boolean isReadOnly(){
        return propertyList.propertyFromName("readonly") instanceof ReadOnly readonly && readonly.isOn();
    }

    /** Whether the user can change it at all, which a disabled or a read only control cannot be. */
    public boolean isEditable(){
        return !isDisabled() && !isReadOnly();
    }

    /** What it holds, as it stands: the value property, or nothing where none was written. */
    public String value(){
        return propertyList.propertyFromName("value") instanceof Value value ? value.getText() : "";
    }

    /** Puts a new value in the property and says whether it was a change. */
    public boolean setValue(String text){
        String next = text == null ? "" : text;
        if (next.equals(value())) return false;
        updateProperty("value", next, null);
        caret = Math.min(caret, next.length());
        anchor = -1;
        return true;
    }

    /** Where the words in front of the caret end; the value's length where nothing has moved it. */
    public int caret(){
        return Math.clamp(caret, 0, value().length());
    }

    /** Puts the caret there and drops any selection. */
    public void setCaret(int index){
        moveTo(index, false);
    }

    /**
     * Puts the caret there. Where extend is set the selection stretches from where the caret was, or from
     * the end it already had, to the new place; otherwise any selection is dropped.
     */
    public void moveTo(int index, boolean extend){
        int next = Math.clamp(index, 0, value().length());
        int was = anchor;
        if (extend) {
            if (anchor < 0) anchor = caret();
        } else {
            anchor = -1;
        }
        if (next == caret && was == anchor) return;
        caret = next;
        reveal = true;
        changed();
    }

    // ---- selection ----

    public boolean hasSelection(){
        return anchor >= 0 && anchor != caret();
    }

    /** Where the selection starts, or the caret where nothing is selected. */
    public int selectionStart(){
        return hasSelection() ? Math.min(anchor, caret()) : caret();
    }

    public int selectionEnd(){
        return hasSelection() ? Math.max(anchor, caret()) : caret();
    }

    public String selectedText(){
        return hasSelection() ? value().substring(selectionStart(), selectionEnd()) : "";
    }

    /** Selects from one index to another, with the caret at the second. */
    public void select(int from, int to){
        int length = value().length();
        anchor = Math.clamp(from, 0, length);
        caret = Math.clamp(to, 0, length);
        reveal = true;
        changed();
    }

    public void selectAll(){
        select(0, value().length());
    }

    /** Counts a press: one, then two and three where it follows the last closely, which is what selects a word and a line. */
    public int click(long now){
        clicks = now - lastClick < 500 ? Math.min(clicks + 1, 3) : 1;
        lastClick = now;
        return clicks;
    }

    /** Selects the word at an index, or the run of spaces if that is what it is on. */
    public void selectWordAt(int index){
        String held = value();
        int at = Math.clamp(index, 0, held.length());
        if (held.isEmpty()) return;
        boolean space = Character.isWhitespace(held.charAt(Math.min(at, held.length() - 1)));
        int from = at;
        int to = at;
        while (from > 0 && Character.isWhitespace(held.charAt(from - 1)) == space) from--;
        while (to < held.length() && Character.isWhitespace(held.charAt(to)) == space) to++;
        select(from, to);
    }

    /** Selects the whole line at an index, which for a single line is everything. */
    public void selectLineAt(int index){
        String held = value();
        int at = Math.clamp(index, 0, held.length());
        int from = held.lastIndexOf('\n', at - 1) + 1;
        int to = held.indexOf('\n', at);
        select(from, to < 0 ? held.length() : to);
    }

    /** Whether the caret moved since it was last asked, which is when a field scrolls to keep it in sight. */
    public boolean takeReveal(){
        boolean was = reveal;
        reveal = false;
        return was;
    }

    // ---- validity ----

    public boolean isRequired(){
        return propertyList.propertyFromName("required") instanceof Required required && required.isOn();
    }

    /**
     * Whether what it holds is acceptable: filled in where required, long enough, short enough and
     * matching the pattern. Nothing refuses to submit, there being no forms, and nothing stops the user
     * typing; this is for a host that wants to ask. A control out of use is always valid.
     */
    public boolean isValid(){
        if (isDisabled()) return true;
        String held = value();
        if (isRequired() && held.isEmpty()) return false;
        if (held.isEmpty() || !isTextField()) return true;

        if (propertyList.propertyFromName("minlength") instanceof MinLength min && held.length() < min.getLimit()) return false;
        if (propertyList.propertyFromName("maxlength") instanceof MaxLength max && held.length() > max.getLimit()) return false;
        if (propertyList.propertyFromName("pattern") instanceof Pattern pattern && pattern.hasValue()) {
            try {
                if (!java.util.regex.Pattern.matches(pattern.getStringValue(), held)) return false;
            } catch (java.util.regex.PatternSyntaxException e) {
                // a pattern that does not compile says nothing, the way a browser ignores one
            }
        }
        return true;
    }

    // ---- what it shows ----

    /** Whether it takes text at all. */
    public boolean isTextField(){
        return false;
    }

    /** Whether a new line is a thing it takes. */
    public boolean isMultiline(){
        return false;
    }

    /** Whether the dimmed words are what it is showing right now, because it holds nothing. */
    public boolean showsPlaceholder(){
        return false;
    }

    /** The placeholder as written, or an empty string. */
    protected String placeholder(){
        return propertyList.propertyFromName("placeholder") instanceof Placeholder text && text.hasValue()
                ? resolve(text.getStringValue())
                : "";
    }

    /** Where it sits in the tab order: after the markup, unless it has been taken out of use. */
    @Override
    public int defaultTabIndex(){
        return isDisabled() ? TabIndex.UNSET : 0;
    }

    // ---- editing ----

    /** Whether a typed character is one this control takes. */
    protected boolean accepts(int codePoint){
        return codePoint >= 32 && codePoint != 127;
    }

    /** Writes a new value and caret as one edit that undo can take back. */
    private void edit(String next, int at, boolean typing){
        Snapshot before = new Snapshot(value(), caret());
        // a run of typed characters, each going in where the last one ended, is a single step back
        if (!(typing && typedAt == before.caret() && !undone.isEmpty())) {
            undone.push(before);
            while (undone.size() > HISTORY) undone.removeLast();
        }
        redone.clear();
        updateProperty("value", next, null);
        caret = Math.clamp(at, 0, next.length());
        anchor = -1;
        typedAt = typing ? caret : -1;
        reveal = true;
    }

    /** Takes out what is selected, as an edit of its own. Says whether there was anything. */
    public boolean deleteSelection(){
        if (!isTextField() || !isEditable() || !hasSelection()) return false;
        String held = value();
        int from = selectionStart();
        edit(held.substring(0, from) + held.substring(selectionEnd()), from, false);
        return true;
    }

    /** Types at the caret over whatever is selected, dropping what the control does not take. Says whether anything changed. */
    public boolean insert(String text){
        if (!isTextField() || !isEditable() || text == null || text.isEmpty()) return false;

        StringBuilder taken = new StringBuilder();
        text.codePoints().forEach(each -> {
            if (each == '\r') return;
            if (each == '\n' ? isMultiline() : accepts(each)) taken.appendCodePoint(each);
        });
        if (taken.isEmpty()) return false;

        String held = value();
        int from = selectionStart();
        int to = selectionEnd();
        int room = (propertyList.propertyFromName("maxlength") instanceof MaxLength max ? max.getLimit() : Integer.MAX_VALUE)
                - (held.length() - (to - from));
        if (room <= 0) return false;
        String added = taken.length() > room ? taken.substring(0, room) : taken.toString();

        edit(held.substring(0, from) + added + held.substring(to), from + added.length(),
                added.length() == 1 && from == to);
        return true;
    }

    /** Takes out the selection, or the character in front of the caret. */
    public boolean backspace(){
        if (hasSelection()) return deleteSelection();
        int at = caret();
        if (!isTextField() || !isEditable() || at == 0) return false;
        String held = value();
        int from = at - Character.charCount(held.codePointBefore(at));
        edit(held.substring(0, from) + held.substring(at), from, false);
        return true;
    }

    /** Takes out the selection, or the character after the caret. */
    public boolean delete(){
        if (hasSelection()) return deleteSelection();
        String held = value();
        int at = caret();
        if (!isTextField() || !isEditable() || at >= held.length()) return false;
        edit(held.substring(0, at) + held.substring(at + Character.charCount(held.codePointAt(at))), at, false);
        return true;
    }

    /** Takes out the selection, or the word in front of the caret or after it. Says whether anything went. */
    public boolean deleteWord(int direction){
        if (hasSelection()) return deleteSelection();
        if (!isTextField() || !isEditable()) return false;
        String held = value();
        int at = caret();
        int edge = wordEdge(direction);
        if (edge == at) return false;
        int from = Math.min(at, edge);
        edit(held.substring(0, from) + held.substring(Math.max(at, edge)), from, false);
        return true;
    }

    /** Puts back the last edit, and says whether there was one. */
    public boolean undo(){
        if (!isEditable() || undone.isEmpty()) return false;
        redone.push(new Snapshot(value(), caret()));
        return restore(undone.pop());
    }

    public boolean redo(){
        if (!isEditable() || redone.isEmpty()) return false;
        undone.push(new Snapshot(value(), caret()));
        return restore(redone.pop());
    }

    private boolean restore(Snapshot snapshot){
        updateProperty("value", snapshot.value(), null);
        caret = Math.clamp(snapshot.caret(), 0, snapshot.value().length());
        anchor = -1;
        typedAt = -1;
        reveal = true;
        return true;
    }

    // ---- moving ----

    /**
     * Moves the caret one character along, or back, or a word. Without extend a selection is dropped,
     * and a plain step with one open lands on the edge it was heading for, the way an editor does.
     */
    public void step(int direction, boolean word, boolean extend){
        if (!extend && !word && hasSelection()) {
            moveTo(direction < 0 ? selectionStart() : selectionEnd(), false);
            return;
        }
        String held = value();
        int at = caret();
        int target = at;
        if (word) target = wordEdge(direction);
        else if (direction < 0 && at > 0) target = at - Character.charCount(held.codePointBefore(at));
        else if (direction > 0 && at < held.length()) target = at + Character.charCount(held.codePointAt(at));
        moveTo(target, extend);
    }

    /** Where a step of a word from the caret lands: past the spaces, then to the end of the word. */
    private int wordEdge(int direction){
        String held = value();
        int at = caret();
        if (direction < 0) {
            while (at > 0 && Character.isWhitespace(held.charAt(at - 1))) at--;
            while (at > 0 && !Character.isWhitespace(held.charAt(at - 1))) at--;
        } else {
            while (at < held.length() && Character.isWhitespace(held.charAt(at))) at++;
            while (at < held.length() && !Character.isWhitespace(held.charAt(at))) at++;
        }
        return at;
    }

    /** Where the line the caret is on starts and ends, as indexes into the value. */
    public int lineStart(){
        return value().lastIndexOf('\n', caret() - 1) + 1;
    }

    public int lineEnd(){
        int end = value().indexOf('\n', caret());
        return end < 0 ? value().length() : end;
    }

    /** Moves the caret a line up or down, keeping its column where the line it lands on is long enough. */
    public void line(int direction, boolean extend){
        if (!isMultiline()) return;
        String held = value();
        int column = caret() - lineStart();
        if (direction < 0) {
            int start = lineStart();
            if (start == 0) { moveTo(0, extend); return; }
            int before = held.lastIndexOf('\n', start - 2) + 1;
            moveTo(Math.min(before + column, start - 1), extend);
        } else {
            int end = lineEnd();
            if (end >= held.length()) { moveTo(held.length(), extend); return; }
            int next = end + 1;
            int stop = held.indexOf('\n', next);
            moveTo(Math.min(next + column, stop < 0 ? held.length() : stop), extend);
        }
    }
}
