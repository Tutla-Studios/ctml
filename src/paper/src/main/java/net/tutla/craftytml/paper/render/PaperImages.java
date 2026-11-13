package net.tutla.craftytml.paper.render;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;

/**
 * What an {@code <img>} shows on a server, which has no pictures to load: the item the texture belongs to,
 * drawn by each viewer's own client out of its own resources. {@code minecraft:textures/item/diamond.png}
 * is the diamond and {@code minecraft:textures/block/stone.png} the stone block, seen face on; an item id
 * such as {@code minecraft:diamond} names the same thing. Outside the minecraft namespace the name is taken
 * as an item model, so a resource pack's own items can be shown too.
 *
 * <p>A minecraft texture with no item of the same name, {@code block/oak_log_top} say, has nothing to show
 * and is left out. Every picture is taken as 16 pixels square, the size of a vanilla item.
 */
final class PaperImages {
    static final int SIZE = 16;

    private PaperImages(){}

    /** The item model a source stands for, or null where it names nothing a client could draw. */
    static NamespacedKey model(String source){
        if (source == null || source.isBlank()) return null;
        String name = source.trim().toLowerCase(Locale.ROOT);
        int colon = name.indexOf(':');
        String namespace = colon < 0 ? NamespacedKey.MINECRAFT : name.substring(0, colon);
        String path = colon < 0 ? name : name.substring(colon + 1);
        if (path.startsWith("textures/")) path = path.substring("textures/".length());
        if (path.endsWith(".png")) path = path.substring(0, path.length() - ".png".length());
        if (path.startsWith("item/")) path = path.substring("item/".length());
        else if (path.startsWith("block/")) path = path.substring("block/".length());

        NamespacedKey key = NamespacedKey.fromString(namespace + ":" + path);
        if (key == null) return null;
        if (!key.getNamespace().equals(NamespacedKey.MINECRAFT)) return key;
        Material material = Material.matchMaterial(key.toString());
        return material != null && material.isItem() ? key : null;
    }

    /** The stack an item display shows the model with. */
    static ItemStack stack(NamespacedKey model){
        if (model.getNamespace().equals(NamespacedKey.MINECRAFT)) {
            Material material = Material.matchMaterial(model.toString());
            if (material != null && material.isItem()) return new ItemStack(material);
        }
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setItemModel(model);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
