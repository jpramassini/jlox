package com.craftinginterpreters.lox;

import com.sun.tools.corba.se.idl.constExpr.Minus;

import javax.smartcardio.CommandAPDU;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.craftinginterpreters.lox.TokenType.*;

class Scanner {
    // Store raw source code as a String.
    private final String source;
    private final List<Token> tokens = new ArrayList<>();

    // Ints to keep track of current position in source.
    private int start = 0; // Position of first character of lexeme being scanned.
    private int current = 0; // Position of current lexeme being examined.
    private int line = 1;

    Scanner(String source){
        this.source = source;
    }

    List<Token> scanTokens(){
        // Not at end of file, keep on scanning tokens.
        while(!isAtEnd()){
            start = current;
            scanToken();
        }

        // Made it to the end, tack on an EOF token.
        // Unnecessary, but makes parsing cleaner.
        tokens.add(new Token(EOF, "", null, line));
        return tokens;
    }

    private void scanToken(){
        char c = advance();
        switch(c) {
            case '(': addToken(LEFT_PAREN); break;
            case ')': addToken(RIGHT_PAREN); break;
            case '{': addToken(LEFT_BRACE); break;
            case '}': addToken(RIGHT_BRACE); break;
            case ',': addToken(COMMA); break;
            case '.': addToken(DOT); break;
            case '-': addToken(MINUS); break;
            case '+': addToken(PLUS); break;
            case ';': addToken(SEMICOLON); break;
            case '*': addToken(STAR); break;
        }
    }

    private boolean isAtEnd(){
        return current >= source.length();
    }

    private char advance(){
        current++;
        return source.charAt(current-1);
    }

    private void addToken(TokenType type){
        addToken(type, null);
    }

    private void addToken(TokenType type, Object literal){
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line));
    }
}
