package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.Map;

public class Environment {
    final Environment enclosing;
    private final Map<String, Object> values = new HashMap<>();

    Environment() {         // Note: We might not have an enclosing scope, so this needs to be nullable.
        enclosing = null;
    }

    Environment(Environment enclosing){         // Note: Overloaded constructor supports optional enclosing scope.
        this.enclosing = enclosing;             // This also allows for chaining of multiple nested Environments to an arbitrary depth.
    }

    Object get(Token name){                     // Note: There are also some nuances here. Treating this as a runtime error allows
        if(values.containsKey(name.lexeme)){    // for more flexible recursive declarations.
            return values.get(name.lexeme);
        }

        if(enclosing != null) return enclosing.get(name);   // Nothing in inner scope, so we'll check in the next highest.
                                                            // This will bubble up all the way until we (hopefully) find a valid entry.
        throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "'.");
    }

    void assign(Token name, Object value) {     // Note: The way this function is defined doesn't allow implicit variable declaration.
        if(values.containsKey(name.lexeme)){    // If this function added the variable if the key didn't already exist, this would be possible.
            values.put(name.lexeme, value);
            return;
        }

        if(enclosing != null){                  // Same general pattern here as in get(). If there's no valid assignment target
            enclosing.assign(name, value);      // in this scope, try and do it on the next environment up.
            return;
        }

        throw new RuntimeError(name, "Undefined variable '" + name.lexeme +"'.");
    }

    // The string version of this call is used for adding new native functions.
    void defineInternal(String name, Object value){
        values.put(name, value);
    }

    // No longer allow redefinition to prevent argument reassignment.
    void define(Token name, Object value) {
        if(values.containsKey(name.lexeme)) throw new RuntimeError(name, "Variable '" + name.lexeme + "' already defined in this scope.");
        values.put(name.lexeme, value);
    }

    Environment ancestor(int distance){
        Environment environment = this;
        for(int i = 0; i < distance; i++){      // Hop up the chain until we reach appropriate distance.
            environment = environment.enclosing;
        }

        return environment;
    }

    Object getAt(int distance, String name){
        return ancestor(distance).values.get(name);
    }

    void assignAt(int distance, Token name, Object value){
        ancestor(distance).values.put(name.lexeme, value);
    }
}
