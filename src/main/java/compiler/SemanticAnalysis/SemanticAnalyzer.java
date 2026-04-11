package compiler.SemanticAnalysis;

import compiler.Parser.ast.ASTNode;
import compiler.Parser.ast.LeafNode;

import java.util.*;

/**
 * Analyse sémantique : parcours de l'AST en deux passes.
 *  - Passe 1 : enregistre collections et signatures de fonctions (forward refs)
 *  - Passe 2 : vérifie types, portées, retours, conditions...
 */
public class SemanticAnalyzer {

    private SymbolTable globalTable;
    private Type currentReturnType; // type de retour de la fonction en cours

    public void analyze(ASTNode root) {
        globalTable = new SymbolTable(null);
        registerBuiltins();

        // passe 1 : collections puis fonctions (pour les forward refs)
        for (ASTNode child : root.getChildren()) {
            if (child.getLabel().equals("CollDecl"))
                registerCollDecl(child);
        }
        for (ASTNode child : root.getChildren()) {
            if (child.getLabel().equals("FunctionDef"))
                registerFunctionSignature(child);
        }

        // passe 2 : vérification complète dans l'ordre
        for (ASTNode child : root.getChildren()) {
            switch (child.getLabel()) {
                case "ConstDecl":   checkConstDecl(child);          break;
                case "VarDecl":     checkVarDecl(child, globalTable); break;
                case "CollDecl":    break; // déjà enregistré
                case "FunctionDef": checkFunctionDef(child);         break;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Enregistrement des built-ins
    // -------------------------------------------------------------------------

    private void registerBuiltins() {
        def("not",       SymbolInfo.function(Type.BOOL,   list(Type.BOOL),  list("b")));
        def("str",       SymbolInfo.function(Type.STRING, list(Type.INT),   list("c")));
        def("floor",     SymbolInfo.function(Type.INT,    list(Type.FLOAT), list("x")));
        def("ceil",      SymbolInfo.function(Type.INT,    list(Type.FLOAT), list("x")));
        def("read_INT",    SymbolInfo.function(Type.INT,    Collections.emptyList(), Collections.emptyList()));
        def("read_FLOAT",  SymbolInfo.function(Type.FLOAT,  Collections.emptyList(), Collections.emptyList()));
        def("read_STRING", SymbolInfo.function(Type.STRING, Collections.emptyList(), Collections.emptyList()));
        def("print_INT",   SymbolInfo.function(Type.VOID,   list(Type.INT),   list("x")));
        def("print_FLOAT", SymbolInfo.function(Type.VOID,   list(Type.FLOAT), list("x")));
        // length, print, println : paramTypes null = accepte n'importe quoi (variadic)
        def("length",  SymbolInfo.function(Type.INT,  null, null));
        def("print",   SymbolInfo.function(Type.VOID, null, null));
        def("println", SymbolInfo.function(Type.VOID, null, null));
    }

    private void def(String name, SymbolInfo info) { globalTable.define(name, info); }

    @SafeVarargs
    private static <T> List<T> list(T... items) { return Arrays.asList(items); }

    // -------------------------------------------------------------------------
    // Passe 1 : enregistrement collections et fonctions
    // -------------------------------------------------------------------------

    private void registerCollDecl(ASTNode node) {
        int line = node.getLine();
        String name = leafVal(node, 0);

        if (!Character.isUpperCase(name.charAt(0)))
            throw new SemanticException("CollectionError: le nom '" + name + "' doit commencer par une majuscule", line);

        if (isBaseTypeName(name))
            throw new SemanticException("CollectionError: '" + name + "' est un type de base réservé", line);

        if (globalTable.isDefined(name))
            throw new SemanticException("CollectionError: '" + name + "' est déjà défini", line);

        Map<String, Type> fields = new LinkedHashMap<>();
        List<String> order = new ArrayList<>();
        for (int i = 1; i < node.getChildren().size(); i++) {
            ASTNode field = node.getChildren().get(i);
            String typeName  = leafVal(field, 0);
            String fieldName = leafVal(field, 1);
            fields.put(fieldName, parseTypeStr(typeName, field.getLine()));
            order.add(fieldName);
        }
        globalTable.define(name, SymbolInfo.collection(fields, order));
    }

    private void registerFunctionSignature(ASTNode node) {
        int line = node.getLine();
        String retTypeName = leafVal(node, 0);
        String funcName    = leafVal(node, 1);
        ASTNode paramList  = node.getChildren().get(2);

        if (globalTable.isDefined(funcName))
            throw new SemanticException("ScopeError: '" + funcName + "' est déjà défini", line);

        Type retType = retTypeName.equals("void") ? Type.VOID : parseTypeStr(retTypeName, line);

        List<Type> pTypes = new ArrayList<>();
        List<String> pNames = new ArrayList<>();
        for (ASTNode p : paramList.getChildren()) {
            pTypes.add(parseTypeStr(leafVal(p, 0), p.getLine()));
            pNames.add(leafVal(p, 1));
        }
        globalTable.define(funcName, SymbolInfo.function(retType, pTypes, pNames));
    }

    // -------------------------------------------------------------------------
    // Passe 2 : vérification des déclarations globales
    // -------------------------------------------------------------------------

    private void checkConstDecl(ASTNode node) {
        int line = node.getLine();
        String typeName = leafVal(node, 0);
        String name     = leafVal(node, 1);
        ASTNode expr    = node.getChildren().get(2);

        Type declType = parseTypeStr(typeName, line);

        // seuls les types de base sont autorisés pour les constantes
        if (declType.kind == Type.Kind.ARRAY || declType.kind == Type.Kind.COLLECTION)
            throw new SemanticException("TypeError: les constantes doivent être de type de base", line);

        if (globalTable.isDefined(name))
            throw new SemanticException("ScopeError: '" + name + "' est déjà déclaré", line);

        Type exprType = typeOf(expr, globalTable);
        if (!exprType.isAssignableTo(declType))
            throw new SemanticException("TypeError: impossible d'assigner " + exprType + " à " + declType, line);

        globalTable.define(name, SymbolInfo.constant(declType));
    }

    private void checkVarDecl(ASTNode node, SymbolTable table) {
        int line    = node.getLine();
        String typeName = leafVal(node, 0);
        String name     = leafVal(node, 1);

        Type declType = parseTypeStr(typeName, line);

        if (table.isDefined(name))
            throw new SemanticException("ScopeError: '" + name + "' est déjà déclaré dans cette portée", line);

        if (node.getChildren().size() > 2) {
            Type exprType = typeOf(node.getChildren().get(2), table);
            if (!exprType.isAssignableTo(declType))
                throw new SemanticException("TypeError: impossible d'assigner " + exprType + " à " + declType, line);
        }

        table.define(name, SymbolInfo.variable(declType));
    }

    private void checkFunctionDef(ASTNode node) {
        int line = node.getLine();
        String retTypeName = leafVal(node, 0);
        ASTNode paramList  = node.getChildren().get(2);
        ASTNode block      = node.getChildren().get(3);

        currentReturnType = retTypeName.equals("void") ? Type.VOID : parseTypeStr(retTypeName, line);

        SymbolTable localTable = new SymbolTable(globalTable);
        for (ASTNode p : paramList.getChildren()) {
            Type pType  = parseTypeStr(leafVal(p, 0), p.getLine());
            String pName = leafVal(p, 1);
            localTable.define(pName, SymbolInfo.variable(pType));
        }

        checkBlock(block, localTable);
    }

    // -------------------------------------------------------------------------
    // Vérification des blocs et statements
    // -------------------------------------------------------------------------

    private void checkBlock(ASTNode block, SymbolTable table) {
        for (ASTNode stmt : block.getChildren())
            checkStatement(stmt, table);
    }

    private void checkStatement(ASTNode node, SymbolTable table) {
        switch (node.getLabel()) {
            case "VarDecl":    checkVarDecl(node, table);          break;
            case "AssignStmt": checkAssignStmt(node, table);       break;
            case "ExprStmt":   typeOf(node.getChildren().get(0), table); break;
            case "IfStmt":     checkIfStmt(node, table);           break;
            case "WhileStmt":  checkWhileStmt(node, table);        break;
            case "ForStmt":    checkForStmt(node, table);          break;
            case "ReturnStmt": checkReturnStmt(node, table);       break;
            default:
                throw new SemanticException("Statement inconnu : " + node.getLabel(), node.getLine());
        }
    }

    private void checkAssignStmt(ASTNode node, SymbolTable table) {
        int line  = node.getLine();
        ASTNode lhs = node.getChildren().get(0);
        ASTNode rhs = node.getChildren().get(1);

        Type lhsType = typeOfLvalue(lhs, table);
        Type rhsType = typeOf(rhs, table);

        // vérifier que l'on n'assigne pas à une constante
        if (lhs instanceof LeafNode && lhs.getLabel().equals("Identifier")) {
            SymbolInfo info = table.lookup(((LeafNode) lhs).getValue());
            if (info != null && info.isConst)
                throw new SemanticException("TypeError: impossible d'assigner à la constante '" + ((LeafNode) lhs).getValue() + "'", line);
        }

        if (!rhsType.isAssignableTo(lhsType))
            throw new SemanticException("TypeError: impossible d'assigner " + rhsType + " à " + lhsType, line);
    }

    private void checkIfStmt(ASTNode node, SymbolTable table) {
        int line = node.getLine();
        Type condType = typeOf(node.getChildren().get(0), table);
        if (!condType.equals(Type.BOOL))
            throw new SemanticException("MissingConditionError: la condition du if doit être BOOL, reçu " + condType, line);

        checkBlock(node.getChildren().get(1), new SymbolTable(table));
        if (node.getChildren().size() > 2)
            checkBlock(node.getChildren().get(2), new SymbolTable(table));
    }

    private void checkWhileStmt(ASTNode node, SymbolTable table) {
        int line = node.getLine();
        Type condType = typeOf(node.getChildren().get(0), table);
        if (!condType.equals(Type.BOOL))
            throw new SemanticException("MissingConditionError: la condition du while doit être BOOL, reçu " + condType, line);

        checkBlock(node.getChildren().get(1), new SymbolTable(table));
    }

    private void checkForStmt(ASTNode node, SymbolTable table) {
        int line = node.getLine();
        // enfants : ForVar, startExpr, endExpr, stepExpr, Block
        ASTNode forVar   = node.getChildren().get(0);
        ASTNode startExpr = node.getChildren().get(1);
        ASTNode endExpr   = node.getChildren().get(2);
        ASTNode stepExpr  = node.getChildren().get(3);
        ASTNode block     = node.getChildren().get(4);

        SymbolTable forScope = new SymbolTable(table);

        if (forVar.getChildren().size() == 2) {
            // for (INT i; ...)
            Type varType = parseTypeStr(leafVal(forVar, 0), line);
            String varName = leafVal(forVar, 1);
            forScope.define(varName, SymbolInfo.variable(varType));
        } else {
            // for (i; ...) — variable déjà déclarée
            String varName = leafVal(forVar, 0);
            if (table.lookup(varName) == null)
                throw new SemanticException("ScopeError: variable '" + varName + "' non déclarée", line);
        }

        Type startType = typeOf(startExpr, forScope);
        Type endType   = typeOf(endExpr,   forScope);
        if (!startType.isNumeric())
            throw new SemanticException("TypeError: borne de début du for doit être numérique, reçu " + startType, line);
        if (!endType.isNumeric())
            throw new SemanticException("TypeError: borne de fin du for doit être numérique, reçu " + endType, line);

        typeOf(stepExpr, forScope);
        checkBlock(block, forScope);
    }

    private void checkReturnStmt(ASTNode node, SymbolTable table) {
        int line = node.getLine();
        if (node.getChildren().isEmpty()) {
            if (currentReturnType != null && !currentReturnType.equals(Type.VOID))
                throw new SemanticException("ReturnError: la fonction doit retourner " + currentReturnType + " mais retourne void", line);
        } else {
            Type exprType = typeOf(node.getChildren().get(0), table);
            if (currentReturnType == null || currentReturnType.equals(Type.VOID))
                throw new SemanticException("ReturnError: fonction void ne peut pas retourner de valeur", line);
            if (!exprType.isAssignableTo(currentReturnType))
                throw new SemanticException("ReturnError: retourne " + exprType + " mais la fonction attend " + currentReturnType, line);
        }
    }

    // -------------------------------------------------------------------------
    // Inférence de type des expressions
    // -------------------------------------------------------------------------

    private Type typeOf(ASTNode expr, SymbolTable table) {
        int line = expr.getLine();
        String label = expr.getLabel();

        if (expr instanceof LeafNode) {
            LeafNode leaf = (LeafNode) expr;
            switch (label) {
                case "Integer":      return Type.INT;
                case "FloatLiteral": return Type.FLOAT;
                case "BoolLiteral":  return Type.BOOL;
                case "String":       return Type.STRING;
                case "Identifier": {
                    String name = leaf.getValue();
                    SymbolInfo info = table.lookup(name);
                    if (info == null)
                        throw new SemanticException("ScopeError: identifiant '" + name + "' non déclaré", line);
                    if (info.kind == SymbolInfo.Kind.FUNCTION)
                        throw new SemanticException("ScopeError: '" + name + "' est une fonction, pas une variable", line);
                    if (info.kind == SymbolInfo.Kind.COLLECTION)
                        throw new SemanticException("ScopeError: '" + name + "' est un type collection, pas une variable", line);
                    return info.type;
                }
                default:
                    throw new SemanticException("Noeud feuille inconnu : " + label, line);
            }
        }

        if (label.startsWith("BinaryExpr: "))
            return typeOfBinaryExpr(expr, table);

        if (label.equals("UnaryExpr: -")) {
            Type inner = typeOf(expr.getChildren().get(0), table);
            if (!inner.isNumeric())
                throw new SemanticException("OperatorError: '-' unaire requiert un type numérique, reçu " + inner, line);
            return inner;
        }

        switch (label) {
            case "FunctionCall":    return typeOfFunctionCall(expr, table);
            case "CollConstructor": return typeOfCollConstructor(expr, table);
            case "ArrayCreation":   return typeOfArrayCreation(expr, table);
            case "FieldAccess":     return typeOfFieldAccess(expr, table);
            case "ArrayAccess":     return typeOfArrayAccess(expr, table);
            default:
                throw new SemanticException("Expression inconnue : " + label, line);
        }
    }

    private Type typeOfBinaryExpr(ASTNode expr, SymbolTable table) {
        int line = expr.getLine();
        String op = expr.getLabel().substring("BinaryExpr: ".length());
        Type left  = typeOf(expr.getChildren().get(0), table);
        Type right = typeOf(expr.getChildren().get(1), table);

        switch (op) {
            case "+":
                if (left.equals(Type.STRING) && right.equals(Type.STRING)) return Type.STRING;
                if (left.isNumeric() && right.isNumeric()) return Type.numericResult(left, right);
                throw new SemanticException("OperatorError: '+' requiert deux numériques ou deux STRING, reçu " + left + " et " + right, line);
            case "-": case "*": case "/":
                if (left.isNumeric() && right.isNumeric()) return Type.numericResult(left, right);
                throw new SemanticException("OperatorError: '" + op + "' requiert des types numériques, reçu " + left + " et " + right, line);
            case "%":
                if (left.equals(Type.INT) && right.equals(Type.INT)) return Type.INT;
                throw new SemanticException("OperatorError: '%' requiert INT et INT, reçu " + left + " et " + right, line);
            case "EQUAL": case "NOT_EQUAL":
                if (left.equals(right) || left.isAssignableTo(right) || right.isAssignableTo(left))
                    return Type.BOOL;
                throw new SemanticException("OperatorError: comparaison d'égalité entre types incompatibles " + left + " et " + right, line);
            case "LT": case "GT": case "LE": case "GE":
                if (left.isNumeric() && right.isNumeric()) return Type.BOOL;
                throw new SemanticException("OperatorError: comparaison requiert des types numériques, reçu " + left + " et " + right, line);
            case "&&": case "||":
                if (left.equals(Type.BOOL) && right.equals(Type.BOOL)) return Type.BOOL;
                throw new SemanticException("OperatorError: '" + op + "' requiert BOOL et BOOL, reçu " + left + " et " + right, line);
            default:
                throw new SemanticException("OperatorError: opérateur binaire inconnu '" + op + "'", line);
        }
    }

    private Type typeOfFunctionCall(ASTNode expr, SymbolTable table) {
        int line = expr.getLine();
        String funcName = leafVal(expr, 0);
        ASTNode argList = expr.getChildren().get(1);

        SymbolInfo info = table.lookup(funcName);
        if (info == null || info.kind != SymbolInfo.Kind.FUNCTION)
            throw new SemanticException("ScopeError: fonction '" + funcName + "' non déclarée", line);

        // variadic (print, println, length) : paramTypes == null
        if (info.paramTypes == null) {
            // length : doit recevoir STRING ou ARRAY
            if (funcName.equals("length")) {
                if (argList.getChildren().size() != 1)
                    throw new SemanticException("ArgumentError: 'length' attend 1 argument", line);
                Type argType = typeOf(argList.getChildren().get(0), table);
                if (!argType.equals(Type.STRING) && argType.kind != Type.Kind.ARRAY)
                    throw new SemanticException("ArgumentError: 'length' attend STRING ou ARRAY, reçu " + argType, line);
            }
            return info.type;
        }

        List<ASTNode> args = argList.getChildren();
        if (args.size() != info.paramTypes.size())
            throw new SemanticException("ArgumentError: '" + funcName + "' attend " + info.paramTypes.size() + " argument(s), reçu " + args.size(), line);

        for (int i = 0; i < args.size(); i++) {
            Type argType = typeOf(args.get(i), table);
            if (!argType.isAssignableTo(info.paramTypes.get(i)))
                throw new SemanticException("ArgumentError: argument " + (i + 1) + " de '" + funcName + "' attend " + info.paramTypes.get(i) + ", reçu " + argType, line);
        }

        return info.type;
    }

    private Type typeOfCollConstructor(ASTNode expr, SymbolTable table) {
        int line = expr.getLine();
        String collName = leafVal(expr, 0);
        ASTNode argList = expr.getChildren().get(1);

        SymbolInfo collInfo = globalTable.lookup(collName);
        if (collInfo == null || collInfo.kind != SymbolInfo.Kind.COLLECTION)
            throw new SemanticException("CollectionError: collection '" + collName + "' non définie", line);

        List<ASTNode> args = argList.getChildren();
        if (args.size() != collInfo.fieldOrder.size())
            throw new SemanticException("ArgumentError: constructeur '" + collName + "' attend " + collInfo.fieldOrder.size() + " argument(s), reçu " + args.size(), line);

        for (int i = 0; i < args.size(); i++) {
            String fieldName = collInfo.fieldOrder.get(i);
            Type fieldType   = collInfo.fields.get(fieldName);
            Type argType     = typeOf(args.get(i), table);
            if (!argType.isAssignableTo(fieldType))
                throw new SemanticException("ArgumentError: champ '" + fieldName + "' de '" + collName + "' attend " + fieldType + ", reçu " + argType, line);
        }

        return Type.collectionOf(collName);
    }

    private Type typeOfArrayCreation(ASTNode expr, SymbolTable table) {
        int line = expr.getLine();
        Type elemType = parseTypeStr(leafVal(expr, 0), line);
        Type sizeType = typeOf(expr.getChildren().get(1), table);
        if (!sizeType.equals(Type.INT))
            throw new SemanticException("TypeError: la taille du tableau doit être INT, reçu " + sizeType, line);
        return Type.arrayOf(elemType);
    }

    private Type typeOfFieldAccess(ASTNode expr, SymbolTable table) {
        int line = expr.getLine();
        Type objType = typeOf(expr.getChildren().get(0), table);
        String fieldName = leafVal(expr, 1);

        if (objType.kind != Type.Kind.COLLECTION)
            throw new SemanticException("TypeError: accès à un champ sur un type non-collection " + objType, line);

        SymbolInfo collInfo = globalTable.lookup(objType.collectionName);
        if (collInfo == null || collInfo.kind != SymbolInfo.Kind.COLLECTION)
            throw new SemanticException("CollectionError: type collection '" + objType.collectionName + "' non défini", line);

        Type fieldType = collInfo.fields.get(fieldName);
        if (fieldType == null)
            throw new SemanticException("TypeError: la collection '" + objType.collectionName + "' n'a pas de champ '" + fieldName + "'", line);

        return fieldType;
    }

    private Type typeOfArrayAccess(ASTNode expr, SymbolTable table) {
        int line = expr.getLine();
        Type arrayType = typeOf(expr.getChildren().get(0), table);
        Type indexType = typeOf(expr.getChildren().get(1), table);

        if (!indexType.equals(Type.INT))
            throw new SemanticException("TypeError: l'index doit être INT, reçu " + indexType, line);

        // STRING[i] retourne INT (caractère)
        if (arrayType.equals(Type.STRING)) return Type.INT;

        if (arrayType.kind != Type.Kind.ARRAY)
            throw new SemanticException("TypeError: accès par index sur un type non-tableau " + arrayType, line);

        return arrayType.elementType;
    }

    // type d'un lvalue (côté gauche d'une assignation)
    private Type typeOfLvalue(ASTNode node, SymbolTable table) {
        int line = node.getLine();
        if (node instanceof LeafNode && node.getLabel().equals("Identifier")) {
            String name = ((LeafNode) node).getValue();
            SymbolInfo info = table.lookup(name);
            if (info == null)
                throw new SemanticException("ScopeError: variable '" + name + "' non déclarée", line);
            return info.type;
        }
        if (node.getLabel().equals("FieldAccess")) return typeOfFieldAccess(node, table);
        if (node.getLabel().equals("ArrayAccess"))  return typeOfArrayAccess(node, table);
        throw new SemanticException("TypeError: lvalue invalide", line);
    }

    // -------------------------------------------------------------------------
    // Utilitaires
    // -------------------------------------------------------------------------

    // valeur d'un LeafNode enfant à l'index i
    private String leafVal(ASTNode node, int i) {
        return ((LeafNode) node.getChildren().get(i)).getValue();
    }

    private Type parseTypeStr(String typeStr, int line) {
        if (typeStr.endsWith("[]")) {
            String base = typeStr.substring(0, typeStr.length() - 2);
            return Type.arrayOf(parseBaseType(base, line));
        }
        return parseBaseType(typeStr, line);
    }

    private Type parseBaseType(String name, int line) {
        switch (name) {
            case "INT":    return Type.INT;
            case "FLOAT":  return Type.FLOAT;
            case "BOOL":   return Type.BOOL;
            case "STRING": return Type.STRING;
            default:
                SymbolInfo info = globalTable.lookup(name);
                if (info == null || info.kind != SymbolInfo.Kind.COLLECTION)
                    throw new SemanticException("CollectionError: type '" + name + "' non défini", line);
                return Type.collectionOf(name);
        }
    }

    private boolean isBaseTypeName(String name) {
        return name.equals("INT") || name.equals("FLOAT") ||
               name.equals("BOOL") || name.equals("STRING") || name.equals("ARRAY");
    }
}
