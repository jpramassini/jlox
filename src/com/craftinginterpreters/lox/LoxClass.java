package com.craftinginterpreters.lox;

import java.util.List;
import java.util.Map;

public class LoxClass implements LoxCallable{
    final String name;
    final LoxClass superclass;
    private final Map<String, LoxFunction> methods;

    LoxClass(String name, LoxClass superclass, Map<String, LoxFunction> methods){
        this.name = name;
        this.superclass = superclass;
        this.methods = methods;
    }

    LoxFunction findMethod(String name){
        if(methods.containsKey(name)) return methods.get(name);
        if(superclass != null) return superclass.findMethod(name);  // Not in this class, so kick it up to the super if it exists.
        return null;
    }

    @Override
    public String toString(){
        return name;
    }

    @Override
    public int arity() {
        LoxFunction initializer = findMethod("init");
        if(initializer == null) return 0;
        return initializer.arity();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        LoxInstance instance = new LoxInstance(this);   // Allocate space and grab reference to new object.
        LoxFunction initializer = findMethod("init");
        if(initializer != null) initializer.bind(instance).call(interpreter, arguments);    // Run user specified constructor.
        return instance;
    }
}
