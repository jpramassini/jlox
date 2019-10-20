package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void>{
    private final Interpreter interpreter;
    private final Stack<Map<String, Boolean>> scopes = new Stack<>();
    private FunctionType currentFunction = FunctionType.NONE;
    private ClassType currentClass = ClassType.NONE;

    private enum ClassType {
        NONE,
        CLASS,
        SUBCLASS
    }

    private enum FunctionType {
        NONE,
        FUNCTION,
        METHOD,
        INITIALIZER
    }

    Resolver(Interpreter interpreter){
        this.interpreter = interpreter;
    }

    void resolve(List<Stmt> statements){
        for(Stmt statement : statements){
            resolve(statement);
        }
    }

    private void resolve(Stmt stmt){
        stmt.accept(this);
    }

    private void resolve(Expr expr){
        expr.accept(this);
    }

    private void resolveFunction(Stmt.Function function, FunctionType type){
        FunctionType enclosingFunction = currentFunction;
        currentFunction = type;

        beginScope();
        for(Token param : function.params){
            declare(param);
            define(param);
        }
        resolve(function.body);     // Different than the dynamic method, here we immediately traverse the function's body.
        endScope();                 // In runtime, the body is only traversed when the function is called.
        currentFunction = enclosingFunction;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt){
        beginScope();
        resolve(stmt.statements);
        endScope();
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt){
        ClassType enclosingClass = currentClass;    // This allows class nesting (and nesting of this references.)
        currentClass = ClassType.CLASS;

        declare(stmt.name);
        define(stmt.name);

        if(stmt.superclass != null){
            currentClass = ClassType.SUBCLASS;
            if(stmt.name.lexeme.equals(stmt.superclass.name.lexeme)){
                Lox.error(stmt.superclass.name, "A class cannot inherit from itself.");
            }
            resolve(stmt.superclass);   // There's a chance the superclass isn't a global variable, which means we need to ensure it gets resolved.
            beginScope();
            scopes.peek().put("super", true);
        }



        beginScope();
        scopes.peek().put("this", true);                    // Bind "this" instance reference to anything in the class scope.

        for(Stmt.Function method : stmt.methods){
            FunctionType declaration = FunctionType.METHOD;
            if(method.name.lexeme.equals("init")) {
                declaration = FunctionType.INITIALIZER;
            }
            resolveFunction(method, declaration);
        }

        if(stmt.superclass != null) endScope(); // If there was a super, we created multiple scopes, so we need to close two.

        endScope();

        currentClass = enclosingClass;
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt){
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt){
        declare(stmt.name);
        define(stmt.name);  // Eagerly define the function name to allow for self-reference and recursion.

        resolveFunction(stmt, FunctionType.FUNCTION);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt){
        resolve(stmt.condition);
        resolve(stmt.thenBranch);
        if(stmt.elseBranch != null) resolve(stmt.elseBranch);   // Note: this traverses any branch that COULD be run, not just
        return null;                                            // the branch that is run.
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt){
        resolve(stmt.expression);   // Only contains a single subexpression, so resolve that.
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt){
        if(currentFunction == FunctionType.NONE) Lox.error(stmt.keyword, "Cannot return from top-level code.");
        if(stmt.value != null){
            if(currentFunction == FunctionType.INITIALIZER){
                Lox.error(stmt.keyword, "Cannot return a value from an initializer.");
            }
            resolve(stmt.value);
        }
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt){
        declare(stmt.name);
        if(stmt.initializer != null){
            resolve(stmt.initializer);
        }
        define(stmt.name);
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt){
        resolve(stmt.condition);
        resolve(stmt.body);     // Again, eagerly resolve body even if it won't get run.
        return null;
    }

    @Override
    public Void visitAssignExpr(Expr.Assign expr){
        resolve(expr.value);    // Resolve other expressions in assignment, in case they reference stuff from another scope.
        resolveLocal(expr, expr.name);  // Now, resolve the local assignment.
        return null;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr){
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr){
        resolve(expr.callee);
        for (Expr argument : expr.arguments){
            resolve(argument);
        }
        return null;
    }

    @Override
    public Void visitGetExpr(Expr.Get expr){
        resolve(expr.object);               // Properties are allocated dynamically, so only resolve the object the 'get' is attached to.
        return null;
    }

    @Override
    public Void visitGroupingExpr(Expr.Grouping expr){
        resolve(expr.expression);
        return null;
    }

    @Override
    public Void visitLiteralExpr(Expr.Literal expr){
        return null;        // Nothing to resolve here, noop.
    }

    @Override
    public Void visitLogicalExpr(Expr.Logical expr){
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitSetExpr(Expr.Set expr){
        resolve(expr.value);
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitSuperExpr(Expr.Super expr){
        if(currentClass == ClassType.NONE){
            Lox.error(expr.keyword, "Cannot use 'super' outside of a class.");
        } else if (currentClass != ClassType.SUBCLASS){
            Lox.error(expr.keyword, "Cannot use 'super' in a class with no superclass.");
        }
        resolveLocal(expr, expr.keyword);
        return null;
    }

    @Override
    public Void visitThisExpr(Expr.This expr){
        if(currentClass == ClassType.NONE) Lox.error(expr.keyword, "Cannot use 'this' outside of a class.");
        resolveLocal(expr, expr.keyword);
        return null;
    }

    @Override
    public Void visitUnaryExpr(Expr.Unary expr){
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitVariableExpr(Expr.Variable expr){
        if(!scopes.isEmpty() && scopes.peek().get(expr.name.lexeme) == Boolean.FALSE){
            Lox.error(expr.name, "Cannot read local variable in its own initializer.");
        }
        resolveLocal(expr, expr.name);
        return null;
    }


    private void beginScope(){
        scopes.push(new HashMap<String, Boolean>());
    }

    private void endScope(){
        scopes.pop();
    }

    private void declare(Token name){
        if(scopes.isEmpty()) return;

        Map<String, Boolean> scope = scopes.peek(); // Add var declaration to innermost scope.
        if(scope.containsKey(name.lexeme)) Lox.error(name, "Variable with this name already declared in this scope.");
        scope.put(name.lexeme, false);  // Boolean value here signifies that the variable has not been defined yet.
    }

    private void define(Token name){
        if(scopes.isEmpty()) return;
        scopes.peek().put(name.lexeme, true);   // Var is now defined, bool set to true.
    }

    /*
        This is a very powerful & important function for the resolver. This allows us to traverse the scope hierarchy and pass
        depths of all declared local variables to the interpreter.
     */
    private void resolveLocal(Expr expr, Token name){
        for(int i = scopes.size() - 1; i >= 0; i--){
            if(scopes.get(i).containsKey(name.lexeme)){
                interpreter.resolve(expr, scopes.size() - 1 - i);
                return;
            }
        }
        // Not found, variable assumed to be global.
    }
}
