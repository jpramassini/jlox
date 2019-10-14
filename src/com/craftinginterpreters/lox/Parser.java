package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;

public class Parser {

    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens){
        this.tokens = tokens;
    }

    List<Stmt> parse(){
        List<Stmt> statements = new ArrayList<>();
        while(!isAtEnd()) {
            statements.add(declaration());
        }

        return statements;
    }

    //  Through this section, the recursive nature of the parser will be much more clear

    //  In our grammar, the "expression" rule simply expands to the equality rule (it can't be anything else due to
    //  precedence!)
    private Expr expression() {
        return assignment();
    }



    private Stmt declaration() {
        try{
            if (match(VAR)) return varDeclaration();
            return statement();
        } catch (ParseError error){
            synchronize();
            return null;
        }
    }

    private Stmt statement() {
        if(match(PRINT)) return printStatement();
        if(match(LEFT_BRACE)) return new Stmt.Block(block());

        return expressionStatement();   // This is the "fallthrough" option, as it's pretty hard to tell something is an
    }                                   // expression statement based on a leading token.

    private Stmt printStatement() {
        Expr value = expression();
        consumeSemi();
        return new Stmt.Print(value);
    }

    private Stmt varDeclaration(){
        // Var has already been matched, so go ahead and grab the var name.
        Token name = consume(IDENTIFIER, "Expect variable name.");
        Expr initializer = null;
        // Parse the initializer if an '=' is present. This branching allows initialization without declaration.
        if(match(EQUAL)){
            initializer = expression();
        }

        consumeSemi("Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    private Stmt expressionStatement(){
        Expr expr = expression();
        consumeSemi();
        return new Stmt.Expression(expr);
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while(!check(RIGHT_BRACE) && !isAtEnd()) {      // Note: This explicit EOF check is necessary to avoid infinite loops when parsing.
            statements.add(declaration());              // If the user forgot a closing '}', we need to be defensive.
        }

        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    // Helper method to consume semicolons for statements.
    private Token consumeSemi(){
        return consume(SEMICOLON, "Expect ';' after value.");
    }

    // Overloaded version to allow for more specific semicolon error msgs.
    private Token consumeSemi(String message){
        return consume(SEMICOLON, message);
    }

    private Expr assignment(){
        Expr expr = equality(); // Note: First parse the left hand side in case there's other stuff that needs to be evaluated first.
                                // We can get away with this because all valid assignment targets are valid expressions on their own.
                                // This line also allows this to be done without a ton of looking ahead.
        if(match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();  // Note: assignment is RIGHT associative, which is why assignment is recursively called.
                                        //       This means that looping is unnecessary.
            if(expr instanceof Expr.Variable) {          // Note: We first check if the L value is a variable identifier before creating a
                Token name = ((Expr.Variable) expr).name;//       Variable node,
                return new Expr.Assign(name, value);
            }

            error(equals, "Invalid assignment target.");    // Note: this gets reported but not thrown to avoid triggering panic mode.
        }

        return expr;
    }

    // ******************** START OF BINARY EXPRESSIONS **********************
    // TODO: Write some unified code that could make some of this less redundant! These are left associative and could be made more uniform.

    private Expr equality() {
        Expr expr = comparison();   // This is the left token of the equality expr.
        /*
         Notes on this loop:
            - This seems weird, but it's important to catch multiple equality statements in a row, i.e. a == b == c
            - This loop solves the above situation by storing the last expression, the operator, and the newest right
              token in their own binary expressions. In this way we get a chain of binary equality expressions
              as long as necessary.
            - The while also ensures that if nothing matches, nothing pertaining to a specific type gets executed!
              Because each of these functions calls the next one first, we will always only tack on expressions
              if they match. (This is a really clever design.)
         */
        while(match(BANG_EQUAL, EQUAL_EQUAL)){  // Run until we hit something that isn't an equality operator
            // We saw a != or ==, which means that the prev token is the operator [because of advance()]!
            Token operator = previous();
            Expr right = comparison();
            // Create a new binary expression with expr (above), the operator we matched, and the next thing available
            expr = new Expr.Binary(expr, operator, right);

        }

        return expr;
    }

    // Note: This function is almost *exactly* the same as the previous.
    private Expr comparison(){
        Expr expr = addition();

        while(match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {    // These variable length arguments are real convenient
            Token operator = previous();
            Expr right = addition();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr addition() {
        Expr expr = multiplication();   // Same deal, calling the function of next highest precedence

        while(match(PLUS, MINUS)){
            Token operator = previous();
            Expr right = multiplication();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr multiplication(){
        Expr expr = unary();

        while(match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // ******************** END OF BINARY EXPRESSIONS **********************

    private Expr unary(){
        if(match(BANG, MINUS)){     // If this thing can be accurately considered a unary...
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return primary();           // Otherwise, pass it up the chain of precedence
    }

    // Single terminals. This means we're going to begin to actually start returning values.
    // This could also be cleaned up to not be so if-statement heavy.
    private Expr primary(){
        if(match(FALSE)) return new Expr.Literal(false);
        if(match(TRUE)) return new Expr.Literal(true);
        if(match(NIL)) return new Expr.Literal(null);

        if(match(NUMBER, STRING)){
            // These values have already been parsed into Java values by the scanner
            return new Expr.Literal(previous().literal);
        }

        if(match(IDENTIFIER)){
            return new Expr.Variable(previous());   // This is what allows the use of variables
        }

        if(match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expected expression.");
    }

    // Helper method to check if a given token matches any of a set of types.
    private boolean match(TokenType... types){
        for (TokenType type : types) {
            if(check(type)) {   // Hit! consume token and step forward
                advance();
                return true;
            }
        }
        return false;   // This was a dud, don't consume any tokens
    }

    // Function similar to match, but used when it's imperative that the next character is of a specific type.
    private Token consume(TokenType type, String message){
        if(check(type)) return advance();   // If the next token is the right type, we've got no problems

        throw error(peek(), message);   // It didn't match, so sound the alarm.
    }

    // Part of match's operation: the actual equality check
    private boolean check(TokenType type){
        return !isAtEnd() && peek().type == type;
    }

    // Returns what is essentially the current token and step ahead by one in Token list
    private Token advance(){
        if(!isAtEnd()) current++;
        return previous();
    }

    // Check if there are any more tokens to parse (here's where explicitly storing EOF as a token makes life easy)
    private boolean isAtEnd(){
        return peek().type == EOF;
    }

    // Returns next token to consume (without advancing)
    private Token peek() {
        return tokens.get(current);
    }

    // Return the last token checked (no advance)
    private Token previous() {
        return tokens.get(current-1);
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();    // We return instead of throw in case it's a non-fatal error. Something else can decide to throw.
    }

    private void synchronize(){     // TODO: Actually use this method. There aren't statements yet so this doesn't mean much.
        advance();

        while(!isAtEnd()){
            if(previous().type == SEMICOLON) return;    // time to relax, we found the end of the statement.
            // This semicolon business is also somewhat vulnerable to for loop syntax. This could maybe be reworked to be
            // more defensive in that case.

            switch(peek().type){    // All of these likely signify the start of a statement, so they're safe to return on
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();  // Keep chewing up tokens until we get to safety (the end/beginning of a statement
        }
    }

}
