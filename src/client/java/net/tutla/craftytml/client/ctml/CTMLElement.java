package net.tutla.craftytml.client.ctml;

import net.tutla.craftytml.client.ctml.property.ClassName;
import net.tutla.craftytml.client.ctml.property.Draggable;
import net.tutla.craftytml.client.ctml.property.ScrollInto;
import net.tutla.craftytml.client.ctml.property.ScrollX;
import net.tutla.craftytml.client.ctml.property.ScrollY;
import net.tutla.craftytml.client.ctml.style.TailwindParser;
import net.tutla.craftytml.client.dom.DOMTreeElement;
import net.tutla.craftytml.client.dom.DOMTreeNode;
import net.tutla.craftytml.client.tusan.CTMLTokenType;
import net.tutla.tums.tusan.TusanContext;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

public abstract class CTMLElement {
    public final String name;
    public final CTMLElementProperties propertyList; //wierd naming convention cuz properties would eb for an instance not like the shi itself iywim
    private ElementStyle style = ElementStyle.UNSET; //not final like the property list cuz every style set swaps the whole thing out
    private ElementStyle computed; //style over the element's defaults, kept until something changes it

    private DOMTreeNode node; //the dom node this element sits in, for marking a change on up the tree

    private int dragX = 0; //how far the user has dragged this element from where layout put it
    private int dragY = 0;

    private int scrollX = 0; //how far the content inside this element has been scrolled away from its start
    private int scrollY = 0;

    private int scrollTargetX = 0; //where the content is heading, which is where it already is unless it is sliding
    private int scrollTargetY = 0;

    //how far along the room it had the scroll sits, so the same place comes back after a resize
    private float scrollShareX = -1;
    private float scrollShareY = -1;
    private long scrollView = 0; //the viewport the pixels above were measured in

    private DOMTreeElement scrollAnchor; //the child the scroll is held against while something above it resizes
    private int scrollAnchorOffset;

    private String scrollInto; //an id a script asked to be brought into view, until whatever lays out takes it

    //where the drag sits as a share of the room it had, so the same spot comes back after a resize
    private float dragShareX = -1;
    private float dragShareY = -1;
    public final Boolean hasChildren;
    //private Boolean isFirstNode = false;

    public final CTMLTokenType tokenType;
    public final CTMLTokenType closingTokenType;

    public CTMLElement(String name, CTMLElementProperties propertyList, Boolean hasChildren, CTMLTokenType tokenType, CTMLTokenType closingTokenType){
        this.name = name;
        this.propertyList = propertyList;
        this.hasChildren = hasChildren;
        this.tokenType = tokenType;
        this.closingTokenType = closingTokenType;
    }

    /* public void makeFirstNode(){
        isFirstNode = true;
    }

    public Boolean isFirstNode(){
        return isFirstNode;
    }
     */

    public void updateProperty(String name, Object value, TusanContext ctx){
        propertyList.updateProperty(name, value, ctx);
        changed();

        ElementProperty property = propertyList.propertyFromName(name);
        if (property instanceof ClassName classes && classes.hasValue()){
            applyClassStyles(classes.getStringValue());
        }
        // the three a script sets to scroll the element, which is the only lever a script has
        if (property instanceof ScrollX x && x.hasValue()) setScrollOffset(x.getPixels(), scrollY);
        if (property instanceof ScrollY y && y.hasValue()) setScrollOffset(scrollX, y.getPixels());
        if (property instanceof ScrollInto into && into.hasValue()) scrollInto = into.getStringValue();
    }

    /**
     * Where an element starts before any class or hand set style lands on it, which is what css calls
     * the initial values. Only the non inherited ones belong here: putting a text colour on every
     * element would stop the parent's colour ever reaching the child.
     */
    public static final ElementStyle DEFAULTS = ElementStyle.builder()
            .set(ElementStyle.DISPLAY, ElementStyle.Display.BLOCK)
            .set(ElementStyle.POSITION, ElementStyle.Position.STATIC)
            .set(ElementStyle.BOX_SIZING, ElementStyle.BoxSizing.BORDER_BOX) // what tailwind's preflight does
            .build();

