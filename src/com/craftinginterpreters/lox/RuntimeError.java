package com.craftinginterpreters.lox;

public class RuntimeError extends RuntimeException{
    final Token token;

    RuntimeError(Token token, String message){
        super(message);
        // This token tracking will give users more usable information about the Lox error.
        // That is, the error will be in a Lox context rather than an interpreter context.
        this.token = token;
    }
}
