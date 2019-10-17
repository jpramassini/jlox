package com.craftinginterpreters.lox;

import java.util.List;

interface LoxCallable {
    int arity();
    // Pass in interpreter in case it's needed. The caller must return the result value of the expr.
    Object call(Interpreter interpreter, List<Object> arguments);
}
