package net.tutla.craftytml.paper.command;

import net.tutla.craftytml.paper.command.executor.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /ctml}, for showing trees to players and standing them in the world, from the console or in game.
 * This only finds the subcommand the first word names and hands it the rest: each one is its own class, with
 * its usage, its help and its tab completion beside what it does, and {@code /ctml help} reads them off.
 *
 * <ul>
 *   <li>{@link OpenCommand}, {@link RunCommand}, {@link DemoCommand} and {@link CloseCommand}: windows.
 *   <li>{@link ExecuteCommand}: markup written into the command.
 *   <li>{@link HologramCommand}: uis standing in the world.
 *   <li>{@link VariableCommand} and {@link CallbackCommand}: what executed uis and holograms have on them.
 *   <li>{@link ListCommand} and {@link HelpCommand}.
 * </ul>
 *
 * <p>Everything they say goes through {@link Reply}, and what they read goes through {@link Args}.
 */
public final class CTMLCommand implements TabExecutor {
    private final Map<String, Subcommand> subcommands = new LinkedHashMap<>();
    private final HelpCommand help;

    /** The command, running scripts out of this ui folder. */
    public CTMLCommand(Path ui){
        ExecutedUIs executed = new ExecutedUIs();
        add(new OpenCommand());
        add(new RunCommand(ui));
        add(new ExecuteCommand(executed));
        add(new HologramCommand(ui, executed));
        add(new VariableCommand(executed));
        add(new CallbackCommand(executed));
        add(new ListCommand(ui));
        add(new DemoCommand(ui));
        add(new CloseCommand());
        help = new HelpCommand(subcommands.values());
        add(help);
    }

    private void add(Subcommand subcommand){
        subcommands.put(subcommand.name(), subcommand);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        if (args.length == 0) return help.all(sender);
        Subcommand subcommand = subcommands.get(args[0].toLowerCase());
        if (subcommand == null) {
            Reply.fail(sender, "There is no /ctml " + args[0] + ".");
            return help.all(sender);
        }
        return subcommand.run(sender, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args){
        if (args.length == 1) return Args.matching(List.copyOf(subcommands.keySet()), args[0]);
        Subcommand subcommand = subcommands.get(args[0].toLowerCase());
        return subcommand == null ? List.of() : subcommand.complete(sender, args);
    }
}
