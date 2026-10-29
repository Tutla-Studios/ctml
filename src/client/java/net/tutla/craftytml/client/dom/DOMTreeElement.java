package net.tutla.craftytml.client.dom;


import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.ElementProperty;
import net.tutla.tums.tusan.TusanContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class DOMTreeElement extends DOMTreeNode {
    private final CTMLElement element;
    final List<DOMTreeNode> children = new ArrayList<>();

    public DOMTreeElement(CTMLElement element) { this.element = element; }

    public CTMLElement getElement() { return element; }

    public String getTag() { return element.name; }

    public DOMTreeElement property(String name, Object value, TusanContext ctx) {
        element.propertyList.updateProperty(name, value, ctx);
        return this;
    }

    public ElementProperty getProperty(String name) {
        return element.propertyList.propertyFromName(name);
    }

    public Object getPropertyValue(String name) {
        ElementProperty property = getProperty(name);
        return property == null ? null : property.getValue();
    }

    public DOMTreeElement append(DOMTreeNode child) {
        if (!element.hasChildren) throw new IllegalStateException("Element <" + getTag() + "> cannot hold children");
        child.detach();
        child.parent = this;
        children.add(child);
        return this;
    }

    public List<DOMTreeNode> getChildren() { return Collections.unmodifiableList(children); }

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
    public void draw() {
        //element.draw(this);
        for (DOMTreeNode c : children) c.draw();
    }

    @Override
    public String render(String indent) {
        StringBuilder sb = new StringBuilder(indent).append("<").append(getTag());
        element.propertyList.getAssignedProperties().forEach((k, v) ->
                sb.append(" ").append(k).append("=\"").append(v.getValue()).append("\""));
        if (children.isEmpty()) return sb.append("/>\n").toString();
        sb.append(">\n");
        for (DOMTreeNode c : children) sb.append(c.render(indent + "  "));
        return sb.append(indent).append("</").append(getTag()).append(">\n").toString();
    }
}