package net.tutla.craftytml.client.dom;

/**
 * A run of text sitting in the tree in its own right, so it can come between two elements
 * the way it does in html. It carries no style of its own: text takes everything from the
 * element around it, which is what css inheritance already does.
 */
public class DOMTreeText extends DOMTreeNode {
    private String text;

    public DOMTreeText(String text){
        this.text = text == null ? "" : text;
    }

    public String getText(){
        return text;
    }

    public void setText(String text){
        String value = text == null ? "" : text;
        if (value.equals(this.text)) return;
        this.text = value;
        touched();
    }

    @Override
    public String render(String indent){
        return text.isBlank() ? "" : indent + text + "\n";
    }
}
