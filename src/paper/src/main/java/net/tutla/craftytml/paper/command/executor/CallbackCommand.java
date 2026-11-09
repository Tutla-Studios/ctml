package net.tutla.craftytml.paper.command.executor;

import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.paper.CraftytmlPaper;
import net.tutla.craftytml.paper.command.Args;
import net.tutla.craftytml.paper.command.ExecutedUIs;
import net.tutla.craftytml.paper.render.CTMLWindow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** {@code /ctml callback list|put|remove [name] [callback] [command]}: the callback commands of executed uis and holograms. */
public final class CallbackCommand extends StoreCommand {
    public CallbackCommand(ExecutedUIs executed){
        super("callback", "/ctml callback list|put|remove [name] [callback] [command]",
                "Lists, sets or clears the callback commands of an executed ui or a hologram.", executed,
                "put <name> <callback> <command>: {player} in the command is whoever pressed it.",
                "remove <name> <callback>, and list [name] for one ui or all of them.",
                "Only a press runs the command, never a hover.");
    }

    @Override Map<String, String> entries(String name){ return executed.callbacks(name); }
    @Override int put(String name, String key, String value){ return executed.putCallback(name, key, value); }
    @Override boolean remove(String name, String key){ return executed.removeCallback(name, key); }

    @Override
    String putMessage(String name, String key, String value){
        return "Pressing " + key + " on " + name + " now runs /" + shown(value);
    }

    @Override String nameTheKey(){ return "Name the callback, as the markup's callback property names it."; }
    @Override String giveTheValue(){ return "Give the command the callback runs."; }
    @Override String valueWord(){ return "command"; }
    @Override String joiner(){ return " runs /"; }

    /** A command without the slash it may have been written with, since the joiner already has one. */
    @Override
    String shown(String value){
        return value.startsWith("/") ? value.substring(1) : value;
    }

    /** The callbacks it has, and on put, the ones the markup of its open windows names and nothing answers yet. */
    @Override
    List<String> keys(String name, String action){
        Set<String> keys = existing(name);
        if (action.equals("put")) {
            for (CTMLWindow window : CraftytmlPaper.windows().named(name)) {
                DOMTreeElement root = window.context().getHeadNode();
                if (root == null) continue;
                root.walk(node -> {
                    if (node instanceof DOMTreeElement element && element.getCallback() != null) keys.add(element.getCallback());
                });
            }
        }
        return List.copyOf(keys);
    }

    /** The server's commands for the first word, then {player}, selectors and players. */
    @Override
    List<String> values(String name, String key, int word){
        if (word == 0) return Args.serverCommands();
        List<String> options = new ArrayList<>(List.of("{player}"));
        options.addAll(Args.SELECTORS);
        options.addAll(Args.players());
        return options;
    }
}
