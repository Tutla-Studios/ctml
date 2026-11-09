package net.tutla.craftytml.paper.render;

import net.tutla.craftytml.client.ctml.element.input.FormControl;
import net.tutla.craftytml.client.ctml.element.input.Input;
import net.tutla.craftytml.client.dom.CTMLCallback;
import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.CTMLEvents;
import net.tutla.craftytml.client.render.CTMLFocus;
import net.tutla.craftytml.client.render.CTMLLayout;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.logging.Level;

/**
 * What a click on a laid out tree does, the same for a window and a hologram: a control under it takes it
 * first, the way a client asks its controls before anything else, and otherwise the callback of whatever
 * is under it hears it.
 *
 * <p>A checkbox, a radio button and a slider answer a click straight away: a tick, a dot, or the slider set
 * to where it was aimed. Anything that needs typing into, a text field, a textarea, a select, a number, a
 * date or a colour, opens the tree's form as a dialog instead, on Paper; Spigot and Bukkit have no dialogs.
 */
final class CTMLClicks {
    /** How wide a slider's thumb is drawn, which a click on the track is measured against. */
    private static final int THUMB = 4;

    /** Whether the server has Paper's dialogs, which Spigot and Bukkit do not. Found without loading PaperForms. */
    private static final boolean DIALOGS = present("io.papermc.paper.dialog.Dialog");

    private CTMLClicks(){}

    private static boolean present(String name){
        try {
            Class.forName(name, false, CTMLClicks.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * A press of one of {@link CTMLEvents.MouseButton}'s buttons at a point in layout pixels. Says whether
     * anything took it, a control or a callback, so the caller can try something else with it.
     */
    static boolean press(Plugin plugin, Player player, CTMLLayout.Box layout, DOMContext ctx, int button,
                         double x, double y){
        if (layout == null) return false;
        CTMLLayout.Box control = button == CTMLEvents.MouseButton.LEFT ? control(CTMLFocus.path(layout, x, y)) : null;
        if (control != null) {
            operate(plugin, player, ctx, control, x);
            return true;
        }

        boolean[] taken = { false };
        fire(plugin, () -> taken[0] = CTMLEvents.click(layout, ctx, button, x, y));
        if (taken[0]) player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5f, 1.2f);
        return taken[0];
    }

    /** Whether a control is one a form fills in, rather than a button that is pressed or one never shown. */
    static boolean isControl(FormControl control){
        if (!(control instanceof Input input)) return true;
        return switch (input.kind()) {
            case BUTTON, SUBMIT, RESET, HIDDEN -> false;
            default -> true;
        };
    }

    /** The innermost box in a chain that is a control a form fills in, or null. */
    private static CTMLLayout.Box control(List<CTMLLayout.Box> chain){
        for (int i = chain.size() - 1; i >= 0; i--) {
            if (chain.get(i).element().getElement() instanceof FormControl control && isControl(control)) {
                return chain.get(i);
            }
        }
        return null;
    }

    /** A click on a control: the ones a click can work on their own, and the form for the rest. */
    private static void operate(Plugin plugin, Player player, DOMContext ctx, CTMLLayout.Box box, double x){
        FormControl control = (FormControl) box.element().getElement();
        if (!control.isEditable()) return;
        DOMTreeElement element = box.element();

        if (control instanceof Input input) {
            boolean changed;
            switch (input.kind()) {
                case CHECKBOX -> changed = input.setChecked(!input.isChecked());
                case RADIO -> changed = tick(ctx, element, input);
                case RANGE -> changed = input.slideTo((float) ((x - box.x() - THUMB / 2.0) / Math.max(1, box.width() - THUMB)));
                default -> {
                    form(plugin, player, ctx);
                    return;
                }
            }
            if (changed) {
                fire(plugin, () -> ctx.fire(CTMLCallback.CallbackType.INPUT, element, CTMLCallback.NO_KEY));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5f, 1.2f);
            }
            return;
        }
        form(plugin, player, ctx);
    }

    private static void form(Plugin plugin, Player player, DOMContext ctx){
        if (!DIALOGS) {
            player.sendMessage("Typing into a form needs a Paper server.");
            return;
        }
        PaperForms.open(plugin, player, ctx);
    }

    /**
     * Ticks a radio button and unticks every other one under the same name in the same tree. Says whether
     * anything changed: a button that is already ticked stays so, the way a browser has it.
     */
    static boolean tick(DOMContext ctx, DOMTreeElement element, Input radio){
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

    /** Runs a callback, and logs whatever it throws rather than letting it take the caller down. */
    static void fire(Plugin plugin, Runnable callback){
        try {
            callback.run();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "a ctml callback threw", e);
        }
    }
}
