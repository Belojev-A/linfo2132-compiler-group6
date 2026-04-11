package compiler.SemanticAnalysis;

/**
 * Exception levée lors d'une erreur sémantique.
 * Le message doit contenir le mot-clé correspondant à l'erreur :
 *   TypeError, CollectionError, OperatorError, ArgumentError,
 *   MissingConditionError, ReturnError, ScopeError
 */
public class SemanticException extends RuntimeException {

    private final int line;

    public SemanticException(String message, int line) {
        super("Semantic error at line " + line + ": " + message);
        this.line = line;
    }

    public int getLine() { return line; }
}
