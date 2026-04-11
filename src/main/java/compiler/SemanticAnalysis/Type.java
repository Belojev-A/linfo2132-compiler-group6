package compiler.SemanticAnalysis;

import java.util.Objects;

/**
 * Représente un type du langage : INT, FLOAT, BOOL, STRING, ARRAY, COLLECTION, void.
 */
public class Type {

    public enum Kind { INT, FLOAT, BOOL, STRING, ARRAY, COLLECTION, VOID }

    // instances singleton pour les types de base
    public static final Type INT    = new Type(Kind.INT,    null, null);
    public static final Type FLOAT  = new Type(Kind.FLOAT,  null, null);
    public static final Type BOOL   = new Type(Kind.BOOL,   null, null);
    public static final Type STRING = new Type(Kind.STRING, null, null);
    public static final Type VOID   = new Type(Kind.VOID,   null, null);

    public final Kind kind;
    public final Type elementType;      // pour ARRAY : type des éléments
    public final String collectionName; // pour COLLECTION : nom de la collection

    private Type(Kind kind, Type elementType, String collectionName) {
        this.kind = kind;
        this.elementType = elementType;
        this.collectionName = collectionName;
    }

    public static Type arrayOf(Type elem) {
        return new Type(Kind.ARRAY, elem, null);
    }

    public static Type collectionOf(String name) {
        return new Type(Kind.COLLECTION, null, name);
    }

    public boolean isNumeric() {
        return kind == Kind.INT || kind == Kind.FLOAT;
    }

    // INT est assignable à FLOAT (promotion automatique)
    public boolean isAssignableTo(Type target) {
        if (this.equals(target)) return true;
        if (this.kind == Kind.INT && target.kind == Kind.FLOAT) return true;
        return false;
    }

    // résultat d'une opération numérique : INT op FLOAT = FLOAT
    public static Type numericResult(Type a, Type b) {
        if (a.kind == Kind.FLOAT || b.kind == Kind.FLOAT) return FLOAT;
        return INT;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Type)) return false;
        Type t = (Type) o;
        if (kind != t.kind) return false;
        if (kind == Kind.ARRAY)      return Objects.equals(elementType, t.elementType);
        if (kind == Kind.COLLECTION) return Objects.equals(collectionName, t.collectionName);
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, elementType, collectionName);
    }

    @Override
    public String toString() {
        switch (kind) {
            case INT:        return "INT";
            case FLOAT:      return "FLOAT";
            case BOOL:       return "BOOL";
            case STRING:     return "STRING";
            case VOID:       return "void";
            case ARRAY:      return elementType + "[]";
            case COLLECTION: return collectionName;
            default:         return "?";
        }
    }
}
