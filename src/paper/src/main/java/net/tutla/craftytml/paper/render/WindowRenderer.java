package net.tutla.craftytml.paper.render;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.UIRenderer;
import net.tutla.craftytml.paper.CraftytmlPaper;
import org.bukkit.entity.Player;

/**
 * The server's {@link UIRenderer}, for one player: a tree is opened as a window in front of them, in place
 * of any they had open, and a link is sent to their chat to click.
 */
public record WindowRenderer(Player player) implements UIRenderer {
    @Override
    public void show(String name, DOMTreeElement tree, DOMContext ctx){
        CraftytmlPaper.windows().open(player, name, tree, ctx);
    }

    /**
     * Shows a tree somewhere other than straight ahead: so many blocks right of and above where the player
     * is looking, turned to face them. {@code new CTMLWindow.Position(-1.5, 0)} puts it off to their left.
     */
    public void show(String name, DOMTreeElement tree, DOMContext ctx, CTMLWindow.Position position){
        CraftytmlPaper.windows().open(player, name, tree, ctx, CTMLWindow.Frame.DEFAULT, position);
    }

    @Override
    public void openLink(String url){
        TextComponent link = new TextComponent(url);
        link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        player.spigot().sendMessage(link);
    }
}
