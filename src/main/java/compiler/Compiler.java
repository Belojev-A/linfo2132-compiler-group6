package compiler;

import compiler.Lexer.Lexer;
import compiler.Lexer.Symbol;
import compiler.Lexer.TokenType;
import compiler.Parser.Parser;
import compiler.Parser.ast.ASTNode;
import compiler.SemanticAnalysis.SemanticAnalyzer;
import compiler.SemanticAnalysis.SemanticException;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

public class Compiler {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: -lexer <filepath>  |  -parser <filepath>");
            System.exit(1);
        }

        String mode     = args[0];
        String filepath = args[1];

        if (mode.equals("-lexer")) {
            runLexer(filepath);
        } else if (mode.equals("-parser")) {
            runParser(filepath);
        } else if (mode.equals("-semantic")) {
            runSemantic(filepath);
        } else {
            System.err.println("Mode inconnu: " + mode);
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
