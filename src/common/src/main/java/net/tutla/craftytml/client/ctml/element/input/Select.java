package net.tutla.craftytml.client.ctml.element.input;

import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.style.Length;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

import java.util.ArrayList;
import java.util.List;

/**
 * A list to choose one thing from, written as a select with an option for each choice. The box shows
 * the choice it is on; a press opens the whole list under it, and a press on a choice there takes it.
 * The up and down keys walk the choices without opening it.
 *
 * <p>The value property is the value of the choice it is on, rewritten whenever the user chooses, and
 * a function the callback property names is called with a {@link net.tutla.craftytml.client.dom.CTMLCallback}
 * of type INPUT after each change. The list is drawn over everything else and is not styled by classes.
 * A select starts on the option marked selected, or the one its own value names, or the first.
 *
 * @ctml.group input
 * @ctml.properties value, required, autofocus, disabled, callback, tabindex
 * @ctml.example <select class="w-32" callback="pickedFruit"><option value="a" text="Apple"></option><option value="b" text="Bee"></option></select>
 */
public class Select extends FormControl {
    private static final ElementStyle SELECT_DEFAULTS = field()
            .set(ElementStyle.WIDTH, Length.px(120))
            .set(ElementStyle.PADDING_RIGHT, Length.px(14)) // room for the arrow
            .set(ElementStyle.OVERFLOW_X, ElementStyle.Overflow.HIDDEN)
            .set(ElementStyle.WHITE_SPACE, ElementStyle.WhiteSpace.PRE)
            .set(ElementStyle.TEXT_ALIGN, ElementStyle.TextAlign.LEFT)
            .build();

    private int chosen = -1; // an index into the options, or below zero until it has been worked out
    private int highlight;

    public Select(){
        super("select", true, CTMLTokenType.SELECT, CTMLTokenType.SELECT_CLOSING);
    }

    @Override
    public ElementStyle defaultStyle(){
        return SELECT_DEFAULTS;
    }

    /** Every option under it, in the order written. */
    public List<Option> options(){
        List<Option> found = new ArrayList<>();
        DOMTreeElement self = domElement();
        if (self == null) return found;
        for (DOMTreeElement child : self.getChildElements()) {
            if (child.getElement() instanceof Option option) found.add(option);
        }
        return found;
    }

    /** The index of the choice it is on, or below zero where it has no options at all. */
    public int selectedIndex(){
        List<Option> options = options();
        if (options.isEmpty()) return -1;
        if (chosen >= 0 && chosen < options.size()) return chosen;

        String named = value();
        int marked = -1;
        for (int i = 0; i < options.size(); i++) {
            if (!named.isEmpty() && options.get(i).choice().equals(named)) return chosen = i;
            if (marked < 0 && options.get(i).isMarked()) marked = i;
        }
        return chosen = Math.max(marked, 0);
    }

    /** Takes the choice at this index, and says whether that was a change. */
    public boolean choose(int index){
        List<Option> options = options();
        if (options.isEmpty() || !isEditable()) return false;

        int next = Math.clamp(index, 0, options.size() - 1);
        if (options.get(next).isDisabled()) return false;
        boolean moved = next != selectedIndex();
        chosen = next;
        setValue(options.get(next).choice());
        if (moved) changed();
        return moved;
    }

    @Override
    public String shownText(){
        int at = selectedIndex();
        return at < 0 ? " " : options().get(at).label();
    }

    public void open(){
        if (!isEditable() || options().isEmpty()) return;
        raise();
        highlight = Math.max(0, selectedIndex());
    }

    /** The choice the keyboard is on while the list is open. */
    public int highlighted(){
        return highlight;
    }

    public void highlight(int index){
        highlight = Math.clamp(index, 0, Math.max(0, options().size() - 1));
    }
}
