package net.tutla.craftytml.paper.command.executor;

import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.UIRegistry;
import net.tutla.craftytml.paper.command.Args;
import net.tutla.craftytml.paper.command.Reply;
import net.tutla.craftytml.paper.command.Subcommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** {@code /ctml open <name> [player]}: a registered ui, in a window in front of a player. */
public final class OpenCommand extends Subcommand {
    public OpenCommand(){
        super("open", "/ctml open <name> [player]", "Shows a registered ui to a player.",
                "name is a ui in the registry: /ctml list registered names them.",
                "Leave the player out to show it to yourself.");
    }

    @Override
    public boolean run(CommandSender sender, String[] args){
        if (args.length < 2) return usage(sender, "Name the ui to open.");
        if (args.length > 3) return usage(sender, "Too many arguments.");
        DOMTreeElement tree = UIRegistry.getUIFromName(args[1]);
        if (tree == null) return Reply.fail(sender, "No ui called " + args[1] + " is loaded. /ctml list names the ones that are.");
        Player target = Args.target(sender, args, 2);
        if (target == null) return true;
        if (!Reply.show(sender, target, args[1], tree, new DOMContext(tree))) return true;
        return Reply.done(sender, "Opened " + args[1] + " for " + Reply.whose(sender, target) + ".");
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args){
        String typed = args[args.length - 1];
        return switch (args.length - 1) {
            case 1 -> Args.matching(List.copyOf(UIRegistry.getAllNames()), typed);
            case 2 -> Args.matching(Args.players(), typed);
            default -> List.of();
        };
    }
}