    /** Overridden by the elements that behave differently from a plain div. */
    public ElementStyle defaultStyle(){
        return DEFAULTS;
    }

    public void applyClassStyles(String classList){
        ElementStyle.Builder parsed = ElementStyle.builder();
        List<String> unknown = TailwindParser.parseInto(parsed, classList);
        if (!unknown.isEmpty()) System.out.println("[CTML] <" + name + "> has classes nothing knows how to style: " + unknown);
        setStyle(style.inherit(parsed.build()));
    }

    /** Everything set on the element, with its own defaults underneath for whatever is left. */
    public ElementStyle getStyle(){
        if (computed == null) computed = style.inherit(defaultStyle());
        return computed;
    }

    /** Only what was actually set on this element, so defaults don't get mistaken for author styles. */
    public ElementStyle getOwnStyle(){
        return style;
    }

    public void setStyle(ElementStyle style){
        this.style = style == null ? ElementStyle.UNSET : style;
        computed = null;
        changed();
    }

    public <T> void updateStyle(ElementStyle.Key<T> key, T value){
        setStyle(style.with(key, value));
    }

    public void updateStyle(String cssName, Object value){
        setStyle(style.toBuilder().set(cssName, value).build());
    }

    public boolean isDraggable(){
        return propertyList.propertyFromName("draggable") instanceof Draggable draggable && draggable.isOn();
    }

    public int getDragX(){ return dragX; }
    public int getDragY(){ return dragY; }

    /** Where the element sits relative to the spot layout gave it, moved by however far it has been dragged. */
    /**
     * Straight pixels, which is all a script has to go on, so this forgets any share that was remembered.
     * Whatever lays the element out works a share back out from it once it knows the room it had.
     */
    public void setDragOffset(int dragX, int dragY){
        dragShareX = -1;
        dragShareY = -1;
        if (dragX == this.dragX && dragY == this.dragY) return; // layout clamps these every frame, so most calls change nothing
        this.dragX = dragX;
        this.dragY = dragY;
        changed();
    }

    /**
     * How far along the room it was allowed the element was put, from nothing to all of it. That is what
     * gets remembered, so a window resize or a gui scale change brings the element back to the spot it
     * was put rather than to the same number of pixels. Below zero means nothing is remembered.
     */
    public void setDragShare(float shareX, float shareY){
        dragShareX = shareX < 0 ? -1 : Math.clamp(shareX, 0.0f, 1.0f);
        dragShareY = shareY < 0 ? -1 : Math.clamp(shareY, 0.0f, 1.0f);
    }

    public float getDragShareX(){ return dragShareX; }
    public float getDragShareY(){ return dragShareY; }

    /** Whether the element has been dragged at all, which a share of zero still counts as. */
    public boolean isDragged(){
        return dragX != 0 || dragY != 0 || dragShareX >= 0 || dragShareY >= 0;
    }

    /** Told to the element by the dom node built around it. */
    public void bind(DOMTreeNode node){
        this.node = node;
    }

    /** Says that what layout came up with for this element is out of date, and for everything above it. */
    private void changed(){
        if (node == null) DOMTreeNode.touch();
        else node.touched();
    }

    public int getScrollX(){ return scrollX; }
    public int getScrollY(){ return scrollY; }

    /**
     * How far the content inside the element has been scrolled, in gui pixels, counted from its start.
     * It lands there at once and forgets any share that was remembered, since pixels are all a script
     * or a scrollbar has to go on. Layout works a share back out from it once it knows the room it had.
     */
    public void setScrollOffset(int scrollX, int scrollY){
        scrollShareX = -1;
        scrollShareY = -1;
        scrollAnchor = null; // the user is looking somewhere else now, so the old anchor means nothing
        scrollTargetX = scrollX;
        scrollTargetY = scrollY;
        if (scrollX == this.scrollX && scrollY == this.scrollY) return;
        this.scrollX = scrollX;
        this.scrollY = scrollY;
        changed();
    }

