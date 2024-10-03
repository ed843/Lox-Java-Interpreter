package com.ericduncandev.lox;

import java.util.HashMap;
import java.util.Map;

class Environment {
    final Environment enclosing;
    private final Map<String, Object> values = new HashMap<>();
    private boolean DEBUG = false;

    Environment(boolean DEBUG) {
        this.DEBUG = DEBUG;
        enclosing = null;
    }

    Environment(Environment enclosing, boolean DEBUG) {
        this.enclosing = enclosing;
        this.DEBUG = DEBUG;
    }

    void define(String name, Object value) {
        if (DEBUG) {
            System.out.println("Debug: Defining " + name + " with value " + value + " in environment " + this);
        }
        values.put(name, value);
    }


    Environment ancestor(int distance) {
        Environment environment = this;
        for (int i = 0; i < distance; i++) {
            environment = environment.enclosing;
        }

        return environment;
    }

    Object getAt(int distance, String name) {
        return ancestor(distance).values.get(name);
    }

    void assignAt(int distance, Token name, Object value) {
        ancestor(distance).values.put(name.lexeme, value);
    }

    Object get(Token name) {
        if (values.containsKey(name.lexeme)) {
            if (DEBUG) {
                System.out.println("Debug: Found " + name.lexeme + " in environment " + this);
            }
            return values.get(name.lexeme);
        }

        if (enclosing != null) {
            if (DEBUG) {
                System.out.println("Debug: Looking for " + name.lexeme + " in enclosing environment " + enclosing);
            }
            return enclosing.get(name);
        }
        if (DEBUG) {
            System.out.println("Debug: Variable " + name.lexeme + " not found in any environment");
        }
        throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "'.");
    }

    public Map<String, Object> getValues() {
        return new HashMap<>(values);
    }

    void assign(Token name, Object value) {
        if (values.containsKey(name.lexeme)) {
            values.put(name.lexeme, value);
            return;
        }

        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }

        throw new RuntimeError(name,
                "Undefined variable '" + name.lexeme + "'.");
    }
}
