package net.tutla.craftytml.client.ctml.element.input;

import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.property.Cols;
import net.tutla.craftytml.client.ctml.property.MaxLength;
import net.tutla.craftytml.client.ctml.property.Rows;
import net.tutla.craftytml.client.ctml.style.Length;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

import java.util.Arrays;
import java.util.List;

/**
 * A box of text that takes more than one line: enter starts a new line, and the up and down keys walk
 * the caret between them. What it holds is the value property, written as the user types, and a
 * function the callback property names is called with a {@link net.tutla.craftytml.client.dom.CTMLCallback}
 * of type INPUT after every change.
 *
 * <p>Lines are only ever broken where a new line was typed, never wrapped to fit: a longer one scrolls
 * sideways, and the box scrolls up and down once it has more lines than it is tall.
 *
 * <p>Everything about what it holds can be read back off the element, from a callback or anywhere else
 * that has the tree: cast {@code getElement()} to a TextArea and ask for {@link #details} to get the
 * lot at once (the text, its length, lines, words, the caret and the row and column it is on, the longest
 * line, what room is left under maxlength, and whether it is valid), or for any of them one at a time.
 * It is dressed with classes like any other box, and caret-* and placeholder-* set the colour of its
 * caret and its hint, and selection-* the highlight of what is selected. Ctrl with the left and right
 * arrows, backspace and delete works a word at a time.
 *
 * <p>Text can be selected with shift and the arrows, home and end, with ctrl and A, by dragging, with shift
 * and a press, by two quick presses (a word) or three (a line). Ctrl with C, X and V copies, cuts and
 * pastes, ctrl with Z undoes and ctrl with Y or shift Z redoes; typing, pasting and deleting replace what
 * is selected. The same goes for every text input. The starting text
 * is the value property, not text between the tags. Like every input it takes typing, pasting,
 * backspace, delete, the arrow keys, home and end, and selecting, copying and undoing, below.
 *
 * @ctml.group input
 * @ctml.properties value, placeholder, maxlength, minlength, rows, cols, required, autofocus, disabled, readonly, callback, tabindex
 * @ctml.example <textarea class="w-40 h-12" placeholder="notes" callback="notesChanged"></textarea>
 */
public class TextArea extends FormControl {
    private static final ElementStyle AREA_DEFAULTS = field()
            .set(ElementStyle.WIDTH, Length.px(160))
            .set(ElementStyle.HEIGHT, Length.px(48))
            .set(ElementStyle.OVERFLOW_X, ElementStyle.Overflow.AUTO)
            .set(ElementStyle.OVERFLOW_Y, ElementStyle.Overflow.AUTO)
            .set(ElementStyle.WHITE_SPACE, ElementStyle.WhiteSpace.PRE)
            .set(ElementStyle.TEXT_ALIGN, ElementStyle.TextAlign.LEFT)
            .build();

    public TextArea(){
        super("textarea", false, CTMLTokenType.TEXTAREA, CTMLTokenType.TEXTAREA_CLOSING);
    }

    @Override
    public ElementStyle defaultStyle(){
        int lines = propertyList.propertyFromName("rows") instanceof Rows rows ? rows.getLines() : 0;
        int chars = propertyList.propertyFromName("cols") instanceof Cols cols ? cols.getChars() : 0;
        if (lines <= 0 && chars <= 0) return AREA_DEFAULTS;

        // a line is about ten pixels and a character six, with the padding and the frame on top
        ElementStyle.Builder sized = AREA_DEFAULTS.toBuilder();
        if (lines > 0) sized.set(ElementStyle.HEIGHT, Length.px(lines * 10 + 6));
        if (chars > 0) sized.set(ElementStyle.WIDTH, Length.px(chars * 6 + 8));
        return sized.build();
    }

    @Override
    public boolean isTextField(){
        return true;
    }

    @Override
    public boolean isMultiline(){
        return true;
    }

    @Override
    public boolean showsPlaceholder(){
        return value().isEmpty() && !placeholder().isEmpty();
    }

    /**
     * The lines as they are typed, with a space standing in for each empty one: a line with nothing on
     * it is a line all the same, and it has to keep its height for the lines under it to stay put.
     */
    @Override
    public String shownText(){
        String held = value();
        if (held.isEmpty()) return showsPlaceholder() ? placeholder() : " ";

        String[] lines = held.split("\n", -1);
        StringBuilder shown = new StringBuilder(held.length() + 1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) shown.append('\n');
            shown.append(lines[i].isEmpty() ? " " : lines[i]);
        }
        return shown.toString();
    }

    // ---- what it holds ----

    /** Everything worth knowing about a textarea at one moment, read in one go. Rows and columns count from one. */
    public record Details(String value, int length, int lines, int words, int caret, int row, int column,
                          int longestLine, int remaining, boolean empty, boolean valid,
                          boolean disabled, boolean readOnly,
                          int selectionStart, int selectionEnd, String selectedText) {}

    /** Every line of the value, in order; an empty value is a single empty line, the way an editor has it. */
    public List<String> lines(){
        return Arrays.asList(value().split("\n", -1));
    }

    public int lineCount(){
        return lines().size();
    }

    /** One line of the value counting from zero, or an empty string where there is no such line. */
    public String lineText(int index){
        List<String> all = lines();
        return index >= 0 && index < all.size() ? all.get(index) : "";
    }

    /** The line the caret is on, counting from zero. */
    public int row(){
        String held = value();
        int row = 0;
        for (int i = 0, end = caret(); i < end; i++) if (held.charAt(i) == '\n') row++;
        return row;
    }

    /** How many characters in front of the caret on its line, counting from zero. */
    public int column(){
        return caret() - lineStart();
    }

    /** How many words, which are runs of anything that is not a space. */
    public int wordCount(){
        String held = value().strip();
        return held.isEmpty() ? 0 : held.split("\\s+").length;
    }

    /** The length of the longest line, in characters. */
    public int longestLine(){
        int longest = 0;
        for (String each : lines()) longest = Math.max(longest, each.length());
        return longest;
    }

    public boolean isEmpty(){
        return value().isEmpty();
    }

    /** How many more characters fit under maxlength, or -1 where there is no limit. */
    public int remaining(){
        int limit = propertyList.propertyFromName("maxlength") instanceof MaxLength max ? max.getLimit() : Integer.MAX_VALUE;
        return limit == Integer.MAX_VALUE ? -1 : Math.max(0, limit - value().length());
    }

    public Details details(){
        return new Details(value(), value().length(), lineCount(), wordCount(), caret(), row() + 1, column() + 1,
                longestLine(), remaining(), isEmpty(), isValid(), isDisabled(), isReadOnly(),
                selectionStart(), selectionEnd(), selectedText());
    }
}
