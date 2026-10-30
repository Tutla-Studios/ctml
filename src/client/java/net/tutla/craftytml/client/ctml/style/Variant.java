package net.tutla.craftytml.client.ctml.style;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A condition written in front of a class, the way tailwind writes them: hover:bg-sky-500 is a
 * background that is only there while the cursor is over the element, md:flex a display that is only
 * there on a screen that wide. Several can be stacked, md:hover:underline among them, and every one
 * of them has to hold for the classes behind it to land.
 *
 * <p>What a variant is asked about is split in two: the ones about the element itself go through
 * {@link State}, which the element answers for itself, and the ones about the screen read {@link
 * Media}, since an element has no viewport of its own to look at.
 *
 * <p>There are no variants here for the things ctml has nothing to say about: no group- or peer-,
 * since nothing tracks another element's state for a sibling; no print, motion-reduce or
 * contrast-more, since there is no such setting to read; and no arbitrary [&amp;:nth-child(3)],
 * since there is no selector engine behind it. Anything else in front of a colon is a class the
 * parser hands back as unknown rather than quietly dropping.
 */
public enum Variant {
    /** The cursor is over the element or over something inside it, the way css's :hover reaches both. */
    HOVER("hover"),
    /** The element has the keyboard. focus-visible is the same thing here: a ctml focus is always shown. */
    FOCUS("focus"),
    FOCUS_VISIBLE("focus-visible"),
    /** The element has the keyboard or something inside it does. */
    FOCUS_WITHIN("focus-within"),
    /** A mouse button is being held on the element or on something inside it. */
    ACTIVE("active"),

    /** The nearest thing up the tree that says so is drawing dark, or the host says the ui is. */
    DARK("dark"),

    // the breakpoints, in gui pixels: see Media for why they are not css's numbers
    SM("sm", 320, true),
    MD("md", 480, true),
    LG("lg", 640, true),
    XL("xl", 854, true),
    XXL("2xl", 1024, true),
    MAX_SM("max-sm", 320, false),
    MAX_MD("max-md", 480, false),
    MAX_LG("max-lg", 640, false),
    MAX_XL("max-xl", 854, false),
    MAX_XXL("max-2xl", 1024, false),

    // where the element comes among the elements beside it, which is css's :first-child and friends
    FIRST("first"),
    LAST("last"),
    ONLY("only"),
    ODD("odd"),
    EVEN("even");

    /** Everything a variant can ask about one element, which the element answers for itself. */
    public interface State {
        boolean isHovered();
        boolean isFocused();
        /** Whether something inside the element has the keyboard. The element itself does not count. */
        boolean isFocusWithin();
        boolean isActive();
        /** Whether this element is being drawn dark, which is a colour scheme question. */
        boolean isDark();

        /** Where the element comes among the elements beside it, from zero, or below zero for no parent. */
        int siblingIndex();

        /** How many elements sit beside it, this one included, or zero where it has no parent. */
        int siblingCount();
    }

    private static final Map<String, Variant> BY_NAME = new HashMap<>();

    static {
        for (Variant variant : values()) BY_NAME.put(variant.name, variant);
    }

    public final String name;
    private final int breakpoint; // the gui width it is about, or zero where it is not about a width
    private final boolean atLeast; // and whether it wants to be over that width or under it

    Variant(String name){
        this(name, 0, true);
    }

    Variant(String name, int breakpoint, boolean atLeast){
        this.name = name;
        this.breakpoint = breakpoint;
        this.atLeast = atLeast;
    }

    /** The variant that word names, or null where nothing here is called that. */
    public static Variant named(String name){
        return BY_NAME.get(name);
    }

    /** Whether the classes behind this variant land on an element in this state. */
    public boolean holds(State state){
        if (breakpoint > 0) return atLeast ? Media.width() >= breakpoint : Media.width() < breakpoint;

        return switch (this) {
            case HOVER -> state.isHovered();
            case FOCUS, FOCUS_VISIBLE -> state.isFocused();
            case FOCUS_WITHIN -> state.isFocused() || state.isFocusWithin();
            case ACTIVE -> state.isActive();
            case DARK -> state.isDark();
            // css counts the children from one, so the first child is the odd one
            case FIRST -> state.siblingIndex() == 0;
            case LAST -> state.siblingIndex() >= 0 && state.siblingIndex() == state.siblingCount() - 1;
            case ONLY -> state.siblingIndex() >= 0 && state.siblingCount() == 1;
            case ODD -> state.siblingIndex() >= 0 && state.siblingIndex() % 2 == 0;
            case EVEN -> state.siblingIndex() >= 0 && state.siblingIndex() % 2 == 1;
            default -> false;
        };
    }

    /**
     * A set of variants as one number, so what holds for an element right now is worked out once and
     * then tested against every layer of its classes by a single and. There is room for sixty four
     * variants in it, which is more than twice what there are.
     */
    public static long mask(Set<Variant> variants){
        long mask = 0;
        for (Variant variant : variants) mask |= 1L << variant.ordinal();
        return mask;
    }

    @Override
    public String toString(){ return name; }
}
