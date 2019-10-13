package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.Map;

public class Environment {
    private final Map<String, Object> values = new HashMap<>();

    Object get(Token name){                     // Note: There are also some nuances here. Treating this as a runtime error allows
        if(values.containsKey(name.lexeme)){    // for more flexible recursive declarations.
            return values.get(name.lexeme);
        }

        throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "'.");
    }

    void define(String name, Object value){     // Note: The choice to not check for existing name -> value mappings here
        values.put(name, value);                // allows for the use of var a = "something" to be used to reassign an existing value.
    }                                           // This choice was made to make using the REPL more ergonomic.

}
