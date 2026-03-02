import static org.junit.Assert.*;
import org.junit.Test;

import java.io.StringReader;
import compiler.Lexer.Lexer;
import compiler.Lexer.Symbol;
import compiler.Lexer.TokenType;

public class TestLexer {

    private Lexer lexer(String input) {
        return new Lexer(new StringReader(input));
    }

    private Symbol next(Lexer l) {
        return l.getNextSymbol();
    }

    // verifie type et valeur d'un symbol
    private void check(Symbol s, TokenType type, String value) {
        assertEquals(type, s.type);
        assertEquals(value, s.value);
    }

    private void checkType(Symbol s, TokenType type) {
        assertEquals(type, s.type);
    }

    @Test
    public void testKeywords() {
        Lexer l = lexer("final coll def for while if else return ARRAY");
        checkType(next(l), TokenType.FINAL);
        checkType(next(l), TokenType.COLL);
        checkType(next(l), TokenType.DEF);
        checkType(next(l), TokenType.FOR);
        checkType(next(l), TokenType.WHILE);
        checkType(next(l), TokenType.IF);
        checkType(next(l), TokenType.ELSE);
        checkType(next(l), TokenType.RETURN);
        checkType(next(l), TokenType.ARRAY);
    }

    @Test
    public void testNotIsIdentifier() {
        // "not" est une fonction built-in, pas un keyword
        Lexer l = lexer("not");
        check(next(l), TokenType.IDENTIFIER, "not");
    }

    @Test
    public void testBaseTypes() {
        Lexer l = lexer("INT FLOAT BOOL STRING");
        checkType(next(l), TokenType.INT);
        checkType(next(l), TokenType.FLOAT);
        checkType(next(l), TokenType.BOOL);
        checkType(next(l), TokenType.STRING);
    }

    @Test
    public void testBoolLiterals() {
        Lexer l = lexer("true false");
        checkType(next(l), TokenType.TRUE);
        checkType(next(l), TokenType.FALSE);
    }

    @Test
    public void testIdentifiers() {
        Lexer l = lexer("abc abc123 _abc_ _12s myVar");
        check(next(l), TokenType.IDENTIFIER, "abc");
        check(next(l), TokenType.IDENTIFIER, "abc123");
        check(next(l), TokenType.IDENTIFIER, "_abc_");
        check(next(l), TokenType.IDENTIFIER, "_12s");
        check(next(l), TokenType.IDENTIFIER, "myVar");
    }

    @Test
    public void testCollectionNames() {
        Lexer l = lexer("Point Person MyType");
        check(next(l), TokenType.COLLECTION_NAME, "Point");
        check(next(l), TokenType.COLLECTION_NAME, "Person");
        check(next(l), TokenType.COLLECTION_NAME, "MyType");
    }

    @Test
    public void testIntegers() {
        Lexer l = lexer("0 42 100");
        check(next(l), TokenType.INTEGER_LIT, "0");
        check(next(l), TokenType.INTEGER_LIT, "42");
        check(next(l), TokenType.INTEGER_LIT, "100");
    }

    @Test
    public void testLeadingZeros() {
        // "00342" doit donner "342"
        Lexer l = lexer("00342");
        check(next(l), TokenType.INTEGER_LIT, "342");
    }

    @Test
    public void testFloats() {
        Lexer l = lexer("3.14 0.5");
        check(next(l), TokenType.FLOAT_LIT, "3.14");
        check(next(l), TokenType.FLOAT_LIT, "0.5");
    }

    @Test
    public void testFloatStartingWithDot() {
        // ".234" doit donner "0.234"
        Lexer l = lexer(".234");
        check(next(l), TokenType.FLOAT_LIT, "0.234");
    }

    @Test
    public void testStrings() {
        Lexer l = lexer("\"hello\" \"world\"");
        check(next(l), TokenType.STRING_LIT, "hello");
        check(next(l), TokenType.STRING_LIT, "world");
    }

    @Test
    public void testStringEscapes() {
        Lexer l = lexer("\"a\\nb\" \"say \\\"hi\\\"\" \"back\\\\slash\"");
        check(next(l), TokenType.STRING_LIT, "a\nb");
        check(next(l), TokenType.STRING_LIT, "say \"hi\"");
        check(next(l), TokenType.STRING_LIT, "back\\slash");
    }

