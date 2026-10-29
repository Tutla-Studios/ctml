package net.tutla.craftytml.client.render;

import net.tutla.craftytml.client.dom.DOMTreeElement;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class DOMContext {
    public final HashMap<String, Object> variables = new HashMap<>();
    private final HashMap<String, Consumer<Map<String, Object>>> callbacks = new HashMap<>();
    private final DOMTreeElement headNode;

    public DOMContext(DOMTreeElement element){
        headNode = element;
    }

    public Consumer<Map<String, Object>> getCallbackFromName(String name) {
        return callbacks.get(name);
    }

    public void putCallBack(String name, Consumer<Map<String, Object>> variables){
        callbacks.put(name, variables);
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
