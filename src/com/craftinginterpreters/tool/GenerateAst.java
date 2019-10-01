package com.craftinginterpreters.tool;
// This is in the tool package as this is not a publicly used package, just for constructing the classes initially.
// This could be more easily done in a scripting language.
import java.io.IOException;
import java.io.File;
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
                "Binary   : Expr left, Token operator, Expr right",
                "Grouping : Expr expression",
                "Literal  : Object value",
                "Unary    : Token operator, Expr right"
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
            for (String type : types){
                String className = type.split(":")[0].trim();
                String fields = type.split(":")[1].trim();
                defineType(writer, baseName, className, fields);
            }
        writer.println("}");
        writer.println();
        writer.close();
    }

    private static void defineType(
            PrintWriter writer, String baseName,
            String className, String fieldList) {
        writer.println("  static class " + className + " extends " +
            baseName + " {");

        // Constructor
        writer.println("        " + className + "(" + fieldList + ") {");

        // Store parameters in fields.
        String[] fields = fieldList.split(", ");
        for (String field : fields) {
            String name = field.split(" ")[1];
            writer.println("    this." + name + " = " + name + ";");
        }

        // Close out that constructor
        writer.println("    }");

        // Put those fields in the class
        writer.println();
        for (String field : fields){
            writer.println("    final " + field + ";");
        }

        // Close the class.
        writer.println(" }");
    }
}
