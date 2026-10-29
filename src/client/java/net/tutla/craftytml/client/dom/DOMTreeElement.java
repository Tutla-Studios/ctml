package net.tutla.craftytml.client.dom;


import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.craftytml.client.ctml.ElementStyle;
import net.tutla.tums.tusan.TusanContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class DOMTreeElement extends DOMTreeNode {
    private final CTMLElement element;
    final List<DOMTreeNode> children = new ArrayList<>();

    public DOMTreeElement(CTMLElement element) {
        this.element = element;
        element.bind(this); // so a style set on the element marks the tree above it as changed
    }

    public CTMLElement getElement() { return element; }

    public String getTag() { return element.name; }

    public DOMTreeElement property(String name, Object value, TusanContext ctx) {
        element.updateProperty(name, value, ctx);
        return this;
    }

    public ElementProperty getProperty(String name) {
        return element.propertyList.propertyFromName(name);
    }

    public Object getPropertyValue(String name) {
        ElementProperty property = getProperty(name);
        return property == null ? null : property.getValue();
    }

    public <T> DOMTreeElement style(ElementStyle.Key<T> key, T value) {
        element.updateStyle(key, value);
        return this;
    }

    public DOMTreeElement style(String cssName, Object value) {
        element.updateStyle(cssName, value);
        return this;
    }

    public ElementStyle getStyle() {
        return element.getStyle();
    }

    public boolean isDraggable() {
        return element.isDraggable();
    }

    public int getDragX() { return element.getDragX(); }

    public int getDragY() { return element.getDragY(); }

    public void setDragOffset(int dragX, int dragY) {
        element.setDragOffset(dragX, dragY);
    }

    public void setDragShare(float shareX, float shareY) {
        element.setDragShare(shareX, shareY);
    }

    public float getDragShareX() { return element.getDragShareX(); }

    public float getDragShareY() { return element.getDragShareY(); }

    public boolean isDragged() { return element.isDragged(); }

    public int getScrollX() { return element.getScrollX(); }

    public int getScrollY() { return element.getScrollY(); }

    public void setScrollOffset(int scrollX, int scrollY) {
        element.setScrollOffset(scrollX, scrollY);
    }

    public <T> T getStyleValue(ElementStyle.Key<T> key) {
        return element.getStyle().get(key);
    }

    /** This element's own styles, with the inherited ones filled in from up the tree. */
    public ElementStyle getResolvedStyle() {
        ElementStyle resolved = element.getStyle();
        for (DOMTreeElement ancestor = parent; ancestor != null; ancestor = ancestor.parent) {
            resolved = resolved.inheritFrom(ancestor.getStyle());
        }
        return resolved;
    }

    public DOMTreeElement append(DOMTreeNode child) {
        if (!element.hasChildren) throw new IllegalStateException("Element <" + getTag() + "> cannot hold children");
        child.detach();
        child.parent = this;
        children.add(child);
        touched();
        return this;
    }

    public List<DOMTreeNode> getChildren() { return Collections.unmodifiableList(children); }

    /** The text this element holds directly, which is the text property and its text node children. */
    public List<DOMTreeText> getTextNodes() {
        List<DOMTreeText> result = new ArrayList<>();
        for (DOMTreeNode c : children) if (c instanceof DOMTreeText t) result.add(t);
        return result;
    }

    public List<DOMTreeElement> getChildElements() {
        List<DOMTreeElement> result = new ArrayList<>();
        for (DOMTreeNode c : children) if (c instanceof DOMTreeElement e) result.add(e);
        return result;
    }

    public List<DOMTreeElement> find(String tagName) {
        List<DOMTreeElement> result = new ArrayList<>();
        for (DOMTreeNode c : children) {
            if (c instanceof DOMTreeElement e) {
                if (e.getTag().equals(tagName)) result.add(e);
                result.addAll(e.find(tagName));
            }
        }
        return result;
    }

    public DOMTreeElement findFirst(String tagName) {
        for (DOMTreeNode c : children) {
            if (c instanceof DOMTreeElement e) {
                if (e.getTag().equals(tagName)) return e;
                DOMTreeElement nested = e.findFirst(tagName);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    public List<DOMTreeElement> findByProperty(String name, Object value) {
        List<DOMTreeElement> result = new ArrayList<>();
        for (DOMTreeNode c : children) {
            if (c instanceof DOMTreeElement e) {
                Object held = e.getPropertyValue(name);
                if (held != null && held.equals(value)) result.add(e);
                result.addAll(e.findByProperty(name, value));
            }
        }
        return result;
    }

    public void walk(Consumer<DOMTreeNode> visitor) {
        visitor.accept(this);
        for (DOMTreeNode c : children) {
            if (c instanceof DOMTreeElement e) e.walk(visitor);
            else visitor.accept(c);
        }
    }

    @Override
    public String render(String indent) {
        StringBuilder sb = new StringBuilder(indent).append("<").append(getTag());
        element.propertyList.getAssignedProperties().forEach((k, v) ->
                sb.append(" ").append(k).append("=\"").append(v.getValue()).append("\""));
        Map<String, Object> styles = element.getStyle().getAssignedStyles();
        if (!styles.isEmpty()) {
            sb.append(" style=\"");
            styles.forEach((k, v) -> sb.append(k).append(":").append(v).append(";"));
            sb.append("\"");
        }
        if (children.isEmpty()) return sb.append("/>\n").toString();
        sb.append(">\n");
        for (DOMTreeNode c : children) sb.append(c.render(indent + "  "));
        return sb.append(indent).append("</").append(getTag()).append(">\n").toString();
    }
}