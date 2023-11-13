package com.raja.codelfow;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class Node {
    private String name;
    private boolean isComponent;
    private List<Node> referencedFrom;
    private boolean test;
    private boolean controller;
    private boolean config;
    private boolean interfaceImpl;
    private boolean isInterface;
    private boolean repository;
    private List<Node> inheritsFrom;
    private boolean pubsub;

    Node(
            String name,
            boolean isComponent,
            List<Node> referencedFrom,
            boolean test,
            boolean controller,
            boolean config,
            boolean interfaceImpl,
            boolean isInterface,
            boolean repository
    ) {
        this.name = name;
        this.isComponent = isComponent;
        this.referencedFrom = referencedFrom;
        this.test = test;
        this.controller = controller;
        this.config = config;
        this.interfaceImpl = interfaceImpl;
        this.isInterface = isInterface;
        this.repository = repository;
        this.inheritsFrom = new ArrayList<>();
        this.pubsub = false;
    }

    public static Node newComponent(String name) {
        return new Node(name, true, new ArrayList<>(), false, false, false, false, false, false);
    }

    public static Node newClass(String name) {
        return new Node(name, false, new ArrayList<>(), false, false, false, false, false, false);
    }

    public static Node newTestClass(String name) {
        return new Node(name, false, new ArrayList<>(), true, false, false, false, false, false);
    }

    public void referencedFrom(Node node) {
        if (!referencedFrom.contains(node)) {
            referencedFrom.add(node);
        }
    }

}
