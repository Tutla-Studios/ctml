package net.tutla.craftytml.client.render;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.tutla.craftytml.client.dom.DOMContext;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.dom.DOMTreeNode;

public class CTMLScreen extends Screen {
    /** Sdl numbers the mouse buttons from one, so left is 1 and not 0 the way glfw had it. */
    private static final int LEFT_BUTTON = 1;

    private final DOMTreeElement domTreeHeadElement;
    private final DOMContext domCtx;
    private final Screen parent;

    /** The last frame's boxes, which is what gets drawn and what the cursor gets tested against. */
    private CTMLLayout.Box layout;

    /** What the tree and the screen looked like when that was worked out, so it is only done again when it has to be. */
    private long revision = -1;
    private int laidOutWidth = -1;
    private int laidOutHeight = -1;

    private CTMLDrag.Grip grip;

    /** A scrollbar thumb the user has hold of, which takes the press before any draggable box does. */
    private CTMLScroll.Grip bar;

    /**
     * What the keyboard is talking to: whatever was last pressed on, or whatever tab was last walked to.
     * A ring is drawn round it, and the keys that scroll go to it and then outwards from it.
     */
    private DOMTreeElement focus;

    /** Set when the focus has just moved, so the newly focused box is brought into view once. */
    private boolean reveal;

    /** Watches the cursor while something is held, so a release that never arrives cannot be waited on. */
    private final CTMLDrag.Watch watch = new CTMLDrag.Watch();

    /** When the last frame was drawn, so a smooth scroll moves by time rather than by frames. */
    private long drawnAt;

    public CTMLScreen(String name, DOMTreeElement domTreeHeadElement, Screen parent, DOMContext domCtx){
        super(Component.literal(name));
        this.domTreeHeadElement = domTreeHeadElement;
        this.parent = parent;
        this.domCtx = domCtx;
    }

    public DOMTreeElement getDomTreeHeadElement(){
        return domTreeHeadElement;
    }

    public Screen getParent(){
        return parent;
    }

    /**
     * Layout only runs again once something has actually moved: a style, a property, the tree, a drag or a
     * scroll bumps the dom's revision, and a resize changes the screen. Every other frame just draws the
     * boxes that are already worked out.
     */
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        // anything part way through a smooth scroll moves on by however long the last frame took,
        // which marks the tree as changed and so brings it back through the layout below
        long now = System.nanoTime();
        float elapsed = drawnAt == 0 ? 0 : Math.min(0.25f, (now - drawnAt) / 1_000_000_000.0f);
        drawnAt = now;
        CTMLScroll.animate(layout, elapsed);

        long current = DOMTreeNode.revision();
        if (layout == null || current != revision || width != laidOutWidth || height != laidOutHeight) {
            layout = CTMLRenderer.layout(domTreeHeadElement, width, height);

            // what a script asked to be brought into view, whatever tab has just walked to, and holding
            // still whatever a resize above it would otherwise have pushed out from under the user's
            // eye. All of them move a scroll offset, so the tree is laid out once more and this frame
            // draws where things end up rather than the frame after it
            boolean moved = CTMLScroll.intoViewPending(layout) | CTMLScroll.anchor(layout);
            if (reveal) {
                reveal = false;
                moved |= focus != null && CTMLScroll.intoView(layout, focus);
            }
            if (moved) layout = CTMLRenderer.layout(domTreeHeadElement, width, height);

            laidOutWidth = width;
            laidOutHeight = height;
            // laying out clamps the scroll and drag offsets it finds, which bumps the revision itself
            revision = DOMTreeNode.revision();
        }

        // a press ends on a release, and one that never arrives would leave the box following the
        // cursor for good, so a cursor drawn away from where the drag last saw it lets go instead
        if (holding() && watch.lost(mouseX, mouseY)) release();

