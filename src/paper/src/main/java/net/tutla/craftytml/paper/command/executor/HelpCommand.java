package net.tutla.craftytml.paper.command.executor;

import net.tutla.craftytml.paper.command.Args;
import net.tutla.craftytml.paper.command.Reply;
import net.tutla.craftytml.paper.command.Subcommand;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;

/**
 * {@code /ctml help [command]}: every subcommand with a line on what it does, or one in detail. It reads
 * all of it off the subcommands themselves, so a new one shows up here by being added to the command.
 */
public final class HelpCommand extends Subcommand {
    private final Collection<Subcommand> subcommands;

    public HelpCommand(Collection<Subcommand> subcommands){
        super("help", "/ctml help [command]", "Shows these commands, or one of them in detail.");
        this.subcommands = subcommands;
    }

    @Override
    public boolean run(CommandSender sender, String[] args){
        if (args.length == 1) return all(sender);
        if (args.length > 2) return usage(sender, "help takes at most one command.");
        Subcommand page = find(args[1]);
        if (page == null) {
            Reply.fail(sender, "There is no /ctml " + args[1] + ".");
            return all(sender);
        }
        sender.sendMessage(ChatColor.GOLD + page.usage());
        sender.sendMessage(ChatColor.WHITE + page.summary());
        for (String line : page.detail()) sender.sendMessage(ChatColor.GRAY + " " + line);
        return true;
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args){
        return args.length == 2 ? Args.matching(subcommands.stream().map(Subcommand::name).toList(), args[1]) : List.of();
    }

    /** Every subcommand, how it is written and what it does. */
    public boolean all(CommandSender sender){
        sender.sendMessage(ChatColor.GOLD + "CraftyTML commands " + ChatColor.GRAY + "(/ctml help <command> for more):");
        for (Subcommand each : subcommands) {
            sender.sendMessage(ChatColor.WHITE + " " + each.usage() + ChatColor.GRAY + " - " + each.summary());
        }
        return true;
    }

    private Subcommand find(String name){
        for (Subcommand each : subcommands) if (each.name().equalsIgnoreCase(name)) return each;
        return null;
    }
}
