package net.tutla.craftytml.client.ctml.element.input;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.CTMLElementProperties;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.craftytml.client.ctml.property.Disabled;
import net.tutla.craftytml.client.ctml.property.Label;
import net.tutla.craftytml.client.ctml.property.Selected;
import net.tutla.craftytml.client.ctml.property.Value;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.dom.DOMTreeText;
import net.tutla.craftytml.client.tusan.CTMLTokenType;

/**
 * One choice in a select. It is never drawn where it is written: the select shows the one it is on,
 * and lists them all when it is opened. Its words are the text property or the text between its tags,
 * and what it stands for is the value property, which is the words again where it has none.
 *
 * @ctml.group input
 * @ctml.properties value, label, selected, disabled, text
 * @ctml.example <option value="b" text="Bee"></option>
 */
public class Option extends CTMLElement {
    private static final ElementStyle OPTION_DEFAULTS = DEFAULTS.toBuilder()
            .set(ElementStyle.DISPLAY, ElementStyle.Display.NONE)
            .build();

    public Option(){
        super("option", CTMLElementProperties.newElementProperties(), true,
                CTMLTokenType.OPTION, CTMLTokenType.OPTION_CLOSING);
    }

    @Override
    public ElementStyle defaultStyle(){
        return OPTION_DEFAULTS;
    }

    /** Whether it is listed but cannot be chosen. */
    public boolean isDisabled(){
        return propertyList.propertyFromName("disabled") instanceof Disabled disabled && disabled.isOn();
    }

    /** What the option says: its label, its text property, or the words written between its tags. */
    public String label(){
        if (propertyList.propertyFromName("label") instanceof Label written && written.hasValue()) {
            return resolve(written.getStringValue());
        }
        String own = getStringProperty("text");
        if (own != null) return own;

        DOMTreeElement self = domElement();
        if (self == null) return "";
        StringBuilder words = new StringBuilder();
        for (DOMTreeText run : self.getTextNodes()) {
            if (!words.isEmpty()) words.append(' ');
            words.append(run.getText().strip());
        }
        return words.toString();
    }

    /** What choosing it stands for: its value property, or its words where it has none. */
    public String choice(){
        return propertyList.propertyFromName("value") instanceof Value value && value.hasValue()
                ? value.getText()
                : label();
    }

    /** Whether it was written to be the one a select starts on. */
    public boolean isMarked(){
        return propertyList.propertyFromName("selected") instanceof Selected selected && selected.isOn();
    }
}
