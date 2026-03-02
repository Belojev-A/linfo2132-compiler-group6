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
