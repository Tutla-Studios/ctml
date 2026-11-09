package net.tutla.craftytml.paper.command.executor;

import net.tutla.craftytml.paper.CraftytmlPaper;
import net.tutla.craftytml.paper.command.Args;
import net.tutla.craftytml.paper.command.Reply;
import net.tutla.craftytml.paper.command.Subcommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** {@code /ctml close [player]}: puts a player's window away. */
public final class CloseCommand extends Subcommand {
    public CloseCommand(){
        super("close", "/ctml close [player]", "Closes a player's window.",
                "Leave the player out to close your own. Sneaking closes it too.");
    }

    @Override
    public boolean run(CommandSender sender, String[] args){
        if (args.length > 2) return usage(sender, "Too many arguments.");
        Player target = Args.target(sender, args, 1);
        if (target == null) return true;
        if (!CraftytmlPaper.windows().close(target)) {
            return Reply.fail(sender, (sender == target ? "You have" : target.getName() + " has") + " no window open.");
        }
        return Reply.done(sender, "Closed " + (sender == target ? "your" : target.getName() + "'s") + " window.");
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args){
        return args.length == 2 ? Args.matching(Args.players(), args[1]) : List.of();
    }
}
