package net.tutla.craftytml.client.tusan.register;

import net.tutla.craftytml.client.ctml.CTMLElement;
import net.tutla.craftytml.client.ctml.element.Division;
import net.tutla.craftytml.client.tusan.node.CTMLNode;

import java.util.ArrayList;
import java.util.HashMap;

public class NodeRegistry {
    private static final ArrayList<Class<? extends CTMLNode>> nodes = new ArrayList<>();
    private static final HashMap<Class<? extends CTMLNode>, CTMLElement> Node2ElementMap = new HashMap<>();

    public static void init(){
        //register(DivisionNode.class, new Division());
    }

    public static void register(Class<? extends CTMLNode> node, CTMLElement element){
        nodes.add(node);
        Node2ElementMap.put(node, element);
    }

    public static ArrayList<Class<? extends CTMLNode>> getRegister(){
        return nodes;
    }

    public static CTMLElement getElementFromNode(Class<? extends CTMLNode> node){
        return Node2ElementMap.get(node);
    }
}