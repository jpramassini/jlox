package com.craftinginterpreters.lox;

import com.sun.tools.corba.se.idl.constExpr.Minus;

import javax.smartcardio.CommandAPDU;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.craftinginterpreters.lox.TokenType.*;

// TODO: Add support for block comments. This will require handling newlines. Consider how nesting might work.

class Scanner {
    // Store raw source code as a String.
    private final String source;
    private final List<Token> tokens = new ArrayList<>();

    // Map of keywords for checking later.
    private static final Map<String, TokenType> keywords;

    /*
        This is where keywords are defined. If you want to customize them (i.e. capitalize something, like booleans
        in Python (true => True), you can do it here. New additions go here as well (like maybe some more standard
        library functions!)
     */
    static {
        keywords = new HashMap<>();
        keywords.put("and",     AND);
        keywords.put("class",   CLASS);
        keywords.put("else",    ELSE);
        keywords.put("false",   FALSE);
        keywords.put("for",     FOR);
        keywords.put("fun",     FUN);
        keywords.put("if",      IF);
        keywords.put("nil",     NIL);
        keywords.put("or",      OR);
        keywords.put("print",   PRINT);
        keywords.put("return",  RETURN);
        keywords.put("super",   SUPER);
        keywords.put("this",    THIS);
        keywords.put("true",    TRUE);
        keywords.put("var",     VAR);
        keywords.put("while",   WHILE);
    }

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
            case '!': addToken(match('=') ? BANG_EQUAL : BANG); break;
            case '=': addToken(match('=') ? EQUAL_EQUAL : EQUAL); break;
            case '<': addToken(match('=') ? LESS_EQUAL : LESS); break;
            case '>': addToken(match('=') ? GREATER_EQUAL : GREATER); break;
            case '/':
                if (match('/')) {   // This indicates a comment, which we don't care about.
                    while(peek() != '\n' && !isAtEnd()) advance();
                } else {
                    addToken(SLASH);    // Not succeeded by another slash, so this means we actually care about this.
                }
                break;
            case ' ':
            case '\r':
            case '\t':
                break;  // Ignoring whitespace
            case '\n':
                line++; // Keeping our line count accurate!
                break;
            case '"': string(); break; // A " signifies the beginning of a string.

            default:
                if(isDigit(c)){ // Any numerical digit is valid here
                    number();
                } else if(isAlpha(c)) {
                    identifier();
                } else {
                    Lox.error(line, "Unexpected character.");
                }
                break;
        }
    }

    private void identifier(){
        while(isAlphaNumeric(peek())) advance();
        // Check if identifier is a reserved word.
        String text = source.substring(start, current);
        TokenType type = keywords.get(text);
        if(type == null) type = IDENTIFIER;
        addToken(type);
    }

    private void number() {
        while(isDigit(peek())) advance();  // Keep on trucking while the next thing is also a digit.

        // Look for decimals...
        if(peek() == '.' && isDigit(peekNext())){   // If another digit is not next, then stop.
            // Consume '.'
            advance();

            while (isDigit(peek())) advance();
        }

        addToken(NUMBER, Double.parseDouble(source.substring(start,current)));
        // This is fine since we only have a number type rather than Int, Double, etc.
    }

    private void string() {
        while(peek() != '"' && !isAtEnd()){
            if(peek() == '\n') line++;  // This line means multi-line strings ARE supported by Lox.
            advance();
        }

        if(isAtEnd()){  // Uh oh, somebody forgot a closing quote.
            Lox.error(line, "Unterminated string.");
        }

        advance(); // To get closing ".

        String value = source.substring(start + 1, current - 1); // store value with quotes stripped off.
        addToken(STRING, value);
    }

    // For differentiating between single and double char operators.
    private boolean match(char expected) {
        if (isAtEnd()) return false;
        // No match! return false and don't advance. The next char is part of a different token.
        if (source.charAt(current) != expected) return false;

        // Match. Advance past next char, as it's part of this token
        current++;
        return true;
    }

    private char peek(){    // This is a lookahead, not how we never call advance() here.
        if(isAtEnd()) return '\0';
        return source.charAt(current);
    }

    private char peekNext(){
        if(current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
    }

    private boolean isAlpha(char c){
        return (c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                c == '_';
    }

    private boolean isAlphaNumeric(char c){
        return isAlpha(c) || isDigit(c);
    }

    private boolean isDigit(char c){
        return c >= '0' && c <= '9';
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
