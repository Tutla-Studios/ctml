package net.tutla.craftytml.paper.command.executor;

import net.tutla.craftytml.client.render.UIRegistry;
import net.tutla.craftytml.paper.CraftytmlPaper;
import net.tutla.craftytml.paper.command.Args;
import net.tutla.craftytml.paper.command.Reply;
import net.tutla.craftytml.paper.command.Subcommand;
import net.tutla.craftytml.paper.render.CTMLWindow;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** {@code /ctml list [registered|running]}: the registered uis, and the windows and holograms up now. */
public final class ListCommand extends Subcommand {
    private final Path ui;

    public ListCommand(Path ui){
        super("list", "/ctml list [registered|running]", "Lists the registered uis and the windows open now.",
                "registered: the uis loaded from the ui folder or registered from java.",
                "running: every window open right now, by name, with who has it open.",
                "With neither, both.");
        this.ui = ui;
    }

    @Override
    public boolean run(CommandSender sender, String[] args){
        if (args.length > 2) return usage(sender, "list takes at most one of registered or running.");
        String which = args.length == 2 ? args[1].toLowerCase() : "both";
        return switch (which) {
            case "registered" -> registered(sender);
            case "running" -> running(sender);
            case "both" -> {
                registered(sender);
                yield running(sender);
            }
            default -> usage(sender, args[1] + " is not registered or running.");
        };
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args){
        return args.length == 2 ? Args.matching(List.of("registered", "running"), args[1]) : List.of();
    }

    /** The uis in the registry, which open and demo show. */
    private boolean registered(CommandSender sender){
        if (UIRegistry.isEmpty()) return Reply.fail(sender, "No uis are registered. Put .ctml files in " + ui + " and restart.");
        sender.sendMessage(ChatColor.GOLD + "Registered uis (" + UIRegistry.getAllNames().size() + "): "
                + ChatColor.WHITE + String.join(", ", UIRegistry.getAllNames()));
        return true;
    }

    /** Every window open right now, by the name it was opened under, with who has it open, then the holograms. */
    private static boolean running(CommandSender sender){
        List<CTMLWindow> open = CraftytmlPaper.windows().all();
        if (open.isEmpty()) {
            Reply.fail(sender, "No windows are open.");
        } else {
            Map<String, List<String>> byName = new TreeMap<>();
            for (CTMLWindow window : open) {
                Player owner = Bukkit.getPlayer(window.owner());
                byName.computeIfAbsent(window.name().isEmpty() ? "(unnamed)" : window.name(), n -> new ArrayList<>())
                        .add(owner == null ? window.owner().toString() : owner.getName());
            }
            Reply.heading(sender, "Running uis (" + open.size() + (open.size() == 1 ? " window" : " windows") + "):");
            byName.forEach((name, viewers) -> Reply.entry(sender, name, String.join(", ", viewers)));
        }
        if (!CraftytmlPaper.holograms().all().isEmpty()) HologramCommand.list(sender);
        return true;
    }
}