        CTMLRenderer.paint(layout, graphics, focus);
    }

    /** Takes hold of whatever draggable box the press landed on, and swallows the click if it found one. */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        release(); // a press means whatever was held before is long since let go, heard of or not

        if (event.button() == LEFT_BUTTON) {
            // a press hands the keyboard to whatever it landed on, or to the nearest box above it that
            // can take it. Nothing focusable under the cursor leaves the focus where it was
            DOMTreeElement pressed = CTMLFocus.at(layout, event.x(), event.y());
            if (pressed != null) focus = pressed;

            bar = CTMLScroll.grab(layout, event.x(), event.y()); // the bars are drawn over everything in their box
            if (bar != null) {
                focus = bar.element();
                watch.saw(event.x(), event.y());
                return true;
            }
            grip = CTMLDrag.grab(layout, event.x(), event.y());
            if (grip != null) {
                watch.saw(event.x(), event.y());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    /**
     * The element is put where the cursor is rather than nudged by the deltas this also gets handed,
     * so a movement that arrives late or lands outside what the element is allowed cannot leave it
     * trailing behind the pointer. The event's position is in the same gui pixels as the layout.
     */
    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (holding() && event.button() != LEFT_BUTTON) {
            release(); // another button is doing the dragging now, so the left one is no longer down
            return super.mouseDragged(event, deltaX, deltaY);
        }

        watch.saw(event.x(), event.y());
        if (bar != null) {
            bar.follow(event.x(), event.y());
            return true;
        }
        if (grip == null) return super.mouseDragged(event, deltaX, deltaY);
        grip.follow(event.x(), event.y());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (holding() && event.button() == LEFT_BUTTON) {
            release();
            return true;
        }
        return super.mouseReleased(event);
    }

    /** Whether the user has hold of a box or of a scrollbar thumb. */
    private boolean holding() {
        return grip != null || bar != null;
    }

    /** Lets go of whatever was held. Safe to call when nothing is. */
    private void release() {
        grip = null;
        bar = null;
    }

    /**
     * The wheel goes to the innermost scrollable box under the cursor, and outwards from there. It
     * leaves the focus alone: what the keys talk to is not meant to wander off under the cursor.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (CTMLScroll.wheel(layout, mouseX, mouseY, scrollX, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * Tab walks the focus through everything that can take it and back round to the start, and shift
     * walks it the other way. Whatever it lands on is brought into view, the way a browser does it.
     *
     * <p>The arrows, page up and down and home and end scroll whatever has the focus, falling out to
     * the box around it once that one has nothing left to give. With nothing focused yet the outermost
     * scroll container takes them, which is the nearest thing ctml has to a browser scrolling the page.
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isCycleFocus()) { // tab, which is what minecraft calls walking the focus on
            DOMTreeElement next = CTMLFocus.next(layout, focus, event.hasShiftDown());
            if (next == null) return super.keyPressed(event);
            focus = next;
            reveal = true;
            return true;
        }

        CTMLScroll.Nudge nudge = nudgeFor(event);
        if (nudge != null && CTMLScroll.key(layout, focus, nudge)) return true;
        return super.keyPressed(event);
    }

    private static CTMLScroll.Nudge nudgeFor(KeyEvent event) {
        if (event.isUp()) return CTMLScroll.Nudge.LINE_UP;
        if (event.isDown()) return CTMLScroll.Nudge.LINE_DOWN;
        if (event.isLeft()) return CTMLScroll.Nudge.LINE_LEFT;
        if (event.isRight()) return CTMLScroll.Nudge.LINE_RIGHT;
        return switch (event.input()) {
            case InputConstants.KEY_PAGEUP -> CTMLScroll.Nudge.PAGE_UP;
            case InputConstants.KEY_PAGEDOWN -> CTMLScroll.Nudge.PAGE_DOWN;
            case InputConstants.KEY_HOME -> CTMLScroll.Nudge.TO_START;
            case InputConstants.KEY_END -> CTMLScroll.Nudge.TO_END;
            default -> null;
        };
    }

    /** Puts back whatever the user last did to this screen's elements, by their ids. */
    @Override
    protected void init() {
        super.init();
        release(); // a resize rebuilds the screen, and nothing held across it can be trusted
        reveal = false;
        CTMLState.restore(getTitle().getString(), domTreeHeadElement);
    }

    /** And remembers it on the way out, which is the only place it can be written down. */
    @Override
    public void removed() {
        release(); // the screen is going away mid drag, so the release will never arrive
        CTMLState.remember(getTitle().getString(), domTreeHeadElement);
        super.removed();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(parent);
    }
}
