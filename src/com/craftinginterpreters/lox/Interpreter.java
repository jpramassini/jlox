package com.craftinginterpreters.lox;

class Interpreter implements Expr.Visitor<Object>{

    void interpret(Expr expression){
        try {
            Object value = evaluate(expression);
            System.out.println(stringify(value));
        } catch (RuntimeError error){
            Lox.runtimeError(error);
        }
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            // ARITHMETIC OPERATORS
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left - (double)right;
            case PLUS:                              // The + operator is special because it's overloaded to allow both
                                                    // arithmetic adding and string concatenation.
                if(left instanceof Double && right instanceof Double){
                    return (double)left + (double) right;
                }

                if(left instanceof String && right instanceof String){
                    return (String)left + (String)right;
                }
                // Addition is once again a special case and requires its own specific error handling.
                throw new RuntimeError(expr.operator, "Operands must be either two numbers or two strings.");

            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                return (double)left / (double)right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double)left * (double)right;
            // EQUALITY OPERATORS
            case GREATER:
                return (double)left > (double)right;
            case GREATER_EQUAL:
                return (double)left >= (double)right;
            case LESS:
                return (double)left < (double)right;
            case LESS_EQUAL:
                return (double)left <= (double)right;
            case BANG_EQUAL:
                return !isEqual(left, right);
            case EQUAL_EQUAL:
                return isEqual(left, right);
        }
        return null;    // Unreachable, but we should be safe either way.
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case MINUS:                 // Flip the sign of the operand
                checkNumberOperand(expr.operator, right);
                return -(double)right; // Note: The cast here is important. The operand is cast at runtime, which allows for dynamic typing in Lox.
            case BANG:
                return !isTruthy(right);
        }

        // This should never be reached, but just in case...
        return null;
    }

    private void checkNumberOperand(Token operator, Object operand){
        if(operand instanceof Double) return;
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    private void checkNumberOperands(Token operator, Object left, Object right){
        if(left instanceof Double && right instanceof Double) return;
        throw new RuntimeError(operator, "Operands must be numbers.");
    }

    private boolean isEqual(Object a, Object b){
        // nil is only equal to nil!
        if(a == null && b == null) return true;     // These two null cases are handled so as not to throw a NullPointerException
        if(a == null) return false;
        return a.equals(b);
    }

    /*
        This function is important, as this dictates what is considered truthy for the whole language.
        In this case, nil and false booleans are truthy and everything else is falsey. This could be made more robust
        to check for empty arrays, empty strings, 0, etc. (Different languages have different approaches.)
     */
    private boolean isTruthy(Object object){
        if(object==null) return false;
        if(object instanceof Boolean) return (boolean)object;   // Could be condensed, keeping this for readability/modifiable-ness
        return true;
    }

    private String stringify(Object object){
        if(object == null) return "nil";

        // This is a fun hack to remove the ".0" that comes with treating everything as a double.
        if(object instanceof Double){
            String text = object.toString();
            if(text.endsWith(".0")){
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }
        // It's not a number, so just return the toString (this handles booleans and normal strings)
        // This also keeps the externally visible value _similar_ to Java's version.
        return object.toString();
    }
}
