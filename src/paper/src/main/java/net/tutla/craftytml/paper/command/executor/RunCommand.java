package net.tutla.craftytml.paper.command.executor;

import net.tutla.craftytml.client.CTMLAPI;
import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.paper.command.Args;
import net.tutla.craftytml.paper.command.Reply;
import net.tutla.craftytml.paper.command.Subcommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** {@code /ctml run <script> [player]}: a script from the ui folder, run again as a ui of one player's own. */
public final class RunCommand extends Subcommand {
    private final Path ui;

    public RunCommand(Path ui){
        super("run", "/ctml run <script> [player]", "Runs a script from the ui folder again, as a ui of that player's own.",
                "script is a file in plugins/CraftyTML/ui.",
                "The script sees the player as player, and the tree it builds is not registered.");
        this.ui = ui;
    }

    @Override
    public boolean run(CommandSender sender, String[] args){
        if (args.length < 2) return usage(sender, "Name the script to run.");
        if (args.length > 3) return usage(sender, "Too many arguments.");
        Path file = ui.resolve(args[1]).normalize();
        if (!file.startsWith(ui)) return Reply.fail(sender, "A script has to be inside " + ui + ".");
        if (!Files.isRegularFile(file)) return Reply.fail(sender, "There is no script called " + args[1] + " in " + ui + ".");
        Player target = Args.target(sender, args, 2);
        if (target == null) return true;
        DOMTreeElement tree = CTMLAPI.buildUI(file, Map.of("player", target));
        if (tree == null) return Reply.fail(sender, args[1] + " ran but built no ui. The console has its errors.");
        if (!Reply.show(sender, target, args[1], tree, new DOMContext(tree))) return true;
        return Reply.done(sender, "Ran " + args[1] + " for " + Reply.whose(sender, target) + ".");
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args){
        String typed = args[args.length - 1];
        return switch (args.length - 1) {
            case 1 -> Args.matching(scripts(), typed);
            case 2 -> Args.matching(Args.players(), typed);
            default -> List.of();
        };
    }

    /** The scripts in the ui folder, which run reads from. */
    private List<String> scripts(){
        try (Stream<Path> files = Files.list(ui)) {
            return files.filter(Files::isRegularFile).map(file -> file.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}
