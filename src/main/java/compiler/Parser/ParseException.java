package compiler.Parser;

/**
 * Exception lancée quand le parser rencontre une erreur syntaxique.
 *
 * Exemple : "a int 2"  →  on attend '=' ou ';' après le type, pas '2'
 *           → ParseException("Expected '=' or ';', got INTEGER_LIT '2'", line 5)
 */
public class ParseException extends RuntimeException {

    private final int line;

    public ParseException(String message, int line) {
        super("Parse error at line " + line + ": " + message);
        this.line = line;
    }

    public int getLine() { return line; }
}
