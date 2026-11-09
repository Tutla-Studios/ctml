package net.tutla.craftytml.paper.command.executor;

import net.tutla.craftytml.paper.command.Args;
import net.tutla.craftytml.paper.command.ExecutedUIs;
import net.tutla.craftytml.paper.command.Reply;
import net.tutla.craftytml.paper.command.Subcommand;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * What {@code variable} and {@code callback} have in common: list, put or remove an entry on a ui name, the
 * name of an executed ui or a hologram's id. The two only differ in what an entry is called, what it is
 * put to, and what their messages say, which each says for itself.
 */
public abstract class StoreCommand extends Subcommand {
    protected final ExecutedUIs executed;
    /** What one entry is called, "variable" or "callback". */
    private final String kind;

    public StoreCommand(String kind, String usage, String summary, ExecutedUIs executed, String... detail){
        super(kind, usage, summary, detail);
        this.kind = kind;
        this.executed = executed;
    }

    // ---- what each kind says for itself ----

    /** The entries a name has, or null where it has none. */
    abstract Map<String, String> entries(String name);

    /** Puts an entry on a name, and says how many open uis it reached. */
    abstract int put(String name, String key, String value);

    /** Takes an entry off a name, and says whether the name had it. */
    abstract boolean remove(String name, String key);

    /** What a put says it did, before how many uis it reached. */
    abstract String putMessage(String name, String key, String value);

    /** What asking for the entry's own name says, and for its value, and what remove takes none of. */
    abstract String nameTheKey();
    abstract String giveTheValue();
    abstract String valueWord();

    /** What list puts between an entry and its value, and the value as list shows it. */
    abstract String joiner();
    String shown(String value){ return value; }

    /** What tab offers for an entry's own name, and for each word of its value after it. */
    abstract List<String> keys(String name, String action);
    abstract List<String> values(String name, String key, int word);

    // ---- list, put and remove ----

    @Override
    public boolean run(CommandSender sender, String[] args){
        if (args.length < 2) return usage(sender, "Say whether to list, put or remove " + kind + "s.");
        String action = args[1].toLowerCase();
        if (action.equals("list")) {
            if (args.length > 3) return usage(sender, "list takes at most a ui name.");
            return list(sender, args.length == 3 ? args[2] : null);
        }
        if (!action.equals("put") && !action.equals("remove")) {
            return usage(sender, args[1] + " is not list, put or remove.");
        }
        if (args.length < 3) return usage(sender, "Name the ui the " + kind + " is for.");
        if (args.length < 4) return usage(sender, nameTheKey());
        String name = args[2], key = args[3];

        if (action.equals("remove")) {
            if (args.length > 4) return usage(sender, "remove takes no " + valueWord() + ".");
            if (!remove(name, key)) return Reply.fail(sender, name + " has no " + kind + " " + key + ".");
            return Reply.done(sender, "Removed " + kind + " " + key + " from " + name + ".");
        }
        if (args.length < 5) return usage(sender, giveTheValue());
        String value = Args.rest(args, 4);
        int open = put(name, key, value);
        return Reply.done(sender, putMessage(name, key, value) + Reply.reached(open) + ".");
    }

    /**
     * What has been put on one ui name, or on every name where none is given: each entry as its key, the
     * joiner and its value, under a heading saying whose they are.
     */
    private boolean list(CommandSender sender, String name){
        List<String> names = new ArrayList<>();
        if (name != null) names.add(name);
        else executed.names().forEach(names::add);

        boolean any = false;
        for (String each : names) {
            Map<String, String> entries = entries(each);
            if (entries == null) continue;
            any = true;
            sender.sendMessage(ChatColor.GOLD + each + ChatColor.GRAY + " (" + entries.size() + " "
                    + kind + (entries.size() == 1 ? "" : "s") + "):");
            entries.forEach((key, value) -> sender.sendMessage(ChatColor.GRAY + "  " + ChatColor.WHITE + key
                    + ChatColor.GRAY + joiner() + ChatColor.WHITE + shown(value)));
        }
        if (!any) {
            return Reply.fail(sender, name == null ? "No ui has any " + kind + "s." : name + " has no " + kind + "s.");
        }
        return true;
    }

    /** list, put or remove, then the ui name, then what that ui already has, then its value. */
    @Override
    public List<String> complete(CommandSender sender, String[] args){
        String typed = args[args.length - 1];
        int at = args.length - 1;
        if (at == 1) return Args.matching(List.of("list", "put", "remove"), typed);
        String action = args[1].toLowerCase();
        if (at == 2) return Args.matching(Args.uiNames(executed), typed);
        if (action.equals("list")) return List.of();
        if (at == 3) return Args.matching(keys(args[2], action), typed);
        if (!action.equals("put")) return List.of();
        return Args.matching(values(args[2], args[3], at - 4), typed);
    }

    /** The keys a name already has, sorted, for tab. */
    protected Set<String> existing(String name){
        Set<String> keys = new TreeSet<>();
        Map<String, String> had = entries(name);
        if (had != null) keys.addAll(had.keySet());
        return keys;
    }
}
