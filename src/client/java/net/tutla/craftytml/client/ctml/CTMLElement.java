package net.tutla.craftytml.client.ctml;

import net.tutla.craftytml.client.ctml.property.ClassName;
import net.tutla.craftytml.client.ctml.property.Draggable;
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

    private int dragX = 0; //how far the user has dragged this element from where layout put it
    private int dragY = 0;

    private int scrollX = 0; //how far the content inside this element has been scrolled away from its start
    private int scrollY = 0;
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
        DOMTreeNode.touch();

        if (propertyList.propertyFromName(name) instanceof ClassName classes && classes.hasValue()){
            applyClassStyles(classes.getStringValue());
        }
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
        DOMTreeNode.touch();
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
    public void setDragOffset(int dragX, int dragY){
        if (dragX == this.dragX && dragY == this.dragY) return; // layout clamps these every frame, so most calls change nothing
        this.dragX = dragX;
        this.dragY = dragY;
        DOMTreeNode.touch();
    }

    public int getScrollX(){ return scrollX; }
    public int getScrollY(){ return scrollY; }

    /**
     * How far the content inside the element has been scrolled, in gui pixels, counted from its start.
     * Layout clamps this back into range every frame, since the content can shrink underneath it.
     */
    public void setScrollOffset(int scrollX, int scrollY){
        if (scrollX == this.scrollX && scrollY == this.scrollY) return;
        this.scrollX = scrollX;
        this.scrollY = scrollY;
        DOMTreeNode.touch();
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
