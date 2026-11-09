package net.tutla.craftytml.paper.render;

import net.tutla.craftytml.client.ctml.element.input.FormControl;
import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.render.CTMLEvents;
import net.tutla.craftytml.client.render.CTMLFocus;
import net.tutla.craftytml.client.render.CTMLLayout;
import net.tutla.craftytml.client.render.CTMLScroll;
import net.tutla.craftytml.client.render.CTMLStates;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * A ctml tree standing in the world: a {@link CTMLSurface} with no one player behind it. Its middle is at
 * the location it was put at, and with {@link CTMLSurface.Facing#FIXED} its front faces the way that location
 * looks, yaw and pitch, rolled by {@link Settings#roll()}. With {@link CTMLSurface.Facing#CAMERA} or
 * {@link CTMLSurface.Facing#VERTICAL} it turns to face each viewer instead, on their own client.
 *
 * <p>A hologram is shared or per player, by {@link Settings#perPlayer()}. A shared one is one tree everyone
 * sees: one player's click, hover or drag is what everyone sees happen. A per-player one gives every player
 * near it a tree of their own, built by a factory and seen by them alone, so what one of them does stays
 * theirs; the factory is how it can, since one tree cannot be in two places.
 *
 * <p>Nobody is held still. Where anything in a tree can be clicked, everyone looking at it from within reach
 * gets a cursor of their own, seen by them alone, and their clicks work the way they do in a window, through
 * {@link CTMLClicks}: focus moves, a control takes the click, otherwise the callback under it hears it, and a
 * draggable box or a scrollbar is picked up by one click, follows that player's look, and is put down by
 * their next. Turning the hotbar while looking at it scrolls the box under their look, and the slot only
 * stays put where something scrolled. hover: and the hover callback follow the nearest player looking at a
 * tree, and active: lights up on a press.
 *
 * <p>{@link View#presser()} is whoever clicked a tree last, which is how a callback knows who pressed it.
 * Holograms are not saved: a restart or a reload takes them all away.
 */
public final class CTMLHologram {
    /** What a per-player hologram builds for each player: their own tree, and the context it is shown with. */
    public record Content(DOMTreeElement tree, DOMContext ctx) {}

    /**
     * How a hologram stands: which way it faces, its roll in degrees about that facing, whether each player
     * gets a tree of their own, and how big it is. Yaw and pitch are the location's.
     */
    public record Settings(CTMLSurface.Facing facing, double roll, boolean perPlayer, CTMLWindow.Frame frame) {
        /** Fixed, unrolled, shared, the size of a small screen. */
        public static final Settings DEFAULT = new Settings(CTMLSurface.Facing.FIXED, 0, false, CTMLWindow.Frame.DEFAULT);

        public Settings {
            if (facing == null) facing = CTMLSurface.Facing.FIXED;
            if (frame == null) frame = CTMLWindow.Frame.DEFAULT;
        }

        public Settings withFacing(CTMLSurface.Facing to){ return new Settings(to, roll, perPlayer, frame); }
        public Settings withRoll(double to){ return new Settings(facing, to, perPlayer, frame); }
        public Settings withPerPlayer(boolean to){ return new Settings(facing, roll, to, frame); }
        public Settings withFrame(CTMLWindow.Frame to){ return new Settings(facing, roll, perPlayer, to); }
    }

    private static final int CLICK_DEBOUNCE_TICKS = 4;
    /** How long a press holds active: on what it landed in, since a server never hears the release. */
    private static final int PRESS_TICKS = 3;
    /** How near a player has to be for a per-player hologram to build them a tree. */
    private static final double VIEW_RANGE = 48;
    private static final String CURSOR_GLYPH = "●";

    private final CTMLHolograms holograms;
    private final String id;
    private Settings settings;
    /** The one tree of a shared hologram, or null for a per-player one. */
    private final View shared;
    /** What builds each player's tree, for a per-player hologram, or null for a shared one. */
    private final Function<Player, Content> factory;
    private final Map<UUID, View> views = new HashMap<>();
    /** Whether anything a click could reach is in the trees, which is when there is a hitbox and cursors. */
    private final boolean interactive;

    private Location location;
    private Interaction hitbox;
    private UUID lastPresser;

    /** A shared hologram: the one tree, seen by everyone. */
    CTMLHologram(CTMLHolograms holograms, String id, DOMTreeElement tree, DOMContext ctx, Settings settings){
        this.holograms = holograms;
        this.id = id;
        this.settings = settings;
        this.factory = null;
        this.shared = new View(null, tree, ctx);
        this.interactive = clickable(tree);
    }

    /** A per-player hologram: a tree for each player near it, built by the factory and seen by them alone. */
    CTMLHologram(CTMLHolograms holograms, String id, Function<Player, Content> factory, Settings settings){
        this.holograms = holograms;
        this.id = id;
        this.settings = settings;
        this.factory = factory;
        this.shared = null;
        this.interactive = true; // the trees are not built yet, so there is nothing to look at to say otherwise
    }

    /** The id it was put down under. */
    public String id(){ return id; }

    /** How it stands. */
    public Settings settings(){ return settings; }

    /** Whether every player near it has a tree of their own. */
    public boolean isPerPlayer(){ return shared == null; }

    /** Where its middle is, and, when it is fixed, the way its front faces. */
    public Location location(){ return location.clone(); }

    /** Whether anything in its trees can be clicked. */
    public boolean isInteractive(){ return interactive; }

    /** The context of a shared hologram's tree, or null for a per-player one, which has one a player. */
    public DOMContext context(){ return shared == null ? null : shared.ctx; }

    /** Every tree it is showing now: the one of a shared hologram, or one per player near a per-player one. */
    public List<View> views(){
        return shared != null ? List.of(shared) : List.copyOf(views.values());
    }

    /** Every tree's context, for something that wants to reach into all of them at once. */
    public List<DOMContext> contexts(){
        List<DOMContext> all = new ArrayList<>();
        for (View view : views()) all.add(view.ctx);
        return all;
    }

    /** Whoever clicked it last, in any of its trees, or null where nobody has or they have left. */
    public Player presser(){
        return lastPresser == null ? null : holograms.plugin().getServer().getPlayer(lastPresser);
    }

    // ---- standing ----

    /** Moves it: its middle to the location, its front facing the way the location looks, rolled as it is set to. */
    void place(Location at){
        location = at.clone();
        for (View view : views()) view.place();

        if (hitbox != null && hitbox.isValid()) hitbox.remove();
        hitbox = null;
        if (interactive) {
            // empty handed right clicks at the air send the server nothing, so there is a box to aim at
            float size = (float) Math.max(settings.frame().widthBlocks(), settings.frame().heightBlocks());
            Location box = at.clone().subtract(0, size / 2, 0);
            box.setYaw(0);
            box.setPitch(0);
            hitbox = box.getWorld().spawn(box, Interaction.class, i -> {
                i.setPersistent(false);
                i.getPersistentDataContainer().set(holograms.entityKey(), PersistentDataType.BYTE, (byte) 1);
                i.setInteractionWidth(size);
                i.setInteractionHeight(size);
                i.setResponsive(false);
            });
        }
    }

    /** Moves it and turns it to a new roll, keeping how it faces and whether it is shared. */
    void place(Location at, double roll){
        settings = settings.withRoll(roll);
        place(at);
    }

    /**
     * Keeps it up to date, keeps it standing, and follows who is looking at it. Once a second it checks its
     * chunk: where the chunk was unloaded and came back without its displays, they are put up again, and a
     * chunk nobody is near is left alone until someone comes back to it. A per-player hologram also builds a
     * tree for every player who has come near, and takes down the trees of those who have gone.
     */
    void tick(int now){
        if (now % 20 == 0) {
            if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return;
            if (shared == null) gather();
            boolean broken = interactive && (hitbox == null || !hitbox.isValid());
            for (View view : views()) broken |= !view.surface.intact();
            if (broken) place(location);
        }
        for (View view : views()) view.tick(now);
    }

    /** Builds a tree for every player near a per-player hologram who has none, and drops those far away. */
    private void gather(){
        Set<UUID> near = new HashSet<>();
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) > VIEW_RANGE * VIEW_RANGE) continue;
            near.add(player.getUniqueId());
            if (views.containsKey(player.getUniqueId())) continue;
            Content content;
            try {
                content = factory.apply(player);
            } catch (RuntimeException e) {
                holograms.plugin().getLogger().log(Level.SEVERE, "hologram " + id + " could not build a tree for " + player.getName(), e);
                continue;
            }
            if (content == null || content.tree() == null) continue;
            View view = new View(player.getUniqueId(), content.tree(), content.ctx());
            views.put(player.getUniqueId(), view);
            view.place();
        }
        for (UUID gone : Set.copyOf(views.keySet())) {
            if (!near.contains(gone)) views.remove(gone).remove();
        }
    }

    /** The tree a player sees and works: the shared one, or their own. */
    private View viewOf(Player player){
        return shared != null ? shared : views.get(player.getUniqueId());
    }

    /** Whether an entity is this hologram's own hitbox. */
    boolean owns(Entity entity){
        return hitbox != null && hitbox.equals(entity);
    }

    /**
     * Where a player's look meets the tree they see, as { x, y, distance } with the point in layout pixels,
     * or null where they are not looking at it from within reach.
     */
    double[] lookedAt(Player player, double reach){
        View view = viewOf(player);
        return view == null ? null : view.lookedAt(player, reach);
    }

    /** A click from a player, on the tree they see. */
    void click(Player player, int button, int now){
        View view = viewOf(player);
        if (view != null) view.click(player, button, now);
    }

    /** A turn of the hotbar from a player looking at it. Says whether anything scrolled. */
    boolean scroll(Player player, int notches){
        View view = viewOf(player);
        return view != null && view.scroll(player, notches);
    }

    /** Forgets a player who has left: their clicks, their cursor, what they carried, and their own tree. */
    void forget(UUID player){
        if (shared != null) shared.forget(player);
        View theirs = views.remove(player);
        if (theirs != null) theirs.remove();
    }

    void remove(){
        for (View view : views()) view.remove();
        views.clear();
        if (hitbox != null && hitbox.isValid()) hitbox.remove();
        hitbox = null;
    }

    /**
     * Whether a tree has anything a click could reach: a callback, a control a form fills in, a box that
     * can be dragged or a box that can be scrolled, whose bar can be taken hold of.
     */
    private static boolean clickable(DOMTreeElement tree){
        boolean[] found = { false };
        tree.walk(node -> {
            if (!(node instanceof DOMTreeElement element)) return;
            boolean scrolls = CTMLScroll.userScrolls(CTMLScroll.overflowX(element.getStyle()))
                    || CTMLScroll.userScrolls(CTMLScroll.overflowY(element.getStyle()));
            if (element.getCallback() != null || element.isDraggable() || scrolls
                    || element.getElement() instanceof FormControl control && CTMLClicks.isControl(control)) {
                found[0] = true;
            }
        });
        return found[0];
    }

    /**
     * One tree of a hologram, and everything about it that is about who is using it: its surface, its
     * context, hover and press, a cursor for each player looking at it, what each of them is carrying, and
     * who clicked it last. A shared hologram has one, seen by everyone; a per-player one has one a player.
     */
    public final class View {
        private final UUID viewer;
        private final DOMContext ctx;
        private final CTMLSurface surface;
        private final CTMLStates states = new CTMLStates();
        private final Map<UUID, TextDisplay> cursors = new HashMap<>();
        private final Map<UUID, CTMLHold> holds = new HashMap<>();
        private final Map<UUID, Integer> lastClick = new HashMap<>();
        private DOMTreeElement hovered;
        private int releaseAt = -1;
        private UUID presser;

        private View(UUID viewer, DOMTreeElement tree, DOMContext ctx){
            this.viewer = viewer;
            this.ctx = ctx;
            this.surface = new CTMLSurface(holograms.plugin(), holograms.entityKey(), tree, settings.frame(),
                    viewer == null ? null : List.of(viewer), settings.facing());
        }

        /** The context its tree was put down with, whose variables and callbacks can be changed while it stands. */
        public DOMContext context(){ return ctx; }

        /** The one player who sees it, on a per-player hologram, or null where everyone does. */
        public Player viewer(){
            return viewer == null ? null : holograms.plugin().getServer().getPlayer(viewer);
        }

        /** Whoever clicked this tree last, or null where nobody has or they have left. */
        public Player presser(){
            return presser == null ? null : holograms.plugin().getServer().getPlayer(presser);
        }

        private void place(){
            Vector facing = location.getDirection().normalize();
            Vector along = facing.clone().multiply(-1); // the way someone reading it looks
            double yaw = Math.toRadians(location.getYaw() + 180);
            Vector fallbackRight = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw));
            surface.place(location.getWorld(), location.toVector(), along, fallbackRight, settings.roll());
            removeCursors(); // they were turned to face the old plane
            if (surface.layout() == null) surface.relayout();
        }

        private void tick(int now){
            surface.refresh();
            if (!interactive) return;

            // every player who sees it and looks at it gets their cursor moved and what they carry moved
            // along, and the nearest of them is who hover follows
            Player nearest = null;
            double[] nearestPoint = null;
            double best = Double.MAX_VALUE;
            Set<UUID> looking = new HashSet<>();
            double near = CTMLHolograms.REACH + Math.max(settings.frame().widthBlocks(), settings.frame().heightBlocks());
            for (Player player : location.getWorld().getPlayers()) {
                if (viewer != null && !viewer.equals(player.getUniqueId())) continue;
                if (player.getLocation().distanceSquared(location) > near * near) continue;
                double[] hit = surface.hit(player.getEyeLocation().toVector(), player.getEyeLocation().getDirection());
                if (hit == null || hit[2] > CTMLHolograms.REACH) continue;
                double[] point = surface.toLayout(hit[0], hit[1]);
                if (point == null) continue;
                looking.add(player.getUniqueId());
                moveCursor(player, hit[0], hit[1]);
                CTMLHold hold = holds.get(player.getUniqueId());
                if (hold != null) hold.follow(holograms.plugin(), ctx, point[0], point[1]);
                if (hit[2] < best) {
                    best = hit[2];
                    nearest = player;
                    nearestPoint = point;
                }
            }
            for (UUID gone : Set.copyOf(cursors.keySet())) {
                if (looking.contains(gone)) continue;
                TextDisplay cursor = cursors.remove(gone);
                if (cursor.isValid()) cursor.remove();
            }

            if (releaseAt >= 0 && now >= releaseAt && holds.isEmpty()) {
                releaseAt = -1;
                states.release();
            }
            CTMLLayout.Box layout = surface.layout();
            List<CTMLLayout.Box> under = nearestPoint == null || layout == null
                    ? List.of() : CTMLFocus.path(layout, nearestPoint[0], nearestPoint[1]);
            states.cursor(under);

            DOMTreeElement over = CTMLEvents.at(under, ctx);
            if (over != hovered) {
                hovered = over;
                CTMLClicks.fire(holograms.plugin(), () -> CTMLEvents.hover(ctx, over));
                if (over != null && nearest != null) {
                    nearest.playSound(nearest.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.15f, 2.0f);
                }
            }
        }

        private void moveCursor(Player player, double u, double v){
            TextDisplay cursor = cursors.get(player.getUniqueId());
            if (cursor == null || !cursor.isValid()) {
                cursor = surface.spawnMarker(player, CURSOR_GLYPH);
                cursors.put(player.getUniqueId(), cursor);
            }
            surface.moveMarker(cursor, player.getEyeLocation().toVector(), u, v);
        }

        private void removeCursors(){
            for (TextDisplay cursor : cursors.values()) if (cursor.isValid()) cursor.remove();
            cursors.clear();
        }

        private double[] lookedAt(Player player, double reach){
            if (player.getWorld() != surface.world()) return null;
            double[] hit = surface.hit(player.getEyeLocation().toVector(), player.getEyeLocation().getDirection());
            if (hit == null || hit[2] > reach) return null;
            double[] point = surface.toLayout(hit[0], hit[1]);
            return point == null ? null : new double[]{ point[0], point[1], hit[2] };
        }

        private void click(Player player, int button, int now){
            Integer last = lastClick.get(player.getUniqueId());
            if (last != null && now - last < CLICK_DEBOUNCE_TICKS) return;
            lastClick.put(player.getUniqueId(), now);

            if (holds.remove(player.getUniqueId()) != null) { // the click that puts a carried box or bar down is for nothing else
                if (holds.isEmpty()) states.release();
                CTMLClicks.putDown(player);
                return;
            }
            double[] point = lookedAt(player, CTMLHolograms.REACH);
            if (point == null || surface.layout() == null) return;
            presser = player.getUniqueId();
            lastPresser = presser;
            if (button == CTMLEvents.MouseButton.LEFT) {
                states.press(CTMLFocus.path(surface.layout(), point[0], point[1]));
                releaseAt = now + PRESS_TICKS;
            }
            CTMLHold hold = CTMLClicks.press(holograms.plugin(), player, surface.layout(), ctx, states, button, point[0], point[1]);
            if (hold != null) holds.put(player.getUniqueId(), hold);
        }

        private boolean scroll(Player player, int notches){
            if (notches == 0 || surface.layout() == null) return false;
            double[] point = lookedAt(player, CTMLHolograms.REACH);
            return point != null && CTMLScroll.wheel(surface.layout(), point[0], point[1], 0, -notches);
        }

        private void forget(UUID player){
            lastClick.remove(player);
            holds.remove(player);
            TextDisplay cursor = cursors.remove(player);
            if (cursor != null && cursor.isValid()) cursor.remove();
        }

        private void remove(){
            surface.remove();
            removeCursors();
            holds.clear();
            states.clear(); // nothing on the tree should look hovered or pressed if it is shown again
        }
    }
}
