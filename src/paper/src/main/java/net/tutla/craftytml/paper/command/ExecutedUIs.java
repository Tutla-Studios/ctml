package net.tutla.craftytml.paper.command;

import net.tutla.craftytml.client.dom.CTMLCallback;
import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.paper.CraftytmlPaper;
import net.tutla.craftytml.paper.render.CTMLWindow;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The variables and callback commands of the uis {@code /ctml execute} shows, kept by ui name. What is put
 * here reaches every window open under that name straight away, and every one executed under it later.
 *
 * <p>Only the names and strings are kept. A tree lives in its window alone, so it is gone once the window
 * closes, and a name with nothing left on it is dropped as well.
 */
final class ExecutedUIs {
    /** What one ui name has had put on it. */
    private record Profile(Map<String, String> variables, Map<String, String> callbacks) {
        boolean isEmpty(){ return variables.isEmpty() && callbacks.isEmpty(); }
    }

    private final Map<String, Profile> profiles = new HashMap<>();

    /** The context for one executed window: the name's variables and callbacks, then the ones given with it. */
    DOMContext context(String name, DOMTreeElement tree, Player player, Map<String, String> callbacks){
        DOMContext ctx = new DOMContext(tree);
        Profile profile = profiles.get(name);
        if (profile != null) {
            profile.variables().forEach(ctx::putVariable);
            profile.callbacks().forEach((callback, command) -> ctx.putCallBack(callback, command(player.getUniqueId(), command)));
        }
        callbacks.forEach((callback, command) -> ctx.putCallBack(callback, command(player.getUniqueId(), command)));
        return ctx;
    }

    /** Puts a variable on a name, and says how many open windows it reached. */
    int putVariable(String name, String variable, String value){
        profile(name).variables().put(variable, value);
        List<CTMLWindow> open = CraftytmlPaper.windows().named(name);
        for (CTMLWindow window : open) window.context().putVariable(variable, value);
        return open.size();
    }

    /** Takes a variable off a name, and says whether the name had it. */
    boolean removeVariable(String name, String variable){
        Profile profile = profiles.get(name);
        boolean had = profile != null && profile.variables().remove(variable) != null;
        for (CTMLWindow window : CraftytmlPaper.windows().named(name)) window.context().removeVariable(variable);
        forgetIfEmpty(name);
        return had;
    }

    /** Puts a callback command on a name, and says how many open windows it reached. */
    int putCallback(String name, String callback, String command){
        profile(name).callbacks().put(callback, command);
        List<CTMLWindow> open = CraftytmlPaper.windows().named(name);
        for (CTMLWindow window : open) window.context().putCallBack(callback, command(window.owner(), command));
        return open.size();
    }

    /** Takes a callback off a name, and says whether the name had it. */
    boolean removeCallback(String name, String callback){
        Profile profile = profiles.get(name);
        boolean had = profile != null && profile.callbacks().remove(callback) != null;
        // a context has no way to forget a name, but one that leads nowhere is the same as none
        for (CTMLWindow window : CraftytmlPaper.windows().named(name)) window.context().putCallBack(callback, null);
        forgetIfEmpty(name);
        return had;
    }

    /** The ui names anything has been put on, for tab completion. */
    Iterable<String> names(){
        return profiles.keySet();
    }

    private Profile profile(String name){
        return profiles.computeIfAbsent(name, n -> new Profile(new LinkedHashMap<>(), new LinkedHashMap<>()));
    }

    private void forgetIfEmpty(String name){
        Profile profile = profiles.get(name);
        if (profile != null && profile.isEmpty()) profiles.remove(name);
    }

    /**
     * A callback that runs a command from the console when it is pressed, {@code {player}} in it standing
     * for whoever pressed it. A hover reaches the same callback, and is not a press, so it runs nothing.
     */
    private static Consumer<CTMLCallback> command(UUID owner, String command){
        String line = command.startsWith("/") ? command.substring(1) : command;
        return callback -> {
            if (!callback.isPress()) return;
            Player player = Bukkit.getPlayer(owner);
            if (player == null) return;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line.replace("{player}", player.getName()));
        };
    }
}
