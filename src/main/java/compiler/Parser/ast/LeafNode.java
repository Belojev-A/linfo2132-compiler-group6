package compiler.Parser.ast;

/**
 * Noeud feuille : un noeud SANS enfants, qui porte juste une valeur.
 *
 * Exemples :
 *   - IntLiteral "3"
 *   - FloatLiteral "3.14"
 *   - StringLiteral "hello"
 *   - Identifier "x"
 *   - Type "INT"
 *   - BoolLiteral "true"
 */
public class LeafNode extends ASTNode {

    private final String value;

    public LeafNode(String label, String value, int line) {
        super(label, line);
        this.value = value;
    }

    public String getValue() { return value; }

    /**
     * Affiche le noeud feuille avec sa valeur.
     * Ex: "  Identifier: x"  ou  "  IntLiteral: 3"
     */
    @Override
    public void print(String indent) {
        System.out.println(indent + getLabel() + ": " + value);
    }

    @Override
    public String toString() {
        return getLabel() + ": " + value;
    }
}
