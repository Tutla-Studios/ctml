package net.tutla.craftytml.client.ctml;

import net.tutla.craftytml.client.tusan.CTMLTokenType;
import net.tutla.tums.tusan.TusanContext;

public class ElementProperty {
    public final String name;
    public final ElementPropertyType type;
    public final CTMLTokenType tokenType;

    private Object value;

    public ElementProperty(String name, ElementPropertyType type, CTMLTokenType tokenType){
        this.name = name;
        this.type = type;
        this.tokenType = tokenType;
    }

    public void setValue(Object val, TusanContext ctx){
        if (type == ElementPropertyType.INTEGER && !(val instanceof Integer)){
            ctx.getInterpreter().error("UnexpectedValue", "Expected integer value for property: "+name+"; got"+val.getClass(), null);
        } else if (type == ElementPropertyType.STRING && !(val instanceof String)){
            ctx.getInterpreter().error("UnexpectedValue", "Expected string value for property: "+name+"; got"+val.getClass(), null);
        } else if (type == ElementPropertyType.BOOLEAN && !(val instanceof Boolean)){
            ctx.getInterpreter().error("UnexpectedValue", "Expected boolean value for property: "+name+"; got"+val.getClass(), null);
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

    public boolean hasValue(){
        return value != null;
    }
}
