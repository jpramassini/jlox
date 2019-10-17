package com.craftinginterpreters.lox;

import java.util.List;

class LoxFunction implements LoxCallable {
    private final Stmt.Function declaration;
    private final Environment closure;

    LoxFunction(Stmt.Function declaration, Environment closure) {
        this.closure = closure;
        this.declaration = declaration;
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
            environment.define(declaration.params.get(i), arguments.get(i)); // map param names to arguments passed in environment.
        }

        try {
            interpreter.executeBlock(declaration.body, environment);
        } catch(Return returnValue){    // This allows us to unwind the stack and toss out the rest of the function.
            return returnValue.value;
        }
        return null;
    }

    @Override
    public String toString() {
        return "<fn " + declaration.name.lexeme + ">";  // Since funcs are first-class values, they could be referenced in something like print.
    }
}
