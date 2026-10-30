package net.tutla.craftytml.client.dom;

import java.util.HashMap;
import java.util.function.Consumer;

public class DOMContext {
    public final HashMap<String, Object> variables = new HashMap<>();
    private final HashMap<String, Consumer<CTMLCallback>> callbacks = new HashMap<>();
    private final DOMTreeElement headNode;

    public DOMContext(DOMTreeElement element){
        headNode = element;
    }

    public Consumer<CTMLCallback> getCallbackFromName(String name) {
        return callbacks.get(name);
    }

    public void putCallBack(String name, Consumer<CTMLCallback> consumer){
        callbacks.put(name, consumer);
    }

    public void putVariable(String name, Object value){
        variables.put(name, value);
    }

    public Object getVariableValue(String name){
        return variables.get(name);
    }

    public DOMTreeElement getElementById(String id){
        // TODO: fill this

        return null;
    }
}
