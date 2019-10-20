package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Note: Statements produce no values, hence the Void type on the Visitor interface.
class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void>{

    // Note: putting this instantiation at the Interpreter class level keeps global variables around as long as the
    //      interpreter is running.
    final Environment globals = new Environment();              // This allows us to keep a global environment reference regardless of how
    private Environment environment = globals;                  // deeply nested our environments get. That means this "environment"
    private final Map<Expr, Integer> locals = new HashMap<>();   // field is responsible for the CURRENT environment
    private boolean replMode = false;

    Interpreter() {
        // this is an example of exposing a native function. This could be extended to support anything Java does!
        // TODO: Build a module import system out of this.
        globals.define("clock", new LoxCallable() {  // Note: This means that functions and variables are held together! This allows collisions,
            @Override                                             // but allows referring to functions as first-class values.
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return (double)System.currentTimeMillis() / 1000.0;
            }

            @Override
            public String toString() {return "<native fn>";}
        });
    }

    public void setReplMode(boolean replMode) {
        this.replMode = replMode;
    }

    void interpret(List<Stmt> statements) {
        try {
            for (Stmt statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    private void execute(Stmt stmt) {    // Note: this is the statement equivalent to evaluate()
        stmt.accept(this);
    }

    public void resolve(Expr expr, int depth) {
        locals.put(expr, depth);    // Put the expression in the locals table along with the depth.
    }

    public void executeBlock(List<Stmt> statements, Environment environment) {
        Environment previous = this.environment;    // Store higher environment to reset later
        try {
            this.environment = environment;         // Switch execution scope to new local scope. Because of the recursive nature of
                                                    // the Environment class, it's still accessible if necessary.
            for(Stmt statement : statements) {
                execute(statement);
            }
        } finally {
            this.environment = previous;            // This is maybe a bit inelegant. The other alternative is explicitly passing an
        }                                           // environment to each visit method, which seems equally as inelegant and more tedious.
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));    // Execute block, instantiating a new environment which is enclosed
        return null;                                                    // by the global scope on the way in.
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        Object superclass = null;
        if(stmt.superclass != null) {
            superclass = evaluate(stmt.superclass);
            if(!(superclass instanceof LoxClass)) {
                throw new RuntimeError(stmt.superclass.name, "Superclass must be a class.");
            }
        }

        environment.define(stmt.name.lexeme, null);

        if(stmt.superclass != null) {   // We're evaluating a subclass, so create a new environment to store superclass ref.
            environment = new Environment(environment);
            environment.define("super", superclass);
        }
        // Note: now that the current environment has a reference to the superclass, we can define methods (which may need to reference it)
        Map<String, LoxFunction> methods = new HashMap<>();
        for(Stmt.Function method : stmt.methods) {
            LoxFunction function = new LoxFunction(method, environment, method.name.lexeme.equals("init"));
            methods.put(method.name.lexeme, function);
        }

        LoxClass klass = new LoxClass(stmt.name.lexeme, (LoxClass)superclass, methods);
        if(superclass != null) environment = environment.enclosing; // Done with need to reference super, so pop the environment.
        environment.assign(stmt.name, klass);
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        Object result = evaluate(stmt.expression);
        if(this.replMode && !(stmt.expression instanceof Expr.Assign || stmt.expression instanceof Expr.Set || stmt.expression instanceof Expr.Call)) System.out.println(stringify(result));    // Immediately print result of expressions if running in repl.
        return null;    // Note: This return is necessary to satisfy the Java Void type requirements.
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        LoxFunction function = new LoxFunction(stmt, environment, false);   // Normal function declaration, isInitializer always false.
        environment.define(stmt.name.lexeme, function); // Note: Make sure upper environment knows about function so it can actually be referenced.
        return null;                                    // This also conveniently discards any functions that are declared within a local scope.
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {         // Note: the Lox implementation ends up being a small wrapper around the same Java code.
        if(isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch);
        } else if(stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {   // Note: this discards the expression's value as we only care about printing it.
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object value = null;
        if(stmt.value != null) value = evaluate(stmt.value);

        throw new Return(value);
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = null;
        if(stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }

        environment.define(stmt.name.lexeme, value);    // There is an initializer, so pop it into the map.
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        while(isTruthy(evaluate(stmt.condition))) {
            execute(stmt.body);
        }
        return null;
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);

        Integer distance = locals.get(expr);
        if(distance != null) {
            environment.assignAt(distance, expr.name, value);
        } else {
            globals.assign(expr.name, value);
        }

        return value;                                   // This returns the value to support nesting assignment in other expressions, i.e. print.
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
                if(left instanceof Double && right instanceof Double) {
                    return (double)left + (double) right;
                }

                if((left instanceof String) && (right instanceof String || right instanceof Double || right instanceof Boolean)) {
                    return (String)left + stringify(right);
                }
                // Addition is once again a special case and requires its own specific error handling.
                throw new RuntimeError(expr.operator, "Operands must be either two numbers or a string and a literal value.");

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
    public Object visitCallExpr(Expr.Call expr) {
        Object callee = evaluate(expr.callee);

        List<Object> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments) {
            arguments.add(evaluate(argument));  // Note: These arguments are evaluated in order, which means that if side effects occur
        }                                       // there could be weird consequences. This is mostly on the end user to handle.
                                                // Some compilers reorder things for efficiency, but this can be tough to debug in the side-effect case.

        if(!(callee instanceof LoxCallable)) {
            throw new RuntimeError(expr.paren, "Can only call functions and classes.");
        }

        LoxCallable function = (LoxCallable)callee;
        // Note: this arity check could be done at a LoxCallable implementation level, but doing it here means more DRY
        if(arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren, "Expected " + function.arity() + " arguments but got " +
                                    arguments.size() + ".");
        }

        return function.call(this, arguments);
    }

    @Override
    public Object visitGetExpr(Expr.Get expr) {
        Object object = evaluate(expr.object);
        if(object instanceof LoxInstance) {
            return ((LoxInstance) object).get(expr.name);
        }

        throw new RuntimeError(expr.name, "Only instances have properties.");
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);
         // Here's where the earlier work establishing truthiness becomes helpful!
        if(expr.operator.type == TokenType.OR) {
            if(isTruthy(left)) return left;   // Note: evaluate left first to see if short-circuit is possible.
        } else {
            if(!isTruthy(left)) return left;
        }

        return evaluate(expr.right);        // At this point we have to evaluate the right.
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

    @Override
    public Object visitSetExpr(Expr.Set expr) {
        Object object = evaluate(expr.object);      // first evaluate left side in case there's an expression tree to sort out.

        if(!(object instanceof LoxInstance)) {
            throw new RuntimeError(expr.name, "Only instances have fields.");
        }

        Object value = evaluate(expr.value);
        ((LoxInstance)object).set(expr.name, value);
        return value;
    }

    @Override
    public Object visitSuperExpr(Expr.Super expr) {
        int distance = locals.get(expr);
        LoxClass superclass = (LoxClass)environment.getAt(distance, "super");   // Look up "super" in the proper environment.

        // "this" is always one level closer than "super"'s environment
        LoxInstance object = (LoxInstance)environment.getAt(distance - 1, "this");

        LoxFunction method = superclass.findMethod(expr.method.lexeme);
        if(method == null) {
            throw new RuntimeError(expr.method, "Undefined property '" + expr.method.lexeme + "'.");
        }
        return method.bind(object);
    }

    @Override
    public Object visitThisExpr(Expr.This expr) {
        return lookUpVariable(expr.keyword, expr);
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        // return environment.get(expr.name);    - No longer doing this as we now use static resolution.
        return lookUpVariable(expr.name, expr);
    }

    private Object lookUpVariable(Token name, Expr expr) {
        Integer distance = locals.get(expr);
        if(distance != null) {
            return environment.getAt(distance, name.lexeme);
        } else {
            return globals.get(name);   // No distance, so assumed to be global.
        }
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if(operand instanceof Double) return;
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    private void checkNumberOperands(Token operator, Object left, Object right) {
        if(left instanceof Double && right instanceof Double) return;
        throw new RuntimeError(operator, "Operands must be numbers.");
    }

    private boolean isEqual(Object a, Object b) {
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
    private boolean isTruthy(Object object) {
        if(object==null) return false;
        if(object instanceof Boolean) return (boolean)object;   // Could be condensed, keeping this for readability/modifiable-ness
        return true;
    }

    private String stringify(Object object) {
        if(object == null) return "nil";

        // This is a fun hack to remove the ".0" that comes with treating everything as a double.
        if(object instanceof Double) {
            String text = object.toString();
            if(text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }
        // It's not a number, so just return the toString (this handles booleans and normal strings)
        // This also keeps the externally visible value _similar_ to Java's version.
        return object.toString();
    }
}
