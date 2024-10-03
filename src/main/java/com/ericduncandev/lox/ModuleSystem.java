package com.ericduncandev.lox;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModuleSystem {
    private final Map<String, Environment> modules = new HashMap<>();
    private final Interpreter interpreter;
    private boolean DEBUG = false;

    public ModuleSystem(Interpreter interpreter, boolean DEBUG) {
        this.interpreter = interpreter;
        this.DEBUG = DEBUG;
    }

    public void importModule(String path) throws IOException {
        if (!modules.containsKey(path)) {
            // Load and parse the file
            String source = new String(Files.readAllBytes(Paths.get(path)), Charset.defaultCharset());
            Scanner scanner = new Scanner(source);
            List<Token> tokens = scanner.scanTokens();
            Parser parser = new Parser(tokens, false);
            List<Stmt> statements = parser.parse();

            // Create a new environment for this module
            Environment moduleEnv = new Environment(interpreter.globals, DEBUG);

            Resolver resolver = new Resolver(interpreter, DEBUG);
            resolver.resolve(statements);

            // Execute the module in its own environment
            interpreter.interpret(statements);


            // Store the module's environment
            modules.put(path, moduleEnv);
        }

        // Add the module's exported variables to the current environment
        Environment moduleEnv = modules.get(path);
        for (Map.Entry<String, Object> entry : moduleEnv.getValues().entrySet()) {
            if (entry.getValue() instanceof LoxFunction) {
                LoxFunction function = (LoxFunction) entry.getValue();
                // Create a new function with the module's environment
                LoxFunction newFunction = new LoxFunction(
                        function.declaration,
                        function.closure,
                        function.isInitializer,
                        moduleEnv, // Pass the module's environment
                        DEBUG
                );
                interpreter.defineVariable(entry.getKey(), newFunction);
            }
            else {
                interpreter.defineVariable(entry.getKey(), entry.getValue());
            }
        }
    }

    public void exportDeclaration(String name, Object value) {
        interpreter.defineVariable(name, value);
    }
}