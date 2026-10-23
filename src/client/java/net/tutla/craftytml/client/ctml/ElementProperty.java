package net.tutla.craftytml.client.ctml;

import net.tutla.tums.tusan.TusanContext;

public class ElementProperty {
    public static final String name = "";
    public static final ElementPropertyType type = ElementPropertyType.STRING;

    private Object value;

    public void setValue(Object val, TusanContext ctx){
        if (type == ElementPropertyType.INTEGER && !(val instanceof Integer)){
            ctx.getInterpreter().error("UnexpectedValue", "Expected integer value for property: "+name, null);
        } else if (type == ElementPropertyType.STRING && !(val instanceof String)){
            ctx.getInterpreter().error("UnexpectedValue", "Expected string value for property: "+name, null);
        } else if (type == ElementPropertyType.BOOLEAN && !(val instanceof Boolean)){
            ctx.getInterpreter().error("UnexpectedValue", "Expected boolean value for property: "+name, null);
        } else {
            value = val;
        }
    }

    public ElementProperty withValue(Object val, TusanContext ctx){
        setValue(val, ctx);
        return this;
    }

    public Object getValue(){
        return value;
    }
}
