package com.craftinginterpreters.tool;
// This is in the tool package as this is not a publicly used package, just for constructing the classes initially.
// This could be more easily done in a scripting language.
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

public class GenerateAst {
    public static void main(String[] args) throws IOException {
        if(args.length != 1){
            System.err.println("Usage: generate_ast <output directory>");
            System.exit(1);
        }
        String outputDir = args[0];
        // To generate classes of expressions
        defineAst(outputDir, "Expr", Arrays.asList(
                "Assign   : Token name, Expr value",
                "Binary   : Expr left, Token operator, Expr right",
                "Call     : Expr callee, Token paren, List<Expr> arguments",
                "Get      : Expr object, Token name",
                "Grouping : Expr expression",
                "Literal  : Object value",
                "Logical  : Expr left, Token operator, Expr right",
                "Set      : Expr object, Token name, Expr value",
                "Super    : Token keyword, Token method",
                "This     : Token keyword",
                "Unary    : Token operator, Expr right",
                "Variable : Token name"
        ));

        /* Note: This addition of another base class makes this script super useful. Adding a new base class file is
                 as simple as adding another call to defineAst!
       */
        // To generate classes of statements
        defineAst(outputDir, "Stmt", Arrays.asList(
                "Block      : List<Stmt> statements",
                "Class      : Token name, Expr.Variable superclass, List<Stmt.Function> methods",
                "Expression : Expr expression",
                "Function   : Token name, List<Token> params, List<Stmt> body",
                "If         : Expr condition, Stmt thenBranch, Stmt elseBranch",
                "Print      : Expr expression",
                "Return     : Token keyword, Expr value",
                "Var        : Token name, Expr initializer",
                "While      : Expr condition, Stmt body"
        ));
    }

    private static void defineAst(
            String outputDir, String baseName, List<String> types)
            throws IOException {
        String path = outputDir + "/" + baseName + ".java";
        PrintWriter writer = new PrintWriter(path, "UTF-8");
        System.out.println(path);
        writer.println("package com.craftinginterpreters.lox;");
        writer.println();
        writer.println("import java.util.List;");
        writer.println();
        writer.println("abstract class " + baseName + " {");

        defineVisitor(writer, baseName, types);

            // define AST classes
        for (String type : types){
            String className = type.split(":")[0].trim();
            String fields = type.split(":")[1].trim();
            defineType(writer, baseName, className, fields);
        }

        // base accept() method
        writer.println("    abstract <R> R accept(Visitor<R> visitor);");

        writer.println("}");
        writer.println();
        writer.close();
    }

    private static void defineVisitor(
            PrintWriter writer, String baseName, List<String> types) {
        writer.println("    interface Visitor<R> {");

        for (String type : types) {
            String typeName = type.split(":")[0].trim();
            writer.println("        R visit" + typeName + baseName + "(" +
                typeName + " " + baseName.toLowerCase() + ");");
        }

        writer.println("    }");
    }

    private static void defineType(
            PrintWriter writer, String baseName,
            String className, String fieldList) {
        writer.println("    static class " + className + " extends " +
            baseName + " {");

        // Constructor
        writer.println("        " + className + "(" + fieldList + ") {");

        // Store parameters in fields.
        String[] fields = fieldList.split(", ");
        for (String field : fields) {
            String name = field.split(" ")[1];
            writer.println("            this." + name + " = " + name + ";");
        }

        // Close out that constructor
        writer.println("        }");

        // Visitor pattern
        writer.println();
        writer.println("        <R> R accept(Visitor<R> visitor) {");
        writer.println("            return visitor.visit" +
            className + baseName + "(this);");
        writer.println("        }");

        // Put those fields in the class
        writer.println();
        for (String field : fields){
            writer.println("        final " + field + ";");
        }

        // Close the class.
        writer.println("    }");
        writer.println();
    }
}
