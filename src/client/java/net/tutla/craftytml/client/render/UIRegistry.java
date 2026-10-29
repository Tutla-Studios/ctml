package net.tutla.craftytml.client.render;

import net.tutla.craftytml.client.dom.DOMTreeElement;

import java.util.HashMap;
import java.util.Set;

public class UIRegistry {
    private static final HashMap<String, DOMTreeElement> UIMapping = new HashMap<>();

    public static void register(String name, DOMTreeElement treeStartNode){
        UIMapping.put(name, treeStartNode);
    }

    public static DOMTreeElement getUIFromName(String UIName){
        return UIMapping.get(UIName);
    }

    public static Set<String> getAllNames(){
        return UIMapping.keySet();
    }
}
