package com.craftinginterpreters.lox;

import java.util.List;

class LoxFunction implements LoxCallable {
    private final Stmt.Function declaration;
    private final Environment closure;
    private final boolean isInitializer;

    LoxFunction(Stmt.Function declaration, Environment closure, boolean isInitializer) {
        this.closure = closure;
        this.declaration = declaration;
        this.isInitializer = isInitializer;
    }

    LoxFunction bind(LoxInstance instance){
        Environment environment = new Environment(closure);
        environment.define("this", instance);           // Inject instance reference into scope of function.
        return new LoxFunction(declaration, environment, isInitializer);
    }

    @Override
    public int arity() {
        return declaration.params.size();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        /* Note: This environment instantiation keeps function data encapsulated!!
           This also allows environments to be allocated dynamically on a PER CALL basis.
           Allocation at definition would cause data to be shared across calls, which is very bad.
        */
        Environment environment = new Environment(closure); // inject closure data for local function support
        for(int i = 0; i < declaration.params.size(); i++){
            environment.define(declaration.params.get(i).lexeme, arguments.get(i)); // map param names to arguments passed in environment.
        }

        try {
            interpreter.executeBlock(declaration.body, environment);
        } catch(Return returnValue){    // This allows us to unwind the stack and toss out the rest of the function.
            if(isInitializer) return closure.getAt(0, "this");  // In case of empty return in constructor, force return "this".
            return returnValue.value;
        }
        if(isInitializer) return closure.getAt(0, "this");  // If this is an initializer, forcibly return "this" instance.
        return null;
    }

    @Override
    public String toString() {
        return "<fn " + declaration.name.lexeme + ">";  // Since funcs are first-class values, they could be referenced in something like print.
    }
}
