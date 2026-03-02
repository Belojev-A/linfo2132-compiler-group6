package compiler.Lexer;

public enum TokenType {

    // keywords
    FINAL, COLL, DEF, FOR, WHILE, IF, ELSE, RETURN, ARRAY,

    // base types
    INT, FLOAT, BOOL, STRING,

    // literals
    INTEGER_LIT, FLOAT_LIT, STRING_LIT, TRUE, FALSE,

    // identifiers (lowercase/underscore start = variable/function, uppercase start = collection)
    IDENTIFIER, COLLECTION_NAME,

    // operators
    ASSIGN,         // =
    PLUS,           // +
    MINUS,          // -
    TIMES,          // *
    DIVIDE,         // /
    MOD,            // %
    EQUAL,          // ==
    NOT_EQUAL,      // =/=
    LT,             // <
    GT,             // >
    LE,             // <=
    GE,             // >=
    AND,            // &&
    OR,             // ||
    ARROW,          // ->

    // delimiters
    LPAREN,         // (
    RPAREN,         // )
    LBRACE,         // {
    RBRACE,         // }
    LBRACKET,       // [
    RBRACKET,       // ]
    DOT,            // .
    SEMICOLON,      // ;
    COMMA,          // ,

    // special
    EOF,
    ERROR
}
