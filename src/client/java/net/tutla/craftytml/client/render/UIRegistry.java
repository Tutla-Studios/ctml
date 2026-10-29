package net.tutla.craftytml.client.render;

import net.tutla.craftytml.client.dom.DOMTreeElement;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class UIRegistry {
    private static final LinkedHashMap<String, DOMTreeElement> UIMapping = new LinkedHashMap<>();

    public static void register(String name, DOMTreeElement treeStartNode){
        UIMapping.put(name, treeStartNode);
    }

    public static DOMTreeElement getUIFromName(String UIName){
        return UIMapping.get(UIName);
    }

    public static Set<String> getAllNames(){
        return UIMapping.keySet();
    }

    public static boolean isEmpty(){
        return UIMapping.isEmpty();
    }

    public static Map.Entry<String, DOMTreeElement> getFirstUI(){ // registration order, the order the scripts got run in
        for (Map.Entry<String, DOMTreeElement> entry : UIMapping.entrySet()) return entry;
        return null;
    }
}
