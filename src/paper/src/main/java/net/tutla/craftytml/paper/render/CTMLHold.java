package net.tutla.craftytml.paper.render;

import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.render.CTMLDrag;
import net.tutla.craftytml.client.render.CTMLEvents;
import net.tutla.craftytml.client.render.CTMLScroll;
import org.bukkit.plugin.Plugin;

/**
 * Something a player picked up with a click and carries with their look until their next click: a
 * draggable box, or the thumb of a scrollbar. A server hears a click but never the release after it, so on
 * a window or a hologram a drag is two clicks rather than a press held down, which is all that differs
 * from a screen: the box moves and the scroll follows the same way.
 */
final class CTMLHold {
    private final CTMLDrag.Grip box;
    private final CTMLScroll.Grip bar;

    private CTMLHold(CTMLDrag.Grip box, CTMLScroll.Grip bar){
        this.box = box;
        this.bar = bar;
    }

    static CTMLHold of(CTMLDrag.Grip box){
        return box == null ? null : new CTMLHold(box, null);
    }

    static CTMLHold of(CTMLScroll.Grip bar){
        return bar == null ? null : new CTMLHold(null, bar);
    }

    /** Moves what is held to a point in layout pixels: a box tells its callback it is being dragged. */
    void follow(Plugin plugin, DOMContext ctx, double x, double y){
        if (bar != null) {
            bar.follow(x, y);
            return;
        }
        box.follow(x, y);
        CTMLClicks.fire(plugin, () -> CTMLEvents.drag(ctx, box.element()));
    }
}
