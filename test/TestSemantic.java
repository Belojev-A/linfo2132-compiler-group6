import static org.junit.Assert.*;
import org.junit.Test;

import java.io.StringReader;
import compiler.Lexer.Lexer;
import compiler.Parser.Parser;
import compiler.Parser.ast.ASTNode;
import compiler.SemanticAnalysis.SemanticAnalyzer;
import compiler.SemanticAnalysis.SemanticException;

public class TestSemantic {

    // parse et analyse le programme donné
    private void analyze(String src) {
        ASTNode root = new Parser(new Lexer(new StringReader(src))).getAST();
        new SemanticAnalyzer().analyze(root);
    }

    // vérifie que le message d'erreur contient le mot-clé attendu
    private void assertError(String keyword, String src) {
        try {
            analyze(src);
            fail("SemanticException attendue pour : " + keyword);
        } catch (SemanticException e) {
            assertTrue("Message devrait contenir '" + keyword + "', reçu : " + e.getMessage(),
                       e.getMessage().contains(keyword));
        }
    }

    // -------------------------------------------------------------------------
    // TypeError
    // -------------------------------------------------------------------------

    @Test
    public void testTypeErrorAssignStringToInt() {
        // assigner STRING à INT
        assertError("TypeError", "INT x = \"hello\";");
    }

    @Test
    public void testTypeErrorAssignBoolToFloat() {
        assertError("TypeError", "FLOAT x = true;");
    }

    @Test
    public void testTypeErrorConstantWrongType() {
        assertError("TypeError", "final INT x = 3.14;");
    }

    @Test
    public void testTypeErrorAssignInFunction() {
        assertError("TypeError",
            "def main() { INT x = 1; x = \"oops\"; }");
    }

    // -------------------------------------------------------------------------
    // CollectionError
    // -------------------------------------------------------------------------

    @Test
    public void testCollectionErrorUndefinedFieldType() {
        // champ d'une collection avec un type de collection non défini
        assertError("CollectionError", "coll Point { Unknown x; }");
    }

    @Test
    public void testCollectionErrorDuplicate() {
        // deux déclarations de la même collection
        assertError("CollectionError",
            "coll Point { INT x; INT y; } coll Point { INT z; }");
    }

    @Test
    public void testCollectionErrorUndefinedType() {
        // utiliser un type de collection non déclaré
        assertError("CollectionError", "Unknown x;");
    }

    // -------------------------------------------------------------------------
    // OperatorError
    // -------------------------------------------------------------------------

    @Test
    public void testOperatorErrorStringMinus() {
        // soustraction sur des STRING
        assertError("OperatorError", "def main() { INT x = \"a\" - \"b\"; }");
    }

    @Test
    public void testOperatorErrorModOnFloat() {
        // % requiert INT
        assertError("OperatorError", "def main() { FLOAT x = 3.0 % 2.0; }");
    }

    @Test
    public void testOperatorErrorAndOnInt() {
        // && requiert BOOL
        assertError("OperatorError", "def main() { BOOL x = 1 && 0; }");
    }

    @Test
    public void testOperatorErrorUnaryMinusOnBool() {
        assertError("OperatorError", "def main() { BOOL b = true; INT x = -b; }");
    }

    // -------------------------------------------------------------------------
    // ArgumentError
    // -------------------------------------------------------------------------

    @Test
    public void testArgumentErrorWrongParamType() {
        // foo attend INT mais reçoit STRING
        assertError("ArgumentError",
            "def INT foo(INT x) { return x; } def main() { INT r = foo(\"oops\"); }");
    }

    @Test
    public void testArgumentErrorTooManyArgs() {
        assertError("ArgumentError",
            "def INT foo(INT x) { return x; } def main() { INT r = foo(1, 2); }");
    }

    @Test
    public void testArgumentErrorCollConstructorWrongType() {
        // Point(3, 7) : les champs sont INT mais on passe STRING
        assertError("ArgumentError",
            "coll Point { INT x; INT y; } def main() { Point p = Point(\"a\", 7); }");
    }

    // -------------------------------------------------------------------------
    // MissingConditionError
    // -------------------------------------------------------------------------

    @Test
    public void testMissingConditionErrorIfNotBool() {
        // condition du if qui n'est pas BOOL
        assertError("MissingConditionError",
            "def main() { if (3) { } }");
    }

    @Test
    public void testMissingConditionErrorWhileNotBool() {
        assertError("MissingConditionError",
            "def main() { INT x = 1; while (x) { } }");
    }

    // -------------------------------------------------------------------------
    // ReturnError
    // -------------------------------------------------------------------------

    @Test
    public void testReturnErrorWrongType() {
        // fonction retourne INT mais return donne STRING
        assertError("ReturnError",
            "def INT foo() { return \"hello\"; }");
    }

    @Test
    public void testReturnErrorVoidReturnsValue() {
        assertError("ReturnError",
            "def main() { return 42; }");
    }

    // -------------------------------------------------------------------------
    // ScopeError
    // -------------------------------------------------------------------------

    @Test
    public void testScopeErrorUndeclaredVariable() {
        // utiliser une variable non déclarée
        assertError("ScopeError",
            "def main() { INT x = y; }");
    }

    @Test
    public void testScopeErrorRedeclarationInSameScope() {
        assertError("ScopeError",
            "def main() { INT x = 1; INT x = 2; }");
    }

    @Test
    public void testScopeErrorUndeclaredFunction() {
        assertError("ScopeError",
            "def main() { INT x = bar(); }");
    }

    // -------------------------------------------------------------------------
    // Programmes corrects (ne doivent PAS lever d'exception)
    // -------------------------------------------------------------------------

    @Test
    public void testValidProgram() {
        // programme complet tiré du code_example
        analyze(
            "final INT i = 3;\n" +
            "final FLOAT j = 3.2;\n" +
            "coll Point { INT x; INT y; }\n" +
            "INT a = 3;\n" +
            "def INT square(INT v) { return v * v; }\n" +
            "def main() {\n" +
            "    INT value = read_INT();\n" +
            "    println(square(value));\n" +
            "}"
        );
    }

    @Test
    public void testValidIntToFloatPromotion() {
        // INT est auto-promu vers FLOAT
        analyze("FLOAT x = 3;");
    }

    @Test
    public void testValidForLoop() {
        analyze("def main() { for (INT i; 1 -> 10; i + 1) { } }");
    }

    @Test
    public void testValidShadowing() {
        // variable locale peut masquer une variable globale
        analyze("INT x = 1; def main() { INT x = 2; }");
    }

    @Test
    public void testValidCollectionUsage() {
        analyze(
            "coll Point { INT x; INT y; }\n" +
            "def main() {\n" +
            "    Point p = Point(1, 2);\n" +
            "    INT xVal = p.x;\n" +
            "}"
        );
    }
}
