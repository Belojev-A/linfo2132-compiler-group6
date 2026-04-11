package compiler.SemanticAnalysis;

import java.util.List;
import java.util.Map;

/**
 * Informations stockées dans la table des symboles pour un identifiant.
 */
public class SymbolInfo {

    public enum Kind { VARIABLE, CONSTANT, FUNCTION, COLLECTION }

    public final Kind kind;
    public final Type type;           // type de la variable/constante, ou type de retour d'une fonction
    public final boolean isConst;

    public final List<Type> paramTypes;   // pour les fonctions
    public final List<String> paramNames; // pour les fonctions

    public final Map<String, Type> fields; // pour les collections : nom -> type
    public final List<String> fieldOrder;  // pour les collections : ordre des champs

    private SymbolInfo(Kind kind, Type type, boolean isConst,
                       List<Type> paramTypes, List<String> paramNames,
                       Map<String, Type> fields, List<String> fieldOrder) {
        this.kind       = kind;
        this.type       = type;
        this.isConst    = isConst;
        this.paramTypes = paramTypes;
        this.paramNames = paramNames;
        this.fields     = fields;
        this.fieldOrder = fieldOrder;
    }

    public static SymbolInfo variable(Type type) {
        return new SymbolInfo(Kind.VARIABLE, type, false, null, null, null, null);
    }

    public static SymbolInfo constant(Type type) {
        return new SymbolInfo(Kind.CONSTANT, type, true, null, null, null, null);
    }

    public static SymbolInfo function(Type returnType, List<Type> paramTypes, List<String> paramNames) {
        return new SymbolInfo(Kind.FUNCTION, returnType, false, paramTypes, paramNames, null, null);
    }

    public static SymbolInfo collection(Map<String, Type> fields, List<String> fieldOrder) {
        return new SymbolInfo(Kind.COLLECTION, null, false, null, null, fields, fieldOrder);
    }
}
