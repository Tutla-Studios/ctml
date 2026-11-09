package net.tutla.craftytml.paper.command;

import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.paper.CraftytmlPaper;
import net.tutla.craftytml.paper.render.WindowRenderer;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/** What every {@code /ctml} message looks like, so each subcommand only says what it has to say. */
public final class Reply {
    private Reply(){}

    /** A success, in green. Always true, so a branch can end on it. */
    public static boolean done(CommandSender sender, String message){
        sender.sendMessage(ChatColor.GREEN + message);
        return true;
    }

    /** A failure and why, in red. Always true: the usage bukkit prints for false would say nothing more. */
    public static boolean fail(CommandSender sender, String message){
        sender.sendMessage(ChatColor.RED + message);
        return true;
    }

    /** Input a command could not read, and how it is written. */
    public static boolean usage(CommandSender sender, String problem, String form){
        sender.sendMessage(ChatColor.RED + problem);
        sender.sendMessage(ChatColor.GRAY + "Usage: " + ChatColor.WHITE + form);
        return true;
    }

    /** A heading over a list, in gold. */
    public static void heading(CommandSender sender, String text){
        sender.sendMessage(ChatColor.GOLD + text);
    }

    /** One entry of a list: its name in white, then what it is in grey. */
    public static void entry(CommandSender sender, String name, String about){
        sender.sendMessage(ChatColor.WHITE + " " + name + ChatColor.GRAY + " - " + about);
    }

    /** "you" where the window is the sender's own, and the player's name where it is someone else's. */
    public static String whose(CommandSender sender, Player target){
        return sender == target ? "you" : target.getName();
    }

    /** ", in 3 open uis", or nothing where none are open to reach. */
    public static String reached(int open){
        return open == 0 ? "" : ", in " + open + (open == 1 ? " open ui" : " open uis");
    }

    /** A place as a person reads it: the world, the coordinates and the way it faces. */
    public static String describe(Location at){
        return (at.getWorld() == null ? "?" : at.getWorld().getName())
                + String.format(" %.1f %.1f %.1f, facing %.0f", at.getX(), at.getY(), at.getZ(), at.getYaw());
    }

    /** Hands a tree to a player's window, and says whether it opened, telling the sender why where it did not. */
    public static boolean show(CommandSender sender, Player target, String name, DOMTreeElement tree, DOMContext ctx){
        try {
            new WindowRenderer(target).show(name, tree, ctx);
            return true;
        } catch (RuntimeException e) {
            CraftytmlPaper.getPlugin(CraftytmlPaper.class).getLogger()
                    .log(Level.SEVERE, "could not open " + name + " for " + target.getName(), e);
            fail(sender, "Could not open " + name + " for " + target.getName() + ": " + e.getMessage());
            return false;
        }
    }
}