    /** The same, but slid to over the next few frames rather than landed on at once. */
    public void slideScrollTo(int scrollX, int scrollY){
        scrollShareX = -1;
        scrollShareY = -1;
        scrollAnchor = null;
        if (scrollX == scrollTargetX && scrollY == scrollTargetY) return;
        scrollTargetX = scrollX;
        scrollTargetY = scrollY;
        changed();
    }

    public int getScrollTargetX(){ return scrollTargetX; }
    public int getScrollTargetY(){ return scrollTargetY; }

    /** Whether the content is still on its way somewhere, which is what keeps the frames coming. */
    public boolean isSliding(){
        return scrollX != scrollTargetX || scrollY != scrollTargetY;
    }

    /**
     * Moves the content a share of the way left to go, which over a few frames is an ease out.
     * Always worth at least a pixel, so a slide with a long way to go cannot stall just short.
     */
    public boolean advanceScroll(float seconds, float tau){
        if (!isSliding()) return false;
        float keep = (float) Math.exp(-seconds / Math.max(0.001f, tau));
        int x = step(scrollX, scrollTargetX, keep);
        int y = step(scrollY, scrollTargetY, keep);
        if (x == scrollX && y == scrollY) return false;
        scrollX = x;
        scrollY = y;
        changed();
        return true;
    }

    private static int step(int from, int to, float keep){
        if (from == to) return from;
        int moved = Math.round(to + (from - to) * keep);
        if (moved == from) moved = from + (to > from ? 1 : -1);
        return to > from ? Math.min(moved, to) : Math.max(moved, to);
    }

    /**
     * What layout should scroll the content by, given the room there turns out to be. A viewport that
     * has changed since the pixels were written down goes back to the share instead, so a window resize
     * or a gui scale change leaves the content looking at the same place rather than the same pixel.
     */
    public int scrollFor(boolean vertical, int max, long view){
        float share = vertical ? scrollShareY : scrollShareX;
        if (view != scrollView && share >= 0) return Math.round(share * max);
        return Math.clamp(vertical ? scrollY : scrollX, 0, max);
    }

    /** Told to the element once layout has worked out where the content ended up and how far it could go. */
    public void settleScroll(int scrollX, int scrollY, int maxX, int maxY, long view){
        scrollView = view;
        if (maxX > 0) scrollShareX = scrollX / (float) maxX;
        if (maxY > 0) scrollShareY = scrollY / (float) maxY;
        scrollTargetX = Math.clamp(scrollTargetX, 0, maxX);
        scrollTargetY = Math.clamp(scrollTargetY, 0, maxY);
        if (scrollX == this.scrollX && scrollY == this.scrollY) return;
        this.scrollX = scrollX;
        this.scrollY = scrollY;
        changed();
    }

    /** Where the scroll sits as a share of the room it had, from nothing to all of it, or below zero for none. */
    public float getScrollShareX(){ return scrollShareX; }
    public float getScrollShareY(){ return scrollShareY; }

    public void setScrollShare(float shareX, float shareY){
        scrollShareX = shareX < 0 ? -1 : Math.clamp(shareX, 0.0f, 1.0f);
        scrollShareY = shareY < 0 ? -1 : Math.clamp(shareY, 0.0f, 1.0f);
        scrollView = 0; // nothing has been measured yet, so the share is what gets believed
    }

    /** The child the scroll is being held against, and where in the content it sat when it was picked. */
    public DOMTreeElement getScrollAnchor(){ return scrollAnchor; }
    public int getScrollAnchorOffset(){ return scrollAnchorOffset; }

    public void setScrollAnchor(DOMTreeElement anchor, int offset){
        scrollAnchor = anchor;
        scrollAnchorOffset = offset;
    }

    /** An id a script asked to be brought into view. Taken rather than read, since it only happens once. */
    public String takeScrollInto(){
        String id = scrollInto;
        scrollInto = null;
        return id;
    }

    public CTMLElement newInstance(){
        try {
            return getClass().getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public DOMTreeElement newNode(){
        return new DOMTreeElement(newInstance());
    }
}
