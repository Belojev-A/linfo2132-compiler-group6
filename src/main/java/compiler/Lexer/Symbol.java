package compiler.Lexer;

public class Symbol {

    public final TokenType type;
    public final String value;
    public final int line;
    public final int column;

    public Symbol(TokenType type, String value, int line, int column) {
        this.type = type;
        this.value = value;
        this.line = line;
        this.column = column;
    }

    // tokens sans valeur pertinente
    public Symbol(TokenType type, int line, int column) {
        this(type, null, line, column);
    }

    @Override
    public String toString() {
        if (value != null) {
            return "<" + type + ", " + value + ">";
        }
        return "<" + type + ">";
    }
}