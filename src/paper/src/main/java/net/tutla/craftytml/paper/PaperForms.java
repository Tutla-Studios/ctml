package net.tutla.craftytml.paper;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.tutla.craftytml.client.ctml.element.input.FormControl;
import net.tutla.craftytml.client.ctml.element.input.Input;
import net.tutla.craftytml.client.ctml.element.input.Option;
import net.tutla.craftytml.client.ctml.element.input.Select;
import net.tutla.craftytml.client.ctml.element.input.TextArea;
import net.tutla.craftytml.client.ctml.property.MaxLength;
import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A window's form as one of minecraft's own dialogs, which is how a server gets text out of a player: it
 * has no keyboard to listen to. Every control in the window becomes an input in the dialog, in the order
 * they are written in: a text field or a textarea, a toggle for a checkbox, a slider for a range, and a
 * list of choices for a select or a group of radio buttons. Done writes every value back into the tree
 * and fires the input callback of each control it changed, the same as typing into it on a client does.
 *
 * <p>Dialogs are Paper's, so nothing may so much as load this class until {@code CTMLWindow} has found
 * them there: checking that from in here would already be too late on Spigot.
 */
final class PaperForms {
    private static final int DEFAULT_MAX_LENGTH = 256;
    private static final int TEXTAREA_HEIGHT = 96;

    private PaperForms(){}

    /** One input in the dialog: what it is called there, and how the answer gets back into the tree. */
    private interface Field {
        DialogInput input();
        /** Puts the answer into the tree, and says which elements changed. */
        List<DOMTreeElement> apply(DOMResponse response);
    }

    /** The dialog's answers, read by the key each input was given. */
    private record DOMResponse(DialogResponseView view) {
        String text(String key){ return view.getText(key); }
        Boolean bool(String key){ return view.getBoolean(key); }
        Float number(String key){ return view.getFloat(key); }
    }

