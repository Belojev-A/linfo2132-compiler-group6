package compiler;

import compiler.Lexer.Lexer;
import compiler.Lexer.Symbol;
import compiler.Lexer.TokenType;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

public class Compiler {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: -lexer <filepath>");
            System.exit(1);
        }

        String mode     = args[0];
        String filepath = args[1];

        if (mode.equals("-lexer")) {
            runLexer(filepath);
        } else {
            System.err.println("mode inconnu: " + mode);
            System.exit(1);
        }
    }

    private static void runLexer(String filepath) {
        try {
            Reader reader = new FileReader(filepath);
            Lexer lexer = new Lexer(reader);

            // un token par ligne jusqu'a EOF
            Symbol sym;
            do {
                sym = lexer.getNextSymbol();
                System.out.println(sym);
            } while (sym.type != TokenType.EOF);

            reader.close();
        } catch (IOException e) {
            System.err.println("erreur fichier: " + e.getMessage());
            System.exit(1);
        } catch (RuntimeException e) {
            // token pas reconnu -> exit != 0
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
