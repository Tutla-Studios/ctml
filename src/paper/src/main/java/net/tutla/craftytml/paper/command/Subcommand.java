package net.tutla.craftytml.paper.command;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * One {@code /ctml} subcommand: how it is written, what help says about it, what it does and what tab
 * offers for it. {@link CTMLCommand} only finds the subcommand by name and hands it the arguments.
 *
 * <p>The arguments a subcommand is handed still start with its own name, so its first argument is at
 * index 1, the same index the player sees counting the words after {@code /ctml}.
 */
public abstract class Subcommand {
    private final String name;
    private final String usage;
    private final String summary;
    private final List<String> detail;

    /** A subcommand, with how it is written, a line on what it does, and the lines help adds after it. */
    protected Subcommand(String name, String usage, String summary, String... detail){
        this.name = name;
        this.usage = usage;
        this.summary = summary;
        this.detail = List.of(detail);
    }

    public String name(){ return name; }
    public String usage(){ return usage; }
    public String summary(){ return summary; }
    public List<String> detail(){ return detail; }

    /** Runs it, and says something whatever happens: a success, a failure and why, or how it is written. */
    public abstract boolean run(CommandSender sender, String[] args);

    /** What can go in the argument being typed, the last one. Nothing, unless the subcommand says. */
    public List<String> complete(CommandSender sender, String[] args){
        return List.of();
    }

    /** Input this subcommand could not read, and its own usage under it. */
    protected boolean usage(CommandSender sender, String problem){
        return Reply.usage(sender, problem, usage);
    }
}