    /** Opens the form of a window's tree in front of a player. Says false where there is nothing to fill in. */
    static boolean open(Plugin plugin, Player player, DOMContext ctx){
        DOMTreeElement root = ctx.getHeadNode();
        if (root == null) return false;

        List<Field> fields = new ArrayList<>();
        Map<String, List<DOMTreeElement>> radios = new LinkedHashMap<>();
        List<DOMTreeElement> controls = new ArrayList<>();
        root.walk(node -> {
            if (node instanceof DOMTreeElement element && element.getElement() instanceof FormControl control
                    && control.isEditable() && CTMLWindow.isControl(control)) {
                controls.add(element);
            }
        });

        for (DOMTreeElement element : controls) {
            String key = "f" + fields.size();
            FormControl control = (FormControl) element.getElement();
            Component label = Component.text(label(element, control));

            if (control instanceof Input input) {
                switch (input.kind()) {
                    case CHECKBOX -> fields.add(new Field() {
                        public DialogInput input(){ return DialogInput.bool(key, label).initial(input.isChecked()).build(); }
                        public List<DOMTreeElement> apply(DOMResponse r){
                            Boolean on = r.bool(key);
                            return on != null && input.setChecked(on) ? List.of(element) : List.of();
                        }
                    });
                    case RADIO -> {
                        String group = element.getString("name");
                        if (group == null || group.isEmpty()) group = key; // a radio of its own is a group of one
                        radios.computeIfAbsent(group, name -> new ArrayList<>()).add(element);
                        if (radios.get(group).size() == 1) fields.add(radioGroup(ctx, key, label, radios.get(group)));
                    }
                    case RANGE -> fields.add(new Field() {
                        public DialogInput input(){
                            int low = input.lowest(), high = Math.max(input.lowest() + 1, input.highest());
                            return DialogInput.numberRange(key, label, low, high)
                                    .step((float) Math.max(1, input.amount())).initial((float) input.position()).build();
                        }
                        public List<DOMTreeElement> apply(DOMResponse r){
                            Float value = r.number(key);
                            return value != null && input.setValue(String.valueOf(Math.round(value)))
                                    ? List.of(element) : List.of();
                        }
                    });
                    default -> fields.add(text(key, label, element, control));
                }
            } else if (control instanceof Select select) {
                fields.add(choice(key, label, element, select));
            } else {
                fields.add(text(key, label, element, control));
            }
        }
        if (fields.isEmpty()) return false;

        List<DialogInput> inputs = new ArrayList<>();
        for (Field field : fields) inputs.add(field.input());

        ActionButton done = ActionButton.builder(Component.text("Done"))
                .action(DialogAction.customClick((view, audience) ->
                        // answers can come in off the main thread, and the tree is only touched on it
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            DOMResponse response = new DOMResponse(view);
                            for (Field field : fields) {
                                for (DOMTreeElement changed : field.apply(response)) changed(plugin, ctx, changed);
                            }
                        }),
                        ClickCallback.Options.builder().uses(1).build()))
                .build();
        ActionButton cancel = ActionButton.builder(Component.text("Cancel")).build();

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Form"))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .inputs(inputs)
                        .build())
                .type(DialogType.confirmation(done, cancel)));
        player.showDialog(dialog);
        return true;
    }

    private static Field text(String key, Component label, DOMTreeElement element, FormControl control){
        return new Field() {
            public DialogInput input(){
                int limit = element.getProperty("maxlength") instanceof MaxLength max
                        ? max.getLimit() : DEFAULT_MAX_LENGTH;
                TextDialogInput.Builder builder = DialogInput.text(key, label)
                        .initial(control.value())
                        .maxLength(Math.max(1, Math.max(limit, control.value().length())));
                if (control instanceof TextArea) {
                    builder.multiline(TextDialogInput.MultilineOptions.create(null, TEXTAREA_HEIGHT));
                }
                return builder.build();
            }
            public List<DOMTreeElement> apply(DOMResponse r){
                String text = r.text(key);
                return text != null && control.setValue(text) ? List.of(element) : List.of();
            }
        };
    }

    private static Field choice(String key, Component label, DOMTreeElement element, Select select){
        return new Field() {
            public DialogInput input(){
                List<SingleOptionDialogInput.OptionEntry> entries = new ArrayList<>();
                List<Option> options = select.options();
                for (int i = 0; i < options.size(); i++) {
                    if (options.get(i).isDisabled()) continue;
                    entries.add(SingleOptionDialogInput.OptionEntry.create(String.valueOf(i),
                            Component.text(options.get(i).label()), i == select.selectedIndex()));
                }
                return DialogInput.singleOption(key, label, entries).build();
            }
            public List<DOMTreeElement> apply(DOMResponse r){
                Integer index = index(r.text(key));
                return index != null && select.choose(index) ? List.of(element) : List.of();
            }
        };
    }

    /** A group of radio buttons under one name, as one list of choices. */
    private static Field radioGroup(DOMContext ctx, String key, Component label, List<DOMTreeElement> group){
        return new Field() {
            public DialogInput input(){
                List<SingleOptionDialogInput.OptionEntry> entries = new ArrayList<>();
                for (int i = 0; i < group.size(); i++) {
                    Input radio = (Input) group.get(i).getElement();
                    String shown = radio.value().isEmpty() ? "option " + (i + 1) : radio.value();
                    entries.add(SingleOptionDialogInput.OptionEntry.create(String.valueOf(i),
                            Component.text(shown), radio.isChecked()));
                }
                return DialogInput.singleOption(key, label, entries).build();
            }
            public List<DOMTreeElement> apply(DOMResponse r){
                Integer index = index(r.text(key));
                if (index == null || index < 0 || index >= group.size()) return List.of();
                DOMTreeElement picked = group.get(index);
                return CTMLWindow.tick(ctx, picked, (Input) picked.getElement()) ? List.of(picked) : List.of();
            }
        };
    }

    private static Integer index(String id){
        try {
            return id == null ? null : Integer.parseInt(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** What an input is called in the dialog: its name, its placeholder, or what kind of control it is. */
    private static String label(DOMTreeElement element, FormControl control){
        String name = element.getString("name");
        if (name != null && !name.isBlank()) return name;
        String placeholder = element.getString("placeholder");
        if (placeholder != null && !placeholder.isBlank()) return placeholder;
        if (control instanceof Input input) return input.kind().name().toLowerCase();
        return element.getTag();
    }

    private static void changed(Plugin plugin, DOMContext ctx, DOMTreeElement element){
        try {
            ctx.fire(net.tutla.craftytml.client.dom.CTMLCallback.CallbackType.INPUT, element,
                    net.tutla.craftytml.client.dom.CTMLCallback.NO_KEY);
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "a ctml callback threw", e);
        }
    }
}
