package com.ericduncandev.lox;

import java.util.List;

class LoxFunction implements LoxCallable {
    final Stmt.Function declaration;
    final Environment closure;
    final boolean isInitializer;
    final Environment moduleEnvironment;
    private boolean DEBUG = false;

    LoxFunction(Stmt.Function declaration, Environment closure,
                boolean isInitializer, Environment moduleEnvironment, boolean DEBUG) {
        this.isInitializer = isInitializer;
        this.closure = closure;
        this.declaration = declaration;
        this.moduleEnvironment = moduleEnvironment;
        this.DEBUG = DEBUG;
    }

    LoxFunction bind(LoxInstance instance) {
        Environment environment = new Environment(closure, DEBUG);
        environment.define("this", instance);
        return new LoxFunction(declaration, environment, isInitializer, moduleEnvironment, DEBUG);
    }

    @Override
    public Object call(Interpreter interpreter, Token callToken, List<Object> arguments) {
        Environment environment = new Environment(closure, DEBUG);
        if(DEBUG) {
            System.out.println("Debug: Function environment: " + environment);
            System.out.println("Debug: Closure environment: " + closure);
            System.out.println("Debug: Module environment: " + moduleEnvironment);
            System.out.println("Debug: Calling function " + declaration.name.lexeme);
        }
        for (int i = 0; i < declaration.params.size(); i++) {
            environment.define(declaration.params.get(i).lexeme, arguments.get(i));
            if (DEBUG) {
                System.out.println("Debug: Defining parameter " + declaration.params.get(i).lexeme + " with value " + arguments.get(i));
            }
        }

        try {
            interpreter.executeBlock(declaration.body, environment);
        } catch (Return returnValue) {
            if (isInitializer) return closure.getAt(0, "this");
            return returnValue.value;
        }

        if (isInitializer) return closure.getAt(0, "this");
        return null;
    }

    public boolean isGetter() {
        return declaration.params == null;
    }

    @Override
    public int arity() {
        return declaration.params.size();
    }

    @Override
    public String toString() {
        return "<fn " + declaration.name.lexeme + ">";
    }
}
