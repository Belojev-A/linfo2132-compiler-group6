package compiler;

import compiler.CodeGeneration.CodeGenerator;
import compiler.Lexer.Lexer;
import compiler.Lexer.Symbol;
import compiler.Lexer.TokenType;
import compiler.Parser.Parser;
import compiler.Parser.ast.ASTNode;
import compiler.SemanticAnalysis.SemanticAnalyzer;
import compiler.SemanticAnalysis.SemanticException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

public class Compiler {

    public static void main(String[] args) {
        // mode par défaut : génération de code
        if (args.length == 1) {
            String className = getClassName(args[0]);
            runCodeGen(args[0], className, className + ".class");
            return;
        }

        // source.lang -o output.class
        if (args.length == 3 && args[1].equals("-o")) {
            String className = getClassName(args[2]);
            runCodeGen(args[0], className, args[2]);
            return;
        }

        if (args.length != 2) {
            System.err.println("Usage: <file>  |  <file> -o <output.class>  |  -lexer <file>  |  -parser <file>  |  -semantic <file>");
            System.exit(1);
            return;
        }

        String mode     = args[0];
        String filepath = args[1];

        switch (mode) {
            case "-lexer":    runLexer(filepath);    break;
            case "-parser":   runParser(filepath);   break;
            case "-semantic": runSemantic(filepath); break;
            default:
                System.err.println("Mode inconnu: " + mode);
                System.exit(1);
        }
    }

    // extrait le nom de classe depuis un chemin (ex: "./tests/test.class" → "test")
    private static String getClassName(String path) {
        String name = new File(path).getName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(0, dot) : name;
    }

    // génération de code : analyse sémantique puis bytecode
    private static void runCodeGen(String srcPath, String className, String outputPath) {
        try {
            Reader reader = new FileReader(srcPath);
            Lexer lexer = new Lexer(reader);
            Parser parser = new Parser(lexer);
            ASTNode root = parser.getAST();
            new SemanticAnalyzer().analyze(root);
            new CodeGenerator(root, className, outputPath).generate();
            reader.close();
        } catch (SemanticException e) {
            System.err.println(e.getMessage());
            System.exit(2);
        } catch (IOException e) {
            System.err.println("File error: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    // Mode lexer : affiche tous les tokens un par ligne
    private static void runLexer(String filepath) {
        try {
            Reader reader = new FileReader(filepath);
            Lexer lexer = new Lexer(reader);

            Symbol sym;
            do {
                sym = lexer.getNextSymbol();
                System.out.println(sym);
            } while (sym.type != TokenType.EOF);

            reader.close();
        } catch (IOException e) {
            System.err.println("Erreur fichier: " + e.getMessage());
            System.exit(1);
        } catch (RuntimeException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    // parse + analyse sémantique
    private static void runSemantic(String filepath) {
        try {
            Reader reader = new FileReader(filepath);
            Lexer lexer = new Lexer(reader);
            Parser parser = new Parser(lexer);
            ASTNode root = parser.getAST();
            new SemanticAnalyzer().analyze(root);
            System.out.println("OK: aucune erreur sémantique");
            reader.close();
        } catch (IOException e) {
            System.err.println("File error: " + e.getMessage());
            System.exit(1);
        } catch (SemanticException e) {
            System.err.println(e.getMessage());
            System.exit(2);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    // affiche l'AST
    private static void runParser(String filepath) {
        try {
            Reader reader = new FileReader(filepath);
            Lexer lexer = new Lexer(reader);
            Parser parser = new Parser(lexer);

            ASTNode root = parser.getAST();

            System.out.println("------------------");
            root.print("");
            System.out.println("------------------");

            reader.close();
        } catch (IOException e) {
            System.err.println("File error: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
