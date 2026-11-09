package net.tutla.craftytml.paper.command.executor;

import net.tutla.craftytml.paper.command.ExecutedUIs;

import java.util.List;
import java.util.Map;

/** {@code /ctml variable list|put|remove [name] [variable] [value]}: the variables of executed uis and holograms. */
public final class VariableCommand extends StoreCommand {
    public VariableCommand(ExecutedUIs executed){
        super("variable", "/ctml variable list|put|remove [name] [variable] [value]",
                "Lists, sets or clears the variables of an executed ui.", executed,
                "put <name> <variable> <value>: the value is the rest of the line.",
                "remove <name> <variable>, and list [name] for one ui or all of them.",
                "Every window open under the name changes straight away, and every one executed under it later.");
    }

    @Override Map<String, String> entries(String name){ return executed.variables(name); }
    @Override int put(String name, String key, String value){ return executed.putVariable(name, key, value); }
    @Override boolean remove(String name, String key){ return executed.removeVariable(name, key); }

    @Override
    String putMessage(String name, String key, String value){
        return "Set " + key + " on " + name + " to \"" + value + "\"";
    }

    @Override String nameTheKey(){ return "Name the variable."; }
    @Override String giveTheValue(){ return "Give the variable a value."; }
    @Override String valueWord(){ return "value"; }
    @Override String joiner(){ return " = "; }

    @Override
    List<String> keys(String name, String action){
        return List.copyOf(existing(name));
    }

    /** Its value as it stands, to change rather than retype. */
    @Override
    List<String> values(String name, String key, int word){
        Map<String, String> had = executed.variables(name);
        String now = had == null ? null : had.get(key);
        return word == 0 && now != null ? List.of(now) : List.of();
    }
}
