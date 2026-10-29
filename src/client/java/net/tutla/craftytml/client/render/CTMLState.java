package net.tutla.craftytml.client.render;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.tutla.craftytml.client.ctml.property.ID;
import net.tutla.craftytml.client.dom.DOMTreeElement;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where the things the user did to a screen are kept: how far a box was dragged and how far a scroll
 * container was scrolled. Only the elements carrying an id are remembered, since there is nothing else
 * to recognise one by across a reload, and the whole lot is written next to the scripts so it survives
 * a restart. Away from a running game there is nowhere to write, and then it just remembers nothing.
 */
public final class CTMLState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type SHAPE = new TypeToken<Map<String, Map<String, Entry>>>(){}.getType();

    /** What is worth remembering about one element: where it was put, and where its content was left. */
    private static final class Entry {
        float dragX = -1;
        float dragY = -1;
        int scrollX;
        int scrollY;
        //and how far along the content that was, which is what survives a window of another size
        float scrollShareX = -1;
        float scrollShareY = -1;

        boolean isEmpty(){
            return dragX < 0 && dragY < 0 && scrollX == 0 && scrollY == 0
                    && scrollShareX < 0 && scrollShareY < 0;
        }
    }

    private static Map<String, Map<String, Entry>> screens;
    private static boolean loaded;

    private CTMLState(){}

    /** Puts back whatever was remembered about a screen's elements, by their ids. */
    public static void restore(String screen, DOMTreeElement root){
        if (screen == null || root == null) return;
        Map<String, Entry> saved = read().get(screen);
        if (saved == null || saved.isEmpty()) return;

        root.walk(node -> {
            if (!(node instanceof DOMTreeElement element)) return;
            Entry entry = saved.get(id(element));
            if (entry == null) return;
            if (entry.dragX >= 0 || entry.dragY >= 0) element.setDragShare(entry.dragX, entry.dragY);
            element.setScrollOffset(entry.scrollX, entry.scrollY);
            // the share goes on last, since the pixels above forget it, and it is the one that is
            // believed: the window this is being put back into may be nothing like the one it left
            element.setScrollShare(entry.scrollShareX, entry.scrollShareY);
        });
    }

    /** Remembers where a screen's elements were left, and writes the lot out. */
    public static void remember(String screen, DOMTreeElement root){
        if (screen == null || root == null) return;
        Map<String, Entry> saved = new LinkedHashMap<>();

        root.walk(node -> {
            if (!(node instanceof DOMTreeElement element)) return;
            String id = id(element);
            if (id == null) return;

            Entry entry = new Entry();
            entry.dragX = element.getDragShareX();
            entry.dragY = element.getDragShareY();
            entry.scrollX = element.getScrollX();
            entry.scrollY = element.getScrollY();
            entry.scrollShareX = element.getScrollShareX();
            entry.scrollShareY = element.getScrollShareY();
            if (!entry.isEmpty()) saved.put(id, entry);
        });

        Map<String, Map<String, Entry>> all = read();
        if (saved.isEmpty()) all.remove(screen);
        else all.put(screen, saved);
        write(all);
    }

    private static String id(DOMTreeElement element){
        return element.getProperty("id") instanceof ID id && id.hasValue() ? id.getStringValue() : null;
    }

    private static Map<String, Map<String, Entry>> read(){
        if (loaded) return screens;
        loaded = true;
        screens = new HashMap<>();

        Path file = file();
        if (file == null || !Files.isRegularFile(file)) return screens;
        try {
            Map<String, Map<String, Entry>> parsed =
                    GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), SHAPE);
            if (parsed != null) screens = parsed;
        } catch (Exception e) {
            System.out.println("[CTML] could not read " + file + ": " + e);
        }
        return screens;
    }

    private static void write(Map<String, Map<String, Entry>> all){
        screens = all;
        Path file = file();
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(all, SHAPE), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.out.println("[CTML] could not write " + file + ": " + e);
        }
    }

    /** Next to the scripts, or nowhere at all when there is no game around to ask. */
    private static Path file(){
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("ctml/state.json");
        } catch (Throwable ignored) {
            return null; // no loader, so this is a test or a tool and nothing is kept
        }
    }
}
