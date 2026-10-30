package net.tutla.craftytml.client.dom;

import net.tutla.craftytml.client.dom.CTMLCallback.CallbackType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * What a screen's scripts and callbacks talk to: the variables the host put there, the callbacks it
 * registered by name, and the tree itself, which can be looked through by id.
 *
 * <p>Every element in the tree is named: one that a script gave an id keeps it, and one that was
 * given none has an id made up for it when the context is built, so {@link #getElementById} can
 * reach anything whether or not whoever wrote the script thought to name it.
 *
 * <p>It can also remember what the user did to the tree, by {@link #rememberState} and
 * {@link #restoreState}. Nothing calls those on its own: a screen opening or closing keeps nothing
 * unless the host asks it to.
 */
public class DOMContext {
    public final HashMap<String, Object> variables = new HashMap<>();
    private final HashMap<String, Consumer<CTMLCallback>> callbacks = new HashMap<>();
    private final DOMTreeElement headNode;

    public DOMContext(DOMTreeElement element){
        headNode = element;
        nameEverything();
    }

    public DOMTreeElement getHeadNode(){
        return headNode;
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

    /**
     * Calls the callback the element names, if it names one and the host registered it under that name.
     * Says whether anything was actually called, so whatever is handling the press knows to swallow it.
     */
    public boolean fire(CallbackType type, DOMTreeElement element, int keycode){
        if (element == null) return false;
        String name = element.getCallback();
        if (name == null) return false;

        Consumer<CTMLCallback> callback = callbacks.get(name);
        if (callback == null) return false;
        callback.accept(new CTMLCallback(type, element, this, keycode));
        return true;
    }

    /** The element with this id anywhere under the head node, or null where nothing has it. */
    public DOMTreeElement getElementById(String id){
        if (id == null || headNode == null) return null;
        return search(headNode, id);
    }

    private static DOMTreeElement search(DOMTreeElement element, String id){
        if (id.equals(element.getId())) return element;
        for (DOMTreeElement child : element.getChildElements()) {
            DOMTreeElement found = search(child, id);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * What the user did to the elements of one tree: how far a box was dragged and how far a scroll
     * container was scrolled. Kept in memory for as long as the game runs and written nowhere, so a
     * restart starts the screens over.
     */
    private static final Map<String, Map<String, State>> KEPT = new HashMap<>();

    /** What is worth remembering about one element: where it was put, and where its content was left. */
    private record State(float dragX, float dragY, int scrollX, int scrollY,
                         float scrollShareX, float scrollShareY) {

        static State of(DOMTreeElement element){
            return new State(element.getDragShareX(), element.getDragShareY(),
                    element.getScrollX(), element.getScrollY(),
                    element.getScrollShareX(), element.getScrollShareY());
        }

        /** Nothing was moved, so there is nothing to keep: a share below zero is one nothing set. */
        boolean isEmpty(){
            return dragX < 0 && dragY < 0 && scrollX == 0 && scrollY == 0
                    && scrollShareX < 0 && scrollShareY < 0;
        }

        void putOn(DOMTreeElement element){
            if (dragX >= 0 || dragY >= 0) element.setDragShare(dragX, dragY);
            element.setScrollOffset(scrollX, scrollY);
            // the share goes on last, since the pixels above forget it, and it is the one that is
            // believed: the window this is being put back into may be nothing like the one it left
            element.setScrollShare(scrollShareX, scrollShareY);
        }
    }

    /**
     * Remembers where this tree's elements were left, under whatever key the host wants them back by.
     * Hands back how many were worth remembering, and forgets the key entirely when none were.
     *
     * <p>Elements are recognised by their ids, so one the script named is found again whatever happens
     * to the tree, while a made up id lasts only as long as the tree that was handed it does.
     */
    public int rememberState(String key){
        if (key == null || headNode == null) return 0;
        Map<String, State> saved = new LinkedHashMap<>();

        headNode.walk(node -> {
            if (!(node instanceof DOMTreeElement element)) return;
            String id = element.getId();
            if (id == null) return;
            State state = State.of(element);
            if (!state.isEmpty()) saved.put(id, state);
        });

        if (saved.isEmpty()) KEPT.remove(key);
        else KEPT.put(key, saved);
        return saved.size();
    }

    /** Puts back whatever was remembered under this key, and says how many elements it reached. */
    public int restoreState(String key){
        if (key == null || headNode == null) return 0;
        Map<String, State> saved = KEPT.get(key);
        if (saved == null || saved.isEmpty()) return 0;

        int[] reached = { 0 };
        headNode.walk(node -> {
            if (!(node instanceof DOMTreeElement element)) return;
            State state = saved.get(element.getId());
            if (state == null) return;
            state.putOn(element);
            reached[0]++;
        });
        return reached[0];
    }

    /** Drops what was remembered under a key, so the next restore of it does nothing. */
    public static void forgetState(String key){
        KEPT.remove(key);
    }

    /** Hands an id to everything under the head node that has not got one of its own. */
    private void nameEverything(){
        if (headNode == null) return;
        headNode.walk(node -> {
            if (node instanceof DOMTreeElement element) element.getId();
        });
    }
}
