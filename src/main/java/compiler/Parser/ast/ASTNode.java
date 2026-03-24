package compiler.Parser.ast;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * Un noeud a :
 *   - un label (nom du noeud, ex: "VarDecl", "BinaryExpr", "IntLiteral")
 *   - une liste d'enfants (sous-noeuds)
 *   - un numéro de ligne (pour les messages d'erreur)
 */
public class ASTNode {

    private final String label;
    private final List<ASTNode> children;
    private final int line;

    public ASTNode(String label, int line) {
        this.label = label;
        this.line = line;
        this.children = new ArrayList<>();
    }

    // Ajouter un enfant
    public void addChild(ASTNode child) {
        if (child != null) {
            children.add(child);
        }
    }

    public String getLabel()         { return label; }
    public List<ASTNode> getChildren() { return children; }
    public int getLine()             { return line; }

    /**
     * Affiche l'arbre avec une indentation.
     * Ex pour "INT x = 1 + 3" :
     *
     * VarDecl
     *   Type: INT
     *   Identifier: x
     *   BinaryExpr: +
     *     IntLiteral: 1
     *     IntLiteral: 3
     *
     * @param indent  le prefixe d'espaces (on commence avec "")
     */
    public void print(String indent) {
        System.out.println(indent + label);
        for (ASTNode child : children) {
            child.print(indent + "  ");
        }
    }

    @Override
    public String toString() {
        return label;
    }
}