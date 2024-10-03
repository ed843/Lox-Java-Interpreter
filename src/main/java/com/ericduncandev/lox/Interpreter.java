package com.ericduncandev.lox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Interpreter implements Expr.Visitor<Object>,
        Stmt.Visitor<Void> {

    private static Object uninitialized = new Object();
    private boolean DEBUG = false;
    final Environment globals;
    Environment environment;
    private final Map<Expr, Integer> locals = new HashMap<>();
    private final ModuleSystem moduleSystem;


    Interpreter(boolean DEBUG) {
        this.DEBUG = DEBUG;
        globals = new Environment(DEBUG);
        environment = globals;
        moduleSystem = new ModuleSystem(this, DEBUG);
        globals.define("clock", new LoxCallable() {
            @Override
            public int arity() { return 0; }

            @Override
            public Object call(Interpreter interpreter, Token callToken, List<Object> arguments) {
                return (double)System.currentTimeMillis() / 1000.0;
            }

            @Override
            public String toString() { return "<native fn>"; }
        });
        globals.define("error", new LoxCallable() {
            @Override
            public int arity() { return 1; }

            @Override
            public Object call(Interpreter interpreter, Token callToken, List<Object> arguments) {
                if (arguments.isEmpty()) {
                    throw new RuntimeError(callToken, "Error thrown with no message.");
                } else {
                    throw new RuntimeError(callToken, stringify(arguments.get(0)));
                }
            }

            @Override
            public String toString() { return "<native fn>"; }
        });
        globals.define("len", new LoxCallable() {
            public int arity() { return 1; }

            public Object call(Interpreter interpreter, Token callToken, List<Object> arguments) {
               if (arguments.get(0) instanceof Double) {
                   throw new RuntimeError(callToken, "Arguments to 'len' must be a string.");
               } else if (arguments.get(0) instanceof List) {
                   return ((List<?>)arguments.get(0)).size();
               }
               else {
                   return stringify(arguments.get(0)).length();
               }
            }

            @Override
            public String toString() { return "<native fn>"; }
        });
        // TODO: ADD MORE NATIVE FUNCTIONS
    }

    void interpret(List<Stmt> statements) {
        try {
            for (Stmt statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }

    String interpret(Expr expression) {
        try {
            Object value = evaluate(expression);
            return stringify(value);
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
            return null;
        }
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);

        if (expr.operator.type == TokenType.OR) {
            if (isTruthy(left)) return left;
        } else {
            if (!isTruthy(left)) return left;
        }

        return evaluate(expr.right);
    }

    @Override
    public Object visitSetExpr(Expr.Set expr) {
        Object object = evaluate(expr.object);

        if (!(object instanceof LoxInstance)) {
            throw new RuntimeError(expr.name,
                    "Only instances have fields.");
        }

        Object value = evaluate(expr.value);
        ((LoxInstance)object).set(expr.name, value);
        return value;
    }

    @Override
    public Object visitThisExpr(Expr.This expr) {
        return lookUpVariable(expr.keyword, expr);
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case BANG:
                return !isTruthy(right);
            case MINUS:
                checkNumberOperand(expr.operator, right);
                return -(double) right;
        }

        // Unreachable.
        return null;
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return lookUpVariable(expr.name, expr);
    }

    private Object lookUpVariable(Token name, Expr expr) {
        Integer distance = locals.get(expr);
        if (distance != null) {
            if(DEBUG) {
                System.out.println("Debug: Looking up local variable " + name.lexeme + " at distance " + distance);
            }
            return environment.getAt(distance, name.lexeme);
        } else {
            if(DEBUG) {
                System.out.println("Debug: Looking up global variable " + name.lexeme);
            }
            return globals.get(name);
        }
    }

    @Override
    public Object visitConditionalExpr(Expr.Conditional expr) {
        Object expression = evaluate(expr.expr);

        if (isTruthy(expression)) {
            return evaluate(expr.thenBranch);
        }

        return evaluate(expr.elseBranch);
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) return;
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    private void checkNumberOperands(Token operator,
                                     Object left, Object right) {
        if (left instanceof Double && right instanceof Double) return;

        throw new RuntimeError(operator, "Operands must be numbers.");
    }

    private boolean isTruthy(Object object) {
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean) object;
        return true;
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;

        return a.equals(b);
    }

    private String stringify(Object object) {
        if (object == null) return "nil";

        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }

        if (object instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) object;
            StringBuilder builder = new StringBuilder();
            builder.append("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) builder.append(", ");
                builder.append(stringify(list.get(i)));
            }
            builder.append("]");
            return builder.toString();
        }

        // in the case that a variable is declared but not assigned, return nil
        // could throw [RuntimeError] instead, but it is a pain to find the token
        if (object.toString().startsWith("java.lang.Object")) {
            return "nil";
        }

        return object.toString();
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    void resolve(Expr expr, int depth) {
        locals.put(expr, depth);
    }

    void executeBlock(List<Stmt> statements, Environment environment) {
        Environment previous = this.environment;
        try {
            this.environment = environment;
            for (Stmt statement : statements) {
                execute(statement);
            }
        } finally {
            this.environment = previous;
        }
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment, DEBUG));
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        environment.define(stmt.name.lexeme, null);
        Map<String, LoxFunction> classMethods = new HashMap<>();
        for (Stmt.Function method : stmt.classMethods) {
            LoxFunction function = new LoxFunction(method, environment, false, null, DEBUG);
            classMethods.put(method.name.lexeme, function);
        }

        LoxClass metaclass = new LoxClass(null,
                stmt.name.lexeme + " metaclass", classMethods);

        Map<String, LoxFunction> methods = new HashMap<>();
        for (Stmt.Function method : stmt.methods) {
            LoxFunction function = new LoxFunction(method, environment,
                    method.name.lexeme.equals("init"), null, DEBUG);
            methods.put(method.name.lexeme, function);
        }

        LoxClass klass = new LoxClass(metaclass, stmt.name.lexeme, methods);
        environment.assign(stmt.name, klass);
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        LoxFunction function = new LoxFunction(stmt, environment,
                false, null, DEBUG);
        environment.define(stmt.name.lexeme, function);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitImportStmt(Stmt.Import stmt) {
        String path = (String) stmt.path.literal;
        try {
            moduleSystem.importModule(path);
        } catch (IOException e) {
            throw new RuntimeError(stmt.keyword, "Could not import module '" + path + "'.");
        }
        return null;
    }

    public void defineVariable(String name, Object value) {
        environment.define(name, value);
    }

    @Override
    public Void visitExportStmt(Stmt.Export stmt) {
        execute(stmt.declaration);
        if (stmt.declaration instanceof Stmt.Var) {
            Stmt.Var varStmt = (Stmt.Var) stmt.declaration;
            Object value = environment.get(varStmt.name);
            moduleSystem.exportDeclaration(varStmt.name.lexeme, value);
        } else if (stmt.declaration instanceof Stmt.Function) {
            Stmt.Function funcStmt = (Stmt.Function) stmt.declaration;
            Object value = environment.get(funcStmt.name);
            moduleSystem.exportDeclaration(funcStmt.name.lexeme, value);
        }
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object value = null;
        if (stmt.value != null) value = evaluate(stmt.value);

        throw new Return(value);
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = uninitialized;
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }

        environment.define(stmt.name.lexeme, value);
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        try {
            while (isTruthy(evaluate(stmt.condition))) {
                execute(stmt.body);
            }
        } catch (BreakException ex) {
            // Do nothing.
        }
        return null;
    }

    @Override
    public Void visitBreakStmt(Stmt.Break stmt) {
        throw new BreakException();
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);
        Integer distance = locals.get(expr);
        if (distance != null) {
            environment.assignAt(distance, expr.name, value);
        } else {
            globals.assign(expr.name, value);
        }
        return value;
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double) left > (double) right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double) left >= (double) right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double) left < (double) right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double) left <= (double) right;
            case BANG_EQUAL:
                return !isEqual(left, right);
            case EQUAL_EQUAL:
                return isEqual(left, right);
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double) left - (double) right;
            case PLUS:
                if (left instanceof String || right instanceof String) {
                    return stringify(left) + stringify(right);
                }

                if (left instanceof Double && right instanceof Double) {
                    return (double) left + (double) right;
                }

                throw new RuntimeError(expr.operator,
                        "Operands must be two numbers or two strings.");
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                if ((double) right == 0) {
                    throw new RuntimeError(expr.operator, "Attempted to divide by zero, which is not allowed.");
                }
                return (double) left / (double) right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double) left * (double) right;
            case PERCENT:
                checkNumberOperands(expr.operator, left, right);
                return (double) left % (double) right;
            case BAR:
                checkNumberOperands(expr.operator, left, right);
                int leftInt = (int) ((Double) left).doubleValue();
                int rightInt = (int) ((Double) right).doubleValue();
                int intResult = leftInt | rightInt;
                double result = (double) intResult;
                return result;
            case XOR:
                checkNumberOperands(expr.operator, left, right);
                int leftInt1 = (int) ((Double) left).doubleValue();
                int rightInt1 = (int) ((Double) right).doubleValue();
                int intResult1 = leftInt1 ^ rightInt1;
                double result1 = (double) intResult1;
                return result1;

        }

        // Unreachable.
        return null;
    }

    @Override
    public Object visitCallExpr(Expr.Call expr) {
        Object callee = evaluate(expr.callee);

        List<Object> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments) {
            arguments.add(evaluate(argument));
        }

        if (!(callee instanceof LoxCallable)) {
            throw new RuntimeError(expr.paren,
                    "Can only call functions and classes.");
        }

        LoxCallable function = (LoxCallable)callee;

        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren, "Expected " +
                    function.arity() + " arguments but got " +
                    arguments.size() + ".");
        }

        return function.call(this, expr.paren, arguments);
    }

    @Override
    public Object visitGetExpr(Expr.Get expr) {
        Object object = evaluate(expr.object);
        if (object instanceof LoxInstance) {
            Object result = ((LoxInstance) object).get(expr.name);
            if (result instanceof LoxFunction &&
                    ((LoxFunction) result).isGetter()) {
                result = ((LoxFunction) result).call(this, expr.name, null);
            }

            return result;
        }

        throw new RuntimeError(expr.name,
                "Only instances have properties.");
    }

    @Override
    public Object visitArrayExpr(Expr.Array expr) {
        List<Object> elements = new ArrayList<>();
        for (Expr element : expr.elements) {
            Object value = evaluate(element);
            elements.add(value);
            if (DEBUG) {
                System.out.println("Debug: Added element to array: " + stringify(value));
            }
        }
        if (DEBUG) {
            System.out.println("Debug: Created array: " + stringify(elements));
        }
        return elements;
    }

    @Override
    public Object visitArrayAccessExpr(Expr.ArrayAccess expr) {
        Object target = evaluate(expr.array);
        Object index = evaluate(expr.index);

        if (DEBUG) {
            System.out.println("Debug: Array/String access - Target: " + stringify(target) + ", Index: " + stringify(index));
        }

        if (!(index instanceof Double)) {
            throw new RuntimeError(expr.bracket, "Index must be a number.");
        }

        int intIndex = ((Double) index).intValue();

        if (target instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) target;

            if (intIndex < 0 || intIndex >= list.size()) {
                throw new RuntimeError(expr.bracket, "Array index out of bounds.");
            }

            Object result = list.get(intIndex);
            if (DEBUG) {
                System.out.println("Debug: Array access result: " + stringify(result));
            }
            return result;
        } else if (target instanceof String) {
            String str = (String) target;

            if (intIndex < 0 || intIndex >= str.length()) {
                throw new RuntimeError(expr.bracket, "String index out of bounds.");
            }

            String result = String.valueOf(str.charAt(intIndex));
            if (DEBUG) {
                System.out.println("Debug: String access result: " + stringify(result));
            }
            return result;
        } else {
            throw new RuntimeError(expr.bracket, "Can only index into arrays or strings.");
        }
    }

    private void checkArrayOperand(Token operator, Object operand) {
        if (operand instanceof List) return;
        throw new RuntimeError(operator, "Operand must be an array.");
    }

    private void checkArrayIndexOperand(Token operator, Object operand) {
        if (operand instanceof Double) return;
        throw new RuntimeError(operator, "Array index must be a number.");
    }

    private static class BreakException extends RuntimeException {
    }

    class UnimplementedException extends RuntimeException {
        UnimplementedException(String message) {
            super(message);
        }
    }
}


