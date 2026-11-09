package net.tutla.craftytml.paper.command.executor;

import net.tutla.craftytml.client.demo.DemoIntro;
import net.tutla.craftytml.paper.command.Args;
import net.tutla.craftytml.paper.command.Reply;
import net.tutla.craftytml.paper.command.Subcommand;
import net.tutla.craftytml.paper.render.WindowRenderer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.List;

/** {@code /ctml demo [player]}: the intro in a window, and the full demo from its button. */
public final class DemoCommand extends Subcommand {
    private final Path ui;

    public DemoCommand(Path ui){
        super("demo", "/ctml demo [player]", "Shows the intro, and the full demo from its button.",
                "Needs intro.ctml and demo_screen.ctml in the ui folder.");
        this.ui = ui;
    }

    @Override
    public boolean run(CommandSender sender, String[] args){
        if (args.length > 2) return usage(sender, "Too many arguments.");
        Player target = Args.target(sender, args, 1);
        if (target == null) return true;
        try {
            if (!DemoIntro.open(new WindowRenderer(target))) {
                return Reply.fail(sender, DemoIntro.INTRO_UI + " is not loaded. Put it in " + ui + " and restart.");
            }
        } catch (RuntimeException e) {
            return Reply.fail(sender, "Could not open the demo for " + target.getName() + ": " + e.getMessage());
        }
        return Reply.done(sender, "Opened the demo for " + Reply.whose(sender, target) + ".");
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args){
        return args.length == 2 ? Args.matching(Args.players(), args[1]) : List.of();
    }
}
