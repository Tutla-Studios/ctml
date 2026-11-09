package net.tutla.craftytml.paper.render;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.UIRenderer;
import net.tutla.craftytml.paper.CraftytmlPaper;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * The {@link UIRenderer} for holograms: a tree is stood in the world at a location for everyone to see, in
 * place of whatever this renderer showed before, the way a screen replaces the one before it. A link goes
 * to the chat of whoever clicked the hologram last.
 *
 * <p>Each tree goes up under the name it is shown with, unless the renderer was given an id of its own, in
 * which case everything it shows goes up under that one id: the way to stand one ui that moves on to
 * another when a button in it is pressed.
 */
public final class HologramRenderer implements UIRenderer {
    private final Location location;
    private final String id;
    /** The id of the hologram this renderer put up last, which the next one it shows replaces. */
    private String shown;

    /** Stands each tree under the name it is shown with. */
    public HologramRenderer(Location location){
        this(location, null);
    }

    /** Stands everything under one id, each tree replacing the one before. */
    public HologramRenderer(Location location, String id){
        this.location = location.clone();
        this.id = id;
    }

    @Override
    public void show(String name, DOMTreeElement tree, DOMContext ctx){
        String standing = id != null ? id : name;
        CTMLHolograms holograms = CraftytmlPaper.holograms();
        if (shown != null && !shown.equals(standing)) holograms.remove(shown);
        holograms.spawn(standing, location, tree, ctx);
        shown = standing;
    }

    @Override
    public void openLink(String url){
        CTMLHologram hologram = shown == null ? null : CraftytmlPaper.holograms().get(shown);
        Player presser = hologram == null ? null : hologram.presser();
        if (presser == null) return;
        TextComponent link = new TextComponent(url);
        link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        presser.spigot().sendMessage(link);
    }
}