    @Test
    public void testOperators() {
        Lexer l = lexer("= + - * / % == =/= < > <= >= && ||");
        checkType(next(l), TokenType.ASSIGN);
        checkType(next(l), TokenType.PLUS);
        checkType(next(l), TokenType.MINUS);
        checkType(next(l), TokenType.TIMES);
        checkType(next(l), TokenType.DIVIDE);
        checkType(next(l), TokenType.MOD);
        checkType(next(l), TokenType.EQUAL);
        checkType(next(l), TokenType.NOT_EQUAL);
        checkType(next(l), TokenType.LT);
        checkType(next(l), TokenType.GT);
        checkType(next(l), TokenType.LE);
        checkType(next(l), TokenType.GE);
        checkType(next(l), TokenType.AND);
        checkType(next(l), TokenType.OR);
    }

    @Test
    public void testArrow() {
        Lexer l = lexer("1 -> 10");
        checkType(next(l), TokenType.INTEGER_LIT);
        checkType(next(l), TokenType.ARROW);
        checkType(next(l), TokenType.INTEGER_LIT);
    }

    @Test
    public void testDelimiters() {
        Lexer l = lexer("( ) { } [ ] . ; ,");
        checkType(next(l), TokenType.LPAREN);
        checkType(next(l), TokenType.RPAREN);
        checkType(next(l), TokenType.LBRACE);
        checkType(next(l), TokenType.RBRACE);
        checkType(next(l), TokenType.LBRACKET);
        checkType(next(l), TokenType.RBRACKET);
        checkType(next(l), TokenType.DOT);
        checkType(next(l), TokenType.SEMICOLON);
        checkType(next(l), TokenType.COMMA);
    }

    @Test
    public void testSkipsWhitespaceAndComments() {
        Lexer l = lexer("INT   x\n# commentaire\n= 2;");
        checkType(next(l), TokenType.INT);
        check(next(l), TokenType.IDENTIFIER, "x");
        checkType(next(l), TokenType.ASSIGN);
        check(next(l), TokenType.INTEGER_LIT, "2");
        checkType(next(l), TokenType.SEMICOLON);
    }

    @Test
    public void testEOF() {
        Lexer l = lexer("");
        checkType(next(l), TokenType.EOF);
    }

    @Test
    public void testVarDeclaration() {
        // test basique d'une declaration
        Lexer l = lexer("INT x = 2;");
        checkType(next(l), TokenType.INT);
        check(next(l), TokenType.IDENTIFIER, "x");
        checkType(next(l), TokenType.ASSIGN);
        check(next(l), TokenType.INTEGER_LIT, "2");
        checkType(next(l), TokenType.SEMICOLON);
        checkType(next(l), TokenType.EOF);
    }

    @Test
    public void testNotAsBuiltinCall() {
        // not(x) : not est un IDENTIFIER suivi de LPAREN
        Lexer l = lexer("not(x)");
        check(next(l), TokenType.IDENTIFIER, "not");
        checkType(next(l), TokenType.LPAREN);
        check(next(l), TokenType.IDENTIFIER, "x");
        checkType(next(l), TokenType.RPAREN);
    }

    @Test
    public void testNotEqualNoSpaces() {
        // cas du code_example : while (value=/=3)
        Lexer l = lexer("value=/=3");
        check(next(l), TokenType.IDENTIFIER, "value");
        checkType(next(l), TokenType.NOT_EQUAL);
        check(next(l), TokenType.INTEGER_LIT, "3");
    }

    @Test
    public void testBuiltinFunctionIdentifiers() {
        // str, length, floor, ceil, print, println, read_INT sont des IDENTIFIER
        Lexer l = lexer("str length floor ceil print println read_INT read_FLOAT print_INT");
        checkType(next(l), TokenType.IDENTIFIER);
        checkType(next(l), TokenType.IDENTIFIER);
        checkType(next(l), TokenType.IDENTIFIER);
        checkType(next(l), TokenType.IDENTIFIER);
        checkType(next(l), TokenType.IDENTIFIER);
        checkType(next(l), TokenType.IDENTIFIER);
        checkType(next(l), TokenType.IDENTIFIER);
        checkType(next(l), TokenType.IDENTIFIER);
        checkType(next(l), TokenType.IDENTIFIER);
    }

    @Test(expected = RuntimeException.class)
    public void testUnrecognizedToken() {
        Lexer l = lexer("INT x @ 2;");
        next(l); next(l);
        next(l); // @ -> exception
    }

    @Test(expected = RuntimeException.class)
    public void testUnterminatedString() {
        Lexer l = lexer("\"hello");
        next(l);
    }
}
