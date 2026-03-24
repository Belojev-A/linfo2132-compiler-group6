import static org.junit.Assert.*;
import org.junit.Test;

import java.io.StringReader;
import compiler.Lexer.Lexer;
import compiler.Parser.Parser;
import compiler.Parser.ParseException;
import compiler.Parser.ast.ASTNode;
import compiler.Parser.ast.LeafNode;

public class TestParser {

    private ASTNode parse(String src) {
        Lexer lexer = new Lexer(new StringReader(src));
        Parser parser = new Parser(lexer);
        return parser.getAST();
    }

    private ASTNode child(ASTNode node, int i) {
        return node.getChildren().get(i);
    }

    private String label(ASTNode node) {
        return node.getLabel();
    }

    private String value(ASTNode node) {
        assertTrue(node instanceof LeafNode);
        return ((LeafNode) node).getValue();
    }

    private int childCount(ASTNode node) {
        return node.getChildren().size();
    }

    // Left-associativité : "1 + 2 + 3" doit donner (1+2)+3, pas 1+(2+3)
    @Test
    public void testLeftAssociativity() {
        ASTNode expr = child(child(parse("INT x = 1 + 2 + 3;"), 0), 2);
        assertEquals("BinaryExpr: +", label(expr));
        ASTNode left = child(expr, 0);
        assertEquals("BinaryExpr: +", label(left));
        assertEquals("1", value(child(left, 0)));
        assertEquals("2", value(child(left, 1)));
        assertEquals("3", value(child(expr, 1)));
    }

    // Précédence : "1 + 2 * 3" doit donner 1+(2*3), pas (1+2)*3
    @Test
    public void testOperatorPrecedence() {
        ASTNode expr = child(child(parse("INT x = 1 + 2 * 3;"), 0), 2);
        assertEquals("BinaryExpr: +", label(expr));
        assertEquals("1", value(child(expr, 0)));
        ASTNode right = child(expr, 1);
        assertEquals("BinaryExpr: *", label(right));
        assertEquals("2", value(child(right, 0)));
        assertEquals("3", value(child(right, 1)));
    }

    // Parenthèses forcent la priorité : "(1 + 2) * 3"
    @Test
    public void testParenthesesOverridePrecedence() {
        ASTNode expr = child(child(parse("INT x = (1 + 2) * 3;"), 0), 2);
        assertEquals("BinaryExpr: *", label(expr));
        assertEquals("BinaryExpr: +", label(child(expr, 0)));
        assertEquals("3", value(child(expr, 1)));
    }

    // Unaire : INT x = -5;
    @Test
    public void testUnaryMinus() {
        ASTNode expr = child(child(parse("INT x = -5;"), 0), 2);
        assertEquals("UnaryExpr: -", label(expr));
        assertEquals("5", value(child(expr, 0)));
    }

    // For loop avec syntaxe spéciale : for (INT i; 1 -> 10; i + 1)
    @Test
    public void testForStmt() {
        ASTNode program = parse("def main() { for (INT i; 1 -> 10; i + 1) { } }");
        ASTNode forStmt = child(child(child(program, 0), 3), 0);
        assertEquals("ForStmt", label(forStmt));
        assertEquals(5, childCount(forStmt));
        ASTNode forVar = child(forStmt, 0);
        assertEquals("ForVar", label(forVar));
        assertEquals("INT", value(child(forVar, 0)));
        assertEquals("i",   value(child(forVar, 1)));
        assertEquals("1",  value(child(forStmt, 1)));
        assertEquals("10", value(child(forStmt, 2)));
        assertEquals("BinaryExpr: +", label(child(forStmt, 3)));
    }

    // VarDecl sans initialisation : INT a;
    @Test
    public void testVarDeclWithoutInit() {
        ASTNode varDecl = child(parse("INT a;"), 0);
        assertEquals("VarDecl", label(varDecl));
        assertEquals(2, childCount(varDecl));
    }

    // Collection avec champs
    @Test
    public void testCollDecl() {
        ASTNode collDecl = child(parse("coll Point { INT x; INT y; }"), 0);
        assertEquals("CollDecl", label(collDecl));
        assertEquals(3, childCount(collDecl));
        assertEquals("Point", value(child(collDecl, 0)));
        assertEquals("FieldDecl", label(child(collDecl, 1)));
        assertEquals("FieldDecl", label(child(collDecl, 2)));
    }

    // Accès champ : p.x
    @Test
    public void testFieldAccess() {
        ASTNode varDecl = child(child(child(parse("def main() { INT v = p.x; }"), 0), 3), 0);
        ASTNode fieldAccess = child(varDecl, 2);
        assertEquals("FieldAccess", label(fieldAccess));
        assertEquals("p", value(child(fieldAccess, 0)));
        assertEquals("x", value(child(fieldAccess, 1)));
    }

    // Accès tableau : arr[0]
    @Test
    public void testArrayAccess() {
        ASTNode varDecl = child(child(child(parse("def main() { INT v = arr[0]; }"), 0), 3), 0);
        ASTNode arrayAccess = child(varDecl, 2);
        assertEquals("ArrayAccess", label(arrayAccess));
        assertEquals("arr", value(child(arrayAccess, 0)));
        assertEquals("0",   value(child(arrayAccess, 1)));
    }

    // Création de tableau : INT ARRAY [5]
    @Test
    public void testArrayCreation() {
        ASTNode varDecl = child(child(child(parse("def main() { INT c = INT ARRAY [5]; }"), 0), 3), 0);
        ASTNode arrCreate = child(varDecl, 2);
        assertEquals("ArrayCreation", label(arrCreate));
        assertEquals("INT", value(child(arrCreate, 0)));
        assertEquals("5",   value(child(arrCreate, 1)));
    }

    // Constructeur de collection : Point(3, 7)
    @Test
    public void testCollConstructor() {
        ASTNode varDecl = child(child(child(parse("def main() { Point p = Point(3, 7); }"), 0), 3), 0);
        ASTNode constructor = child(varDecl, 2);
        assertEquals("CollConstructor", label(constructor));
        assertEquals("Point", value(child(constructor, 0)));
        assertEquals(2, childCount(child(constructor, 1)));
    }

    // Erreurs syntaxiques
    @Test(expected = ParseException.class)
    public void testErrorMissingExpression() {
        parse("INT x = ;");
    }

    @Test(expected = ParseException.class)
    public void testErrorMissingSemicolon() {
        parse("INT x = 3");
    }

    @Test(expected = ParseException.class)
    public void testErrorMissingClosingParen() {
        parse("def main( { }");
    }

    @Test(expected = ParseException.class)
    public void testErrorIfMissingParen() {
        parse("def main() { if a > 0 { } }");
    }

    @Test(expected = ParseException.class)
    public void testErrorEmptyCollection() {
        parse("coll Point { }");
    }
}
