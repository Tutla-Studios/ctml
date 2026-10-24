package net.tutla.craftytml.client.dom;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DOMTreeElement extends DOMTreeNode {
    String tag;
    Map<String, String> attributes = new LinkedHashMap<>();
    List<DOMTreeNode> children = new ArrayList<>();

    DOMTreeElement(String tag) { this.tag = tag; }

    DOMTreeElement attr(String name, String value) {
        attributes.put(name, value);
        return this;
    }

    DOMTreeElement append(DOMTreeNode child) {
        if (child.parent != null) child.parent.children.remove(child);
        child.parent = this;
        children.add(child);
        return this;
    }

    //DOMTreeElement text(String t) { return append(new TextNode(t)); }

    List<DOMTreeElement> find(String tagName) {
        List<DOMTreeElement> result = new ArrayList<>();
        for (DOMTreeNode c : children) {
            if (c instanceof DOMTreeElement e) {
                if (e.tag.equals(tagName)) result.add(e);
                result.addAll(e.find(tagName));
            }
        }
        return result;
    }

    String render(String indent) {
        StringBuilder sb = new StringBuilder(indent).append("<").append(tag);
        attributes.forEach((k, v) -> sb.append(" ").append(k).append("=\"").append(v).append("\""));
        sb.append(">\n");
        for (DOMTreeNode c : children) sb.append(c.render(indent + "  "));
        return sb.append(indent).append("</").append(tag).append(">\n").toString();
    }
}