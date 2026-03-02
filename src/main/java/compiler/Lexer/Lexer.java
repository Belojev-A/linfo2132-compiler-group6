package compiler.Lexer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

public class Lexer {

    private final BufferedReader reader;
    private int current; //actuel, -1 = EOF
    private int line = 1;
    private int column = 0;

    // tout les mots maps aux tokens
    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();
    static {
        KEYWORDS.put("final",  TokenType.FINAL);
        KEYWORDS.put("coll",   TokenType.COLL);
        KEYWORDS.put("def",    TokenType.DEF);
        KEYWORDS.put("for",    TokenType.FOR);
        KEYWORDS.put("while",  TokenType.WHILE);
        KEYWORDS.put("if",     TokenType.IF);
        KEYWORDS.put("else",   TokenType.ELSE);
        KEYWORDS.put("return", TokenType.RETURN);
        KEYWORDS.put("ARRAY",  TokenType.ARRAY);
        KEYWORDS.put("INT",    TokenType.INT);
        KEYWORDS.put("FLOAT",  TokenType.FLOAT);
        KEYWORDS.put("BOOL",   TokenType.BOOL);
        KEYWORDS.put("STRING", TokenType.STRING);
        KEYWORDS.put("true",   TokenType.TRUE);
        KEYWORDS.put("false",  TokenType.FALSE);
    }

    public Lexer(Reader input) {
        this.reader = new BufferedReader(input);
        advance(); // prime prem character
    }

    //lis le prochain character en current
    private void advance() {
        try {
            current = reader.read();
            if (current == '\n') { line++; column = 0; }
            else { column++; }
        } catch (IOException e) {
            current = -1;
        }
    }

    //peeking sans consumer
    private int peek() {
        try {
            reader.mark(1);
            int next = reader.read();
            reader.reset();
            return next;
        } catch (IOException e) {
            return -1;
        }
    }

    // skip les espaces et comments
    private void skipWhitespaceAndComments() {
        while (current != -1) {
            if (current == ' ' || current == '\t' || current == '\n' || current == '\r') {
                advance();
            } else if (current == '#') {
                while (current != -1 && current != '\n') advance();
            } else {
                break;
            }
        }
    }

    private Symbol lexIdentifierOrKeyword(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();
        while (current != -1 && (Character.isLetterOrDigit(current) || current == '_')) {
            sb.append((char) current);
            advance();
        }
        String word = sb.toString();

        // priorite aux keywords
        if (KEYWORDS.containsKey(word)) {
            return new Symbol(KEYWORDS.get(word), startLine, startCol);
        }

        // collection names: pas dans la map et commence par majuscule
        if (Character.isUpperCase(word.charAt(0))) {
            return new Symbol(TokenType.COLLECTION_NAME, word, startLine, startCol);
        }

        return new Symbol(TokenType.IDENTIFIER, word, startLine, startCol);
    }

    private Symbol lexNumber(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();

        // ".234" -> "0.234"
        if (current == '.') {
            sb.append("0.");
            advance();
            while (current != -1 && Character.isDigit(current)) {
                sb.append((char) current);
                advance();
            }
            return new Symbol(TokenType.FLOAT_LIT, sb.toString(), startLine, startCol);
        }

        // lire parti integer
        while (current != -1 && Character.isDigit(current)) {
            sb.append((char) current);
            advance();
        }

        // float si ca continue apres la decimal
        if (current == '.') {
            sb.append('.');
            advance();
            while (current != -1 && Character.isDigit(current)) {
                sb.append((char) current);
                advance();
            }
            return new Symbol(TokenType.FLOAT_LIT, sb.toString(), startLine, startCol);
        }

        // parse pour strip les leading zeros: "00342" -> "342"
        int val = Integer.parseInt(sb.toString());
        return new Symbol(TokenType.INTEGER_LIT, String.valueOf(val), startLine, startCol);
    }

    public Symbol getNextSymbol() {
        skipWhitespaceAndComments();

        if (current == -1) {
            return new Symbol(TokenType.EOF, line, column);
        }

        int startLine = line;
        int startCol  = column;

        if (Character.isLetter(current) || current == '_') {
            return lexIdentifierOrKeyword(startLine, startCol);
        }

        // nombre ou float ".234"
        if (Character.isDigit(current) || (current == '.' && Character.isDigit(peek()))) {
            return lexNumber(startLine, startCol);
        }

        if (current == '"') {
            return lexString(startLine, startCol);
        }

        throw new RuntimeException("unexpected char '" + (char) current + "' line " + startLine);
    }

    private Symbol lexString(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();
        advance(); // skip "
        while (current != -1 && current != '"') {
            if (current == '\\') {
                advance();
                switch (current) {
                    case 'n':  sb.append('\n'); break;
                    case '\\': sb.append('\\'); break;
                    case '"':  sb.append('"');  break;
                    default:
                        throw new RuntimeException("bad escape char '\\" + (char) current + "' line " + line);
                }
            } else {
                sb.append((char) current);
            }
            advance();
        }
        if (current == -1) {
            throw new RuntimeException("string pas fermee ligne " + startLine);
        }
        advance(); // skip "
        return new Symbol(TokenType.STRING_LIT, sb.toString(), startLine, startCol);
    }
}
