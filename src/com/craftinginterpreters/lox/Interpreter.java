package com.craftinginterpreters.lox;

class Interpreter implements Expr.Visitor<Object>{

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
                return (double)left - (double)right;
            case PLUS:                              // The + operator is special because it's overloaded to allow both
                                                    // arithmetic adding and string concatenation.
                if(left instanceof Double && right instanceof Double){
                    return (double)left + (double) right;
                }

                if(left instanceof String && right instanceof String){
                    return (String)left + (String)right;
                }

            case SLASH:
                return (double)left / (double)right;
            case STAR:
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
                return -(double)right; // Note: The cast here is important. The operand is cast at runtime, which allows for dynamic typing in Lox.
            case BANG:
                return !isTruthy(right);
        }

        // This should never be reached, but just in case...
        return null;
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
}
