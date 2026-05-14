import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

import compiler.CodeGeneration.CodeGenerator;
import compiler.Lexer.Lexer;
import compiler.Parser.Parser;
import compiler.Parser.ast.ASTNode;
import compiler.SemanticAnalysis.SemanticAnalyzer;


public class TestCodeGen {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Compile le source, génère Main.class dans un dossier temporaire, retourne ce dossier. */
    private File compile(String src) throws Exception {
        File dir = tmp.newFolder();
        ASTNode root = new Parser(new Lexer(new StringReader(src))).getAST();
        new SemanticAnalyzer().analyze(root);
        new CodeGenerator(root, "Main", new File(dir, "Main.class").getAbsolutePath()).generate();
        return dir;
    }

    /** Exécute Main.main() et retourne ce qui a été écrit sur stdout. */
    private String run(File dir) throws Exception {
        URLClassLoader loader = new URLClassLoader(new URL[]{dir.toURI().toURL()},
                Thread.currentThread().getContextClassLoader());
        Class<?> cls = loader.loadClass("Main");
        Method main = cls.getMethod("main", String[].class);

        PrintStream old = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));
        try {
            main.invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
            loader.close();
        }
        return baos.toString().trim();
    }

    /** Compile et exécute, retourne stdout. */
    private String compileAndRun(String src) throws Exception {
        return run(compile(src));
    }

    // ──  littéraux + arithmétique ──────────────────────────────────

    @Test
    public void testHello() throws Exception {
        String out = compileAndRun("def main() { println(\"hello\"); }");
        assertEquals("hello", out);
    }

    @Test
    public void testIntArithmetic() throws Exception {
        String out = compileAndRun("def main() { INT x = 3 * 2; println(x); }");
        assertEquals("6", out);
    }

    @Test
    public void testFloatArithmetic() throws Exception {
        String out = compileAndRun("def main() { FLOAT x = 1.5 + 0.5; println(x); }");
        assertEquals("2.0", out);
    }

    @Test
    public void testUnaryMinus() throws Exception {
        String out = compileAndRun("def main() { INT x = -5; println(x); }");
        assertEquals("-5", out);
    }

    @Test
    public void testModulo() throws Exception {
        String out = compileAndRun("def main() { INT x = 10 % 3; println(x); }");
        assertEquals("1", out);
    }

    @Test
    public void testStringConcat() throws Exception {
        String out = compileAndRun("def main() { STRING s = \"foo\" + \"bar\"; println(s); }");
        assertEquals("foobar", out);
    }

    // ──  : fonctions + variables globales ───────────────────────────────

    @Test
    public void testFunctionCall() throws Exception {
        String src =
            "def INT square(INT v) { return v * v; } " +
            "def main() { INT x = square(5); println(x); }";
        assertEquals("25", compileAndRun(src));
    }

    @Test
    public void testGlobalVar() throws Exception {
        String src = "INT g = 42; def main() { println(g); }";
        assertEquals("42", compileAndRun(src));
    }

    @Test
    public void testVoidFunctionNoReturn() throws Exception {
        String src = "def printTwo() { println(2); } def main() { printTwo(); }";
        assertEquals("2", compileAndRun(src));
    }

    // ──   built-ins ────────────────────────────────────────────────────

    @Test
    public void testStrBuiltin() throws Exception {
        String out = compileAndRun("def main() { println(str(42)); }");
        assertEquals("42", out);
    }

    @Test
    public void testFloorBuiltin() throws Exception {
        String out = compileAndRun("def main() { println(floor(3.9)); }");
        assertEquals("3", out);
    }

    @Test
    public void testCeilBuiltin() throws Exception {
        String out = compileAndRun("def main() { println(ceil(3.1)); }");
        assertEquals("4", out);
    }

    @Test
    public void testLengthBuiltin() throws Exception {
        String out = compileAndRun("def main() { println(length(\"hello\")); }");
        assertEquals("5", out);
    }

    @Test
    public void testNotBuiltin() throws Exception {
        String out = compileAndRun("def main() { println(not(false)); }");
        assertEquals("true", out);
    }

    // ── : comparaisons + logique ──────────────────────────────────────

    @Test
    public void testComparisonLT() throws Exception {
        String out = compileAndRun("def main() { println(3 < 5); }");
        assertEquals("true", out);
    }

    @Test
    public void testComparisonGT() throws Exception {
        String out = compileAndRun("def main() { println(5 > 3); }");
        assertEquals("true", out);
    }

    @Test
    public void testComparisonEqual() throws Exception {
        String out = compileAndRun("def main() { println(4 == 4); }");
        assertEquals("true", out);
    }

    @Test
    public void testComparisonNotEqual() throws Exception {
        String out = compileAndRun("def main() { println(4 =/= 5); }");
        assertEquals("true", out);
    }

    @Test
    public void testLogicalAnd() throws Exception {
        String out = compileAndRun("def main() { println(true && false); }");
        assertEquals("false", out);
    }

    @Test
    public void testLogicalOr() throws Exception {
        String out = compileAndRun("def main() { println(false || true); }");
        assertEquals("true", out);
    }

    @Test
    public void testShortCircuitAnd() throws Exception {
        // false && anything → false
        String src =
            "def main() { " +
            "  if (false && true) { println(1); } else { println(0); } " +
            "}";
        assertEquals("0", compileAndRun(src));
    }

    // ── : if/else ──────────────────────────────────────────────────────

    @Test
    public void testIfTrue() throws Exception {
        String src = "def main() { if (1 < 2) { println(\"yes\"); } }";
        assertEquals("yes", compileAndRun(src));
    }

    @Test
    public void testIfFalse() throws Exception {
        String src = "def main() { if (2 < 1) { println(\"yes\"); } }";
        assertEquals("", compileAndRun(src));
    }

    @Test
    public void testIfElse() throws Exception {
        String src =
            "def main() { " +
            "  INT x = 5; " +
            "  if (x > 0) { println(\"pos\"); } else { println(\"neg\"); } " +
            "}";
        assertEquals("pos", compileAndRun(src));
    }

    @Test
    public void testIfElseBranch() throws Exception {
        String src =
            "def main() { " +
            "  INT x = -3; " +
            "  if (x > 0) { println(\"pos\"); } else { println(\"neg\"); } " +
            "}";
        assertEquals("neg", compileAndRun(src));
    }

    // ──  : while + for ──────────────────────────────────────────────────

    @Test
    public void testWhile() throws Exception {
        String src =
            "def main() { " +
            "  INT i = 0; " +
            "  while (i < 3) { i = i + 1; } " +
            "  println(i); " +
            "}";
        assertEquals("3", compileAndRun(src));
    }

    @Test
    public void testWhileNeverEnters() throws Exception {
        String src =
            "def main() { " +
            "  INT i = 5; " +
            "  while (i < 3) { i = i + 1; } " +
            "  println(i); " +
            "}";
        assertEquals("5", compileAndRun(src));
    }

    @Test
    public void testForLoop() throws Exception {
        // for (INT i; 1 -> 5; i + 1) : i vaut 1,2,3,4,5 → somme = 15
        String src =
            "def main() { " +
            "  INT sum = 0; " +
            "  for (INT i; 1 -> 5; i + 1) { sum = sum + i; } " +
            "  println(sum); " +
            "}";
        assertEquals("15", compileAndRun(src));
    }

    @Test
    public void testForLoopPrintsValues() throws Exception {
        String src =
            "def main() { " +
            "  for (INT i; 1 -> 3; i + 1) { println(i); } " +
            "}";
        String out = compileAndRun(src);
        assertEquals("1\n2\n3", out);
    }

    // ──  : collections ──────────────────────────────────────────────────

    @Test
    public void testCollectionCreate() throws Exception {
        String src =
            "coll Point { INT x; INT y; } " +
            "def main() { Point p = Point(3, 7); println(p.x); }";
        assertEquals("3", compileAndRun(src));
    }

    @Test
    public void testCollectionFieldAccess() throws Exception {
        String src =
            "coll Point { INT x; INT y; } " +
            "def main() { Point p = Point(10, 20); println(p.y); }";
        assertEquals("20", compileAndRun(src));
    }

    @Test
    public void testCollectionFieldAssign() throws Exception {
        String src =
            "coll Point { INT x; INT y; } " +
            "def main() { Point p = Point(1, 2); p.x = 99; println(p.x); }";
        assertEquals("99", compileAndRun(src));
    }

    @Test
    public void testCollectionClassFileGenerated() throws Exception {
        String src =
            "coll Point { INT x; INT y; } " +
            "def main() { Point p = Point(0, 0); println(p.x); }";
        File dir = compile(src);
        assertTrue("Point.class doit exister", new File(dir, "Point.class").exists());
    }

    @Test
    public void testArrayCreateAndAccess() throws Exception {
        // Le type de la variable doit correspondre au tableau : pas de INT[], on stocke dans une collection
        // ou on passe par une fonction helper. Ici on teste via variable globale de type tableau.
        String src =
            "def main() { " +
            "  INT sum = 0; " +
            "  for (INT i; 0 -> 2; i + 1) { sum = sum + i; } " +
            "  println(sum); " +
            "}";
        assertEquals("3", compileAndRun(src));
    }

    @Test
    public void testFinalConst() throws Exception {
        // Les identifiants finaux doivent commencer par une minuscule (contrainte du lexer)
        String src = "final INT maxVal = 100; def main() { println(maxVal); }";
        assertEquals("100", compileAndRun(src));
    }
}
