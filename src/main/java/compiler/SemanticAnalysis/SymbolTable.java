package compiler.SemanticAnalysis;

import java.util.HashMap;
import java.util.Map;

/**
 * Table des symboles à portée lexicale.
 * Chaque portée (fonction, bloc, for...) crée une nouvelle SymbolTable
 * liée à la portée parente.
 */
public class SymbolTable {

    private final SymbolTable parent;
    private final Map<String, SymbolInfo> entries = new HashMap<>();

    public SymbolTable(SymbolTable parent) {
        this.parent = parent;
    }

    public void define(String name, SymbolInfo info) {
        entries.put(name, info);
    }

    // cherche dans la portée courante ET les portées parentes
    public SymbolInfo lookup(String name) {
        SymbolInfo info = entries.get(name);
        if (info != null) return info;
        if (parent != null) return parent.lookup(name);
        return null;
    }

    // cherche uniquement dans la portée courante
    public boolean isDefined(String name) {
        return entries.containsKey(name);
    }

    public SymbolTable getParent() { return parent; }
}
