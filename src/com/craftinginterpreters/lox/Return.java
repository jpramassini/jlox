package com.craftinginterpreters.lox;

public class Return extends RuntimeException{
    final Object value;

    Return(Object value) {
        super(null, null, false, false);    // disable some jvm stuff. (message, cause, enableSuppression, writableStackTrace)
        this.value = value;                 // Note: Since this is for control flow, we don't need stack traces, etc.
    }
}
