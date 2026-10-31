package net.tutla.craftytml.client.render;

import net.tutla.craftytml.client.dom.DOMTreeElement;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The trees {@link net.tutla.craftytml.client.CTMLAPI} has run, under the names they were run as: the
 * file name for one off disk, and whatever name was given for markup written in java.
 *
 * <p>Registration order is the order the scripts ran in, which is the order the ui folder listed them.
 * A tree in here is not showing: it is a tree, waiting for a screen or a hud to be built around it.
 */
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
