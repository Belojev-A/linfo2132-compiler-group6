package compiler.CodeGeneration;

import compiler.Parser.ast.ASTNode;
import compiler.Parser.ast.LeafNode;
import org.objectweb.asm.*;
import static org.objectweb.asm.Opcodes.*;

import java.io.*;
import java.util.*;

public class CodeGenerator {

    private final ASTNode root;
    private final String className;
    private final String outputPath;

    private ClassWriter cw;

    private final Map<String, String> funcDescriptors = new HashMap<>();
    private final Map<String, String> funcReturnTypes  = new HashMap<>();
    private final Map<String, String> globalVarTypes   = new LinkedHashMap<>();
    // types des collections : nom → liste ordonnée de (fieldName → fieldType)
    private final Map<String, LinkedHashMap<String, String>> collectionFields = new LinkedHashMap<>();
    private String currentReturnType;

    // dossier de sortie (pour écrire les .class des collections)
    private final String outputDir;

    public CodeGenerator(ASTNode root, String className, String outputPath) {
        this.root       = root;
        this.className  = className;
        this.outputPath = outputPath;
        File f = new File(outputPath);
        this.outputDir  = f.getParent() != null ? f.getParent() : ".";
    }

    public void generate() throws IOException {
        // passe 1 : collecte signatures fonctions, types globaux, déclarations de collections
        for (ASTNode child : root.getChildren()) {
            switch (child.getLabel()) {
                case "FunctionDef": {
                    String retType  = leafVal(child, 0);
                    String funcName = leafVal(child, 1);
                    ASTNode params  = child.getChildren().get(2);
                    String desc = funcName.equals("main")
                        ? "([Ljava/lang/String;)V"
                        : buildDescriptor(retType, params);
                    funcDescriptors.put(funcName, desc);
                    funcReturnTypes.put(funcName, retType);
                    break;
                }
                case "VarDecl":
                case "ConstDecl":
                    globalVarTypes.put(leafVal(child, 1), leafVal(child, 0));
                    break;
                case "CollDecl": {
                    String collName = leafVal(child, 0);
                    LinkedHashMap<String, String> fields = new LinkedHashMap<>();
                    for (int i = 1; i < child.getChildren().size(); i++) {
                        ASTNode field = child.getChildren().get(i);
                        fields.put(leafVal(field, 1), leafVal(field, 0));
                    }
                    collectionFields.put(collName, fields);
                    break;
                }
            }
        }

        // génère les .class des collections (push 8)
        for (Map.Entry<String, LinkedHashMap<String, String>> e : collectionFields.entrySet())
            generateCollectionClass(e.getKey(), e.getValue());

        // génère la classe principale
        cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(V1_8, ACC_PUBLIC, className, null, "java/lang/Object", null);

        generateGlobals();

        for (ASTNode child : root.getChildren()) {
            if (child.getLabel().equals("FunctionDef"))
                generateFunction(child);
        }

        cw.visitEnd();

        File outFile = new File(outputPath);
        if (outFile.getParentFile() != null)
            outFile.getParentFile().mkdirs();

        byte[] bytecode = cw.toByteArray();
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            fos.write(bytecode);
        }
    }

    // ── : Collections ──────────────────────────────────────────────────

    private void generateCollectionClass(String collName, LinkedHashMap<String, String> fields) throws IOException {
        ClassWriter ccw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        ccw.visit(V1_8, ACC_PUBLIC, collName, null, "java/lang/Object", null);

        // champs publics
        for (Map.Entry<String, String> f : fields.entrySet())
            ccw.visitField(ACC_PUBLIC, f.getKey(), typeDesc(f.getValue()), null, null).visitEnd();

        // constructeur avec tous les paramètres
        StringBuilder ctorDesc = new StringBuilder("(");
        for (String t : fields.values()) ctorDesc.append(typeDesc(t));
        ctorDesc.append(")V");

        MethodVisitor mv = ccw.visitMethod(ACC_PUBLIC, "<init>", ctorDesc.toString(), null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

        int slot = 1;
        for (Map.Entry<String, String> f : fields.entrySet()) {
            mv.visitVarInsn(ALOAD, 0);
            emitLoad(f.getValue(), slot++, mv);
            mv.visitFieldInsn(PUTFIELD, collName, f.getKey(), typeDesc(f.getValue()));
        }
        mv.visitInsn(RETURN);
        mv.visitMaxs(-1, -1);
        mv.visitEnd();

        // constructeur sans argument
        MethodVisitor mv0 = ccw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv0.visitCode();
        mv0.visitVarInsn(ALOAD, 0);
        mv0.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv0.visitInsn(RETURN);
        mv0.visitMaxs(-1, -1);
        mv0.visitEnd();

        ccw.visitEnd();

        File outFile = new File(outputDir, collName + ".class");
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            fos.write(ccw.toByteArray());
        }
    }

    // ── Globaux  ──────────────────────────────────────────

    private void generateGlobals() {
        // champ scanner statique pour les read_*
        cw.visitField(ACC_PRIVATE | ACC_STATIC, "__scanner", "Ljava/util/Scanner;", null, null).visitEnd();

        for (ASTNode child : root.getChildren()) {
            String lbl = child.getLabel();
            if (!lbl.equals("VarDecl") && !lbl.equals("ConstDecl")) continue;
            String typeName = leafVal(child, 0);
            String name     = leafVal(child, 1);
            cw.visitField(ACC_PUBLIC | ACC_STATIC, name, typeDesc(typeName), null, null).visitEnd();
        }

        MethodVisitor mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();

        mv.visitTypeInsn(NEW, "java/util/Scanner");
        mv.visitInsn(DUP);
        mv.visitFieldInsn(GETSTATIC, "java/lang/System", "in", "Ljava/io/InputStream;");
        mv.visitMethodInsn(INVOKESPECIAL, "java/util/Scanner", "<init>", "(Ljava/io/InputStream;)V", false);
        mv.visitFieldInsn(PUTSTATIC, className, "__scanner", "Ljava/util/Scanner;");

        Map<String, Integer> emptySlots = new HashMap<>();
        Map<String, String>  emptyTypes = new HashMap<>();
        for (ASTNode child : root.getChildren()) {
            String lbl = child.getLabel();
            if (!lbl.equals("VarDecl") && !lbl.equals("ConstDecl")) continue;
            String typeName = leafVal(child, 0);
            String name     = leafVal(child, 1);
            if (child.getChildren().size() > 2) {
                String t = generateExpr(child.getChildren().get(2), mv, emptySlots, emptyTypes);
                if (typeName.equals("FLOAT") && t.equals("I")) mv.visitInsn(I2F);
            } else {
                emitDefaultValue(typeName, mv);
            }
            mv.visitFieldInsn(PUTSTATIC, className, name, typeDesc(typeName));
        }
        mv.visitInsn(RETURN);
        mv.visitMaxs(-1, -1);
        mv.visitEnd();
    }

    // ── Fonctions (, inchangé) ──────────────────────────────────────────

    private void generateFunction(ASTNode node) {
        String retTypeName = leafVal(node, 0);
        String funcName    = leafVal(node, 1);
        ASTNode paramList  = node.getChildren().get(2);
        ASTNode block      = node.getChildren().get(3);

        currentReturnType = retTypeName;

        String jvmName    = funcName.equals("main") ? "main" : funcName;
        String descriptor = funcDescriptors.get(funcName);

        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, jvmName, descriptor, null, null);
        mv.visitCode();

        Map<String, Integer> slots    = new LinkedHashMap<>();
        Map<String, String>  varTypes = new LinkedHashMap<>();
        int[] nextSlot = { funcName.equals("main") ? 1 : 0 };

        for (ASTNode p : paramList.getChildren()) {
            String pName = leafVal(p, 1);
            String pType = leafVal(p, 0);
            slots.put(pName, nextSlot[0]++);
            varTypes.put(pName, pType);
        }

        generateBlock(block, mv, slots, nextSlot, varTypes);

        emitDefaultReturn(retTypeName, mv);
        mv.visitMaxs(-1, -1);
        mv.visitEnd();
    }

    // ── Blocs & statements ────────────────────────────────────────────────────

    void generateBlock(ASTNode block, MethodVisitor mv, Map<String, Integer> slots, int[] nextSlot, Map<String, String> varTypes) {
        for (ASTNode stmt : block.getChildren())
            generateStatement(stmt, mv, slots, nextSlot, varTypes);
    }

    private void generateStatement(ASTNode node, MethodVisitor mv, Map<String, Integer> slots, int[] nextSlot, Map<String, String> varTypes) {
        switch (node.getLabel()) {
            case "VarDecl":    generateVarDecl(node, mv, slots, nextSlot, varTypes);   break;
            case "AssignStmt": generateAssignStmt(node, mv, slots, varTypes);          break;
            case "ExprStmt":   generateExprStmt(node, mv, slots, varTypes);            break;
            case "ReturnStmt": generateReturnStmt(node, mv, slots, varTypes);          break;
            case "IfStmt":     generateIfStmt(node, mv, slots, nextSlot, varTypes);    break;  // push 6
            case "WhileStmt":  generateWhileStmt(node, mv, slots, nextSlot, varTypes); break;  // push 7
            case "ForStmt":    generateForStmt(node, mv, slots, nextSlot, varTypes);   break;  // push 7
            default:
                throw new RuntimeException("Statement inconnu : " + node.getLabel());
        }
    }

    // ── VarDecl (, inchangé) ────────────────────────────────────────────

    private void generateVarDecl(ASTNode node, MethodVisitor mv, Map<String, Integer> slots, int[] nextSlot, Map<String, String> varTypes) {
        String typeName = leafVal(node, 0);
        String name     = leafVal(node, 1);
        int slot = nextSlot[0]++;
        slots.put(name, slot);
        varTypes.put(name, typeName);

        if (node.getChildren().size() > 2) {
            String t = generateExpr(node.getChildren().get(2), mv, slots, varTypes);
            if (typeName.equals("FLOAT") && t.equals("I")) mv.visitInsn(I2F);
        } else {
            emitDefaultValue(typeName, mv);
        }
        emitStore(typeName, slot, mv);
    }

    // ── AssignStmt (, étendu pour FieldAccess/ArrayAccess) ───────────

    private void generateAssignStmt(ASTNode node, MethodVisitor mv, Map<String, Integer> slots, Map<String, String> varTypes) {
        ASTNode lhs = node.getChildren().get(0);
        ASTNode rhs = node.getChildren().get(1);

        if (lhs instanceof LeafNode) {
            String name = ((LeafNode) lhs).getValue();
            Integer slot = slots.get(name);
            if (slot != null) {
                String typeName = varTypes.get(name);
                String t = generateExpr(rhs, mv, slots, varTypes);
                if (typeName.equals("FLOAT") && t.equals("I")) mv.visitInsn(I2F);
                emitStore(typeName, slot, mv);
            } else {
                String gType = globalVarTypes.get(name);
                if (gType == null) return;
                String t = generateExpr(rhs, mv, slots, varTypes);
                if (gType.equals("FLOAT") && t.equals("I")) mv.visitInsn(I2F);
                mv.visitFieldInsn(PUTSTATIC, className, name, typeDesc(gType));
            }
        } else if (lhs.getLabel().equals("FieldAccess")) {
            // obj.field = expr  (push 8)
            ASTNode objNode  = lhs.getChildren().get(0);
            String fieldName = ((LeafNode) lhs.getChildren().get(1)).getValue();
            String collType  = resolveType(objNode, varTypes);
            generateExpr(objNode, mv, slots, varTypes);
            generateExpr(rhs, mv, slots, varTypes);
            mv.visitFieldInsn(PUTFIELD, collType, fieldName,
                typeDesc(collectionFields.get(collType).get(fieldName)));
        } else if (lhs.getLabel().equals("ArrayAccess")) {
            // arr[idx] = expr  (push 8)
            ASTNode arrNode = lhs.getChildren().get(0);
            ASTNode idxNode = lhs.getChildren().get(1);
            String arrType  = resolveType(arrNode, varTypes);
            String elemType = arrType.replace("[]", "");
            generateExpr(arrNode, mv, slots, varTypes);
            generateExpr(idxNode, mv, slots, varTypes);
            generateExpr(rhs, mv, slots, varTypes);
            emitArrayStore(elemType, mv);
        }
    }

    // ── ExprStmt (, inchangé) ───────────────────────────────────────────

    private void generateExprStmt(ASTNode node, MethodVisitor mv, Map<String, Integer> slots, Map<String, String> varTypes) {
        String t = generateExpr(node.getChildren().get(0), mv, slots, varTypes);
        if (!t.equals("V")) mv.visitInsn(POP);
    }

    // ── ReturnStmt (push 3, inchangé) ─────────────────────────────────────────

    private void generateReturnStmt(ASTNode node, MethodVisitor mv, Map<String, Integer> slots, Map<String, String> varTypes) {
        if (node.getChildren().isEmpty()) {
            mv.visitInsn(RETURN);
            return;
        }
        String t = generateExpr(node.getChildren().get(0), mv, slots, varTypes);
        if (currentReturnType.equals("FLOAT") && t.equals("I")) mv.visitInsn(I2F);
        switch (currentReturnType) {
            case "INT": case "BOOL": mv.visitInsn(IRETURN); break;
            case "FLOAT":            mv.visitInsn(FRETURN); break;
            default:                 mv.visitInsn(ARETURN); break;
        }
    }

    // ──  : if/else ──────────────────────────────────────────────────────

    private void generateIfStmt(ASTNode node, MethodVisitor mv, Map<String, Integer> slots, int[] nextSlot, Map<String, String> varTypes) {
        // children : [cond, thenBlock, (elseBlock)?]
        ASTNode cond      = node.getChildren().get(0);
        ASTNode thenBlock = node.getChildren().get(1);
        boolean hasElse   = node.getChildren().size() > 2;

        Label elseLabel = new Label();
        Label endLabel  = new Label();

        generateConditionJump(cond, mv, slots, varTypes, elseLabel);
        generateBlock(thenBlock, mv, slots, nextSlot, varTypes);

        if (hasElse) {
            mv.visitJumpInsn(GOTO, endLabel);
            mv.visitLabel(elseLabel);
            generateBlock(node.getChildren().get(2), mv, slots, nextSlot, varTypes);
            mv.visitLabel(endLabel);
        } else {
            mv.visitLabel(elseLabel);
        }
    }

    // ──  : while ────────────────────────────────────────────────────────

    private void generateWhileStmt(ASTNode node, MethodVisitor mv, Map<String, Integer> slots, int[] nextSlot, Map<String, String> varTypes) {
        // children : [cond, body]
        ASTNode cond = node.getChildren().get(0);
        ASTNode body = node.getChildren().get(1);

        Label startLabel = new Label();
        Label endLabel   = new Label();

        mv.visitLabel(startLabel);
        generateConditionJump(cond, mv, slots, varTypes, endLabel);
        generateBlock(body, mv, slots, nextSlot, varTypes);
        mv.visitJumpInsn(GOTO, startLabel);
        mv.visitLabel(endLabel);
    }

    // ── : for ──────────────────────────────────────────────────────────

    private void generateForStmt(ASTNode node, MethodVisitor mv, Map<String, Integer> slots, int[] nextSlot, Map<String, String> varTypes) {
        // children : [ForVar, startExpr, endExpr, stepExpr, body]
        ASTNode forVar    = node.getChildren().get(0);
        ASTNode startExpr = node.getChildren().get(1);
        ASTNode endExpr   = node.getChildren().get(2);
        ASTNode stepExpr  = node.getChildren().get(3);
        ASTNode body      = node.getChildren().get(4);

        String varName;
        String typeName;

        ASTNode firstChild = forVar.getChildren().get(0);
        if (firstChild instanceof LeafNode && ((LeafNode) firstChild).getLabel().equals("Type")) {
            // for (INT i; ...)  — nouvelle variable
            typeName = ((LeafNode) firstChild).getValue();
            varName  = ((LeafNode) forVar.getChildren().get(1)).getValue();
            int slot = nextSlot[0]++;
            slots.put(varName, slot);
            varTypes.put(varName, typeName);
            generateExpr(startExpr, mv, slots, varTypes);
            emitStore(typeName, slot, mv);
        } else {
            // for (i; ...)  — variable déjà déclarée
            varName  = ((LeafNode) firstChild).getValue();
            typeName = varTypes.getOrDefault(varName, "INT");
            generateExpr(startExpr, mv, slots, varTypes);
            emitStore(typeName, slots.get(varName), mv);
        }

        // traduit en while : while (var <= end) { body; var = step; }
        Label startLabel = new Label();
        Label endLabel   = new Label();

        mv.visitLabel(startLabel);
        emitLoad(typeName, slots.get(varName), mv);
        generateExpr(endExpr, mv, slots, varTypes);
        mv.visitJumpInsn(IF_ICMPGE, endLabel);

        generateBlock(body, mv, slots, nextSlot, varTypes);

        // mise à jour : var = stepExpr
        generateExpr(stepExpr, mv, slots, varTypes);
        emitStore(typeName, slots.get(varName), mv);

        mv.visitJumpInsn(GOTO, startLabel);
        mv.visitLabel(endLabel);
    }

    // ── : conditions avec court-circuit ────────────────────────────────

    /**
     * Génère la condition et saute vers jumpIfFalse si elle est fausse.
     */
    private void generateConditionJump(ASTNode cond, MethodVisitor mv,
                                       Map<String, Integer> slots, Map<String, String> varTypes,
                                       Label jumpIfFalse) {
        String label = cond.getLabel();

        // court-circuit &&
        if (label.equals("BinaryExpr: &&")) {
            generateConditionJump(cond.getChildren().get(0), mv, slots, varTypes, jumpIfFalse);
            generateConditionJump(cond.getChildren().get(1), mv, slots, varTypes, jumpIfFalse);
            return;
        }

        // court-circuit ||
        if (label.equals("BinaryExpr: ||")) {
            Label skipFalse = new Label();
            generateConditionJumpTrue(cond.getChildren().get(0), mv, slots, varTypes, skipFalse);
            generateConditionJump(cond.getChildren().get(1), mv, slots, varTypes, jumpIfFalse);
            mv.visitLabel(skipFalse);
            return;
        }

        // comparaisons directes (op = nom du TokenType : EQUAL, NOT_EQUAL, LT, GT, LE, GE)
        if (label.startsWith("BinaryExpr: ")) {
            String op = label.substring("BinaryExpr: ".length());
            switch (op) {
                case "EQUAL": case "NOT_EQUAL":
                case "LT":    case "GT":
                case "LE":    case "GE": {
                    ASTNode l = cond.getChildren().get(0);
                    ASTNode r = cond.getChildren().get(1);
                    String lt = inferType(l, varTypes);
                    String rt = inferType(r, varTypes);
                    boolean isFloat = lt.equals("F") || rt.equals("F");
                    generateExpr(l, mv, slots, varTypes);
                    if (isFloat && lt.equals("I")) mv.visitInsn(I2F);
                    generateExpr(r, mv, slots, varTypes);
                    if (isFloat && rt.equals("I")) mv.visitInsn(I2F);
                    if (isFloat) {
                        mv.visitInsn(FCMPG);
                        switch (op) {
                            case "EQUAL":     mv.visitJumpInsn(IFNE,  jumpIfFalse); break;
                            case "NOT_EQUAL": mv.visitJumpInsn(IFEQ,  jumpIfFalse); break;
                            case "LT":        mv.visitJumpInsn(IFGE,  jumpIfFalse); break;
                            case "GT":        mv.visitJumpInsn(IFLE,  jumpIfFalse); break;
                            case "LE":        mv.visitJumpInsn(IFGT,  jumpIfFalse); break;
                            case "GE":        mv.visitJumpInsn(IFLT,  jumpIfFalse); break;
                        }
                    } else {
                        switch (op) {
                            case "EQUAL":     mv.visitJumpInsn(IF_ICMPNE, jumpIfFalse); break;
                            case "NOT_EQUAL": mv.visitJumpInsn(IF_ICMPEQ, jumpIfFalse); break;
                            case "LT":        mv.visitJumpInsn(IF_ICMPGE, jumpIfFalse); break;
                            case "GT":        mv.visitJumpInsn(IF_ICMPLE, jumpIfFalse); break;
                            case "LE":        mv.visitJumpInsn(IF_ICMPGT, jumpIfFalse); break;
                            case "GE":        mv.visitJumpInsn(IF_ICMPLT, jumpIfFalse); break;
                        }
                    }
                    return;
                }
            }
        }

        // expression booléenne générique : évalue et teste
        generateExpr(cond, mv, slots, varTypes);
        mv.visitJumpInsn(IFEQ, jumpIfFalse);
    }

    /** Saute vers jumpIfTrue si la condition est vraie (pour le court-circuit ||). */
    private void generateConditionJumpTrue(ASTNode cond, MethodVisitor mv,
                                           Map<String, Integer> slots, Map<String, String> varTypes,
                                           Label jumpIfTrue) {
        generateExpr(cond, mv, slots, varTypes);
        mv.visitJumpInsn(IFNE, jumpIfTrue);
    }

    // ── Expressions ───────────────────────────────────────────────────────────

    String generateExpr(ASTNode expr, MethodVisitor mv, Map<String, Integer> slots, Map<String, String> varTypes) {
        String label = expr.getLabel();

        if (expr instanceof LeafNode) {
            LeafNode leaf = (LeafNode) expr;
            switch (label) {
                case "Integer":
                    mv.visitLdcInsn(Integer.parseInt(leaf.getValue()));
                    return "I";
                case "FloatLiteral":
                    mv.visitLdcInsn(Float.parseFloat(leaf.getValue()));
                    return "F";
                case "BoolLiteral":
                    mv.visitInsn(leaf.getValue().equals("true") ? ICONST_1 : ICONST_0);
                    return "Z";
                case "String":
                    mv.visitLdcInsn(leaf.getValue());
                    return "Ljava/lang/String;";
                case "Identifier": {
                    String name = leaf.getValue();
                    Integer slot = slots.get(name);
                    if (slot != null) {
                        emitLoad(varTypes.get(name), slot, mv);
                        return typeDesc(varTypes.get(name));
                    }
                    String gType = globalVarTypes.get(name);
                    if (gType != null) {
                        mv.visitFieldInsn(GETSTATIC, className, name, typeDesc(gType));
                        return typeDesc(gType);
                    }
                    return "I";
                }
            }
        }

        if (label.startsWith("BinaryExpr: "))
            return generateBinaryExpr(expr, mv, slots, varTypes);

        if (label.equals("UnaryExpr: -")) {
            String t = generateExpr(expr.getChildren().get(0), mv, slots, varTypes);
            mv.visitInsn(t.equals("F") ? FNEG : INEG);
            return t;
        }

        if (label.equals("FunctionCall"))
            return generateFunctionCall(expr, mv, slots, varTypes);

        if (label.equals("CollConstructor"))        // push 8
            return generateCollConstructor(expr, mv, slots, varTypes);

        if (label.equals("FieldAccess"))            // push 8
            return generateFieldAccess(expr, mv, slots, varTypes);

        if (label.equals("ArrayAccess"))            // push 8
            return generateArrayAccess(expr, mv, slots, varTypes);

        if (label.equals("ArrayCreation"))          // push 8
            return generateArrayCreation(expr, mv, slots, varTypes);

        return "I";
    }

    // ── Push 5 : BinaryExpr avec comparaisons et logique ─────────────────────

    private String generateBinaryExpr(ASTNode expr, MethodVisitor mv, Map<String, Integer> slots, Map<String, String> varTypes) {
        String op = expr.getLabel().substring("BinaryExpr: ".length());
        ASTNode l = expr.getChildren().get(0);
        ASTNode r = expr.getChildren().get(1);

        // court-circuit && et ||  → retournent un BOOL (int 0/1)
        if (op.equals("&&") || op.equals("||")) {
            Label falseLabel = new Label();
            Label endLabel   = new Label();
            generateConditionJump(expr, mv, slots, varTypes, falseLabel);
            mv.visitInsn(ICONST_1);
            mv.visitJumpInsn(GOTO, endLabel);
            mv.visitLabel(falseLabel);
            mv.visitInsn(ICONST_0);
            mv.visitLabel(endLabel);
            return "Z";
        }

        // comparaisons → retournent un BOOL (int 0/1)
        switch (op) {
            case "EQUAL": case "NOT_EQUAL":
            case "LT":    case "GT":
            case "LE":    case "GE": {
                Label falseLabel = new Label();
                Label endLabel   = new Label();
                generateConditionJump(expr, mv, slots, varTypes, falseLabel);
                mv.visitInsn(ICONST_1);
                mv.visitJumpInsn(GOTO, endLabel);
                mv.visitLabel(falseLabel);
                mv.visitInsn(ICONST_0);
                mv.visitLabel(endLabel);
                return "Z";
            }
        }

        // arithmétique (push 2, inchangé)
        String lt = inferType(l, varTypes);
        String rt = inferType(r, varTypes);
        boolean isFloat = lt.equals("F") || rt.equals("F");

        // concaténation de strings
        if (lt.equals("Ljava/lang/String;") && op.equals("+")) {
            generateExpr(l, mv, slots, varTypes);
            generateExpr(r, mv, slots, varTypes);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "concat",
                "(Ljava/lang/String;)Ljava/lang/String;", false);
            return "Ljava/lang/String;";
        }

        generateExpr(l, mv, slots, varTypes);
        if (isFloat && lt.equals("I")) mv.visitInsn(I2F);
        generateExpr(r, mv, slots, varTypes);
        if (isFloat && rt.equals("I")) mv.visitInsn(I2F);

        switch (op) {
            case "+": mv.visitInsn(isFloat ? FADD : IADD); return isFloat ? "F" : "I";
            case "-": mv.visitInsn(isFloat ? FSUB : ISUB); return isFloat ? "F" : "I";
            case "*": mv.visitInsn(isFloat ? FMUL : IMUL); return isFloat ? "F" : "I";
            case "/": mv.visitInsn(isFloat ? FDIV : IDIV); return isFloat ? "F" : "I";
            case "%": mv.visitInsn(IREM); return "I";
            default:  return "I";
        }
    }

    // ── : CollConstructor ──────────────────────────────────────────────

    private String generateCollConstructor(ASTNode expr, MethodVisitor mv, Map<String, Integer> slots, Map<String, String> varTypes) {
        String collName = leafVal(expr, 0);
        ASTNode argList = expr.getChildren().get(1);

        StringBuilder ctorDesc = new StringBuilder("(");
        LinkedHashMap<String, String> fields = collectionFields.get(collName);
        if (fields != null)
            for (String t : fields.values()) ctorDesc.append(typeDesc(t));
        ctorDesc.append(")V");

        mv.visitTypeInsn(NEW, collName);
        mv.visitInsn(DUP);
        for (ASTNode arg : argList.getChildren())
            generateExpr(arg, mv, slots, varTypes);
        mv.visitMethodInsn(INVOKESPECIAL, collName, "<init>", ctorDesc.toString(), false);
        return "L" + collName + ";";
    }

    // ── Push 8 : FieldAccess ──────────────────────────────────────────────────

    private String generateFieldAccess(ASTNode expr, MethodVisitor mv, Map<String, Integer> slots, Map<String, String> varTypes) {
        ASTNode obj      = expr.getChildren().get(0);
        String fieldName = ((LeafNode) expr.getChildren().get(1)).getValue();
        String collType  = resolveType(obj, varTypes);

        generateExpr(obj, mv, slots, varTypes);
        LinkedHashMap<String, String> fields = collectionFields.get(collType);
        String fieldType = (fields != null && fields.containsKey(fieldName)) ? fields.get(fieldName) : "INT";
        mv.visitFieldInsn(GETFIELD, collType, fieldName, typeDesc(fieldType));
        return typeDesc(fieldType);
    }

    // ── Push 8 : ArrayAccess ──────────────────────────────────────────────────

    private String generateArrayAccess(ASTNode expr, MethodVisitor mv, Map<String, Integer> slots, Map<String, String> varTypes) {
        ASTNode arr     = expr.getChildren().get(0);
        ASTNode idx     = expr.getChildren().get(1);
        String arrType  = resolveType(arr, varTypes);
        String elemType = arrType.replace("[]", "");

        generateExpr(arr, mv, slots, varTypes);
        generateExpr(idx, mv, slots, varTypes);
        emitArrayLoad(elemType, mv);
        return typeDesc(elemType);
    }

    // ── Push 8 : ArrayCreation ────────────────────────────────────────────────

    private String generateArrayCreation(ASTNode expr, MethodVisitor mv, Map<String, Integer> slots, Map<String, String> varTypes) {
        String elemType  = leafVal(expr, 0);
        ASTNode sizeExpr = expr.getChildren().get(1);
        generateExpr(sizeExpr, mv, slots, varTypes);
        switch (elemType) {
            case "INT":    mv.visitIntInsn(NEWARRAY, T_INT);     break;
            case "FLOAT":  mv.visitIntInsn(NEWARRAY, T_FLOAT);   break;
            case "BOOL":   mv.visitIntInsn(NEWARRAY, T_BOOLEAN); break;
            default:       mv.visitTypeInsn(ANEWARRAY, elemType.equals("STRING") ? "java/lang/String" : elemType); break;
        }
        return typeDesc(elemType + "[]");
    }

    // ── Appels de fonctions (, inchangé) ─────────────────────────────

    private String generateFunctionCall(ASTNode expr, MethodVisitor mv, Map<String, Integer> slots, Map<String, String> varTypes) {
        String funcName = leafVal(expr, 0);
        ASTNode argList = expr.getChildren().get(1);

        if (isBuiltin(funcName)) return generateBuiltin(funcName, argList, mv, slots, varTypes);

        for (ASTNode arg : argList.getChildren())
            generateExpr(arg, mv, slots, varTypes);

        mv.visitMethodInsn(INVOKESTATIC, className, funcName, funcDescriptors.get(funcName), false);

        String retType = funcReturnTypes.get(funcName);
        return (retType == null || retType.equals("void")) ? "V" : typeDesc(retType);
    }

    private boolean isBuiltin(String name) {
        switch (name) {
            case "println": case "print": case "write":
            case "print_INT": case "print_FLOAT":
            case "read_INT": case "read_FLOAT": case "read_STRING":
            case "str": case "floor": case "ceil": case "length": case "not":
                return true;
            default: return false;
        }
    }

    private String builtinReturnType(String name) {
        switch (name) {
            case "read_INT": case "floor": case "ceil": case "length": return "I";
            case "not":       return "Z";
            case "read_FLOAT": return "F";
            case "read_STRING": case "str": return "Ljava/lang/String;";
            default: return "V";
        }
    }

    private String generateBuiltin(String name, ASTNode argList, MethodVisitor mv,
                                   Map<String, Integer> slots, Map<String, String> varTypes) {
        List<ASTNode> args = argList.getChildren();
        switch (name) {
            case "println": case "print": case "write": {
                String methodName = name.equals("print") ? "print" : "println";
                if (args.isEmpty()) {
                    mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", methodName, "()V", false);
                    return "V";
                }
                ASTNode arg = args.get(0);
                String argType = inferType(arg, varTypes);
                mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                generateExpr(arg, mv, slots, varTypes);
                String desc;
                switch (argType) {
                    case "F":                  desc = "(F)V"; break;
                    case "Z":                  desc = "(Z)V"; break;
                    case "Ljava/lang/String;": desc = "(Ljava/lang/String;)V"; break;
                    default:                   desc = "(I)V"; break;
                }
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", methodName, desc, false);
                return "V";
            }
            case "print_INT": {
                mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                generateExpr(args.get(0), mv, slots, varTypes);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "print", "(I)V", false);
                return "V";
            }
            case "print_FLOAT": {
                mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                generateExpr(args.get(0), mv, slots, varTypes);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "print", "(F)V", false);
                return "V";
            }
            case "read_INT": {
                mv.visitFieldInsn(GETSTATIC, className, "__scanner", "Ljava/util/Scanner;");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/Scanner", "nextInt", "()I", false);
                return "I";
            }
            case "read_FLOAT": {
                mv.visitFieldInsn(GETSTATIC, className, "__scanner", "Ljava/util/Scanner;");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/Scanner", "nextFloat", "()F", false);
                return "F";
            }
            case "read_STRING": {
                mv.visitFieldInsn(GETSTATIC, className, "__scanner", "Ljava/util/Scanner;");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/Scanner", "next", "()Ljava/lang/String;", false);
                return "Ljava/lang/String;";
            }
            case "str": {
                String argType = inferType(args.get(0), varTypes);
                generateExpr(args.get(0), mv, slots, varTypes);
                String desc = argType.equals("F") ? "(F)Ljava/lang/String;" : "(I)Ljava/lang/String;";
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", desc, false);
                return "Ljava/lang/String;";
            }
            case "floor": {
                generateExpr(args.get(0), mv, slots, varTypes);
                mv.visitInsn(F2D);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "floor", "(D)D", false);
                mv.visitInsn(D2I);
                return "I";
            }
            case "ceil": {
                generateExpr(args.get(0), mv, slots, varTypes);
                mv.visitInsn(F2D);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "ceil", "(D)D", false);
                mv.visitInsn(D2I);
                return "I";
            }
            case "length": {
                generateExpr(args.get(0), mv, slots, varTypes);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
                return "I";
            }
            case "not": {
                generateExpr(args.get(0), mv, slots, varTypes);
                mv.visitInsn(ICONST_1);
                mv.visitInsn(IXOR);
                return "Z";
            }
            default: return "V";
        }
    }

    // ── Inférence de types (étendue ) ────────────────────────────────

    String inferType(ASTNode expr, Map<String, String> varTypes) {
        String label = expr.getLabel();
        if (expr instanceof LeafNode) {
            switch (label) {
                case "Integer":      return "I";
                case "FloatLiteral": return "F";
                case "BoolLiteral":  return "Z";
                case "String":       return "Ljava/lang/String;";
                case "Identifier": {
                    String name = ((LeafNode) expr).getValue();
                    String t = varTypes.get(name);
                    if (t != null) return typeDesc(t);
                    t = globalVarTypes.get(name);
                    return t != null ? typeDesc(t) : "I";
                }
            }
        }
        if (label.equals("FunctionCall")) {
            String funcName = leafVal(expr, 0);
            if (isBuiltin(funcName)) return builtinReturnType(funcName);
            String retType = funcReturnTypes.get(funcName);
            return (retType == null || retType.equals("void")) ? "V" : typeDesc(retType);
        }
        if (label.startsWith("BinaryExpr: ")) {
            String op = label.substring("BinaryExpr: ".length());
            switch (op) {
                case "EQUAL": case "NOT_EQUAL":
                case "LT": case "GT": case "LE": case "GE":
                case "&&": case "||":
                    return "Z";
            }
            if (op.equals("%")) return "I";
            String lt = inferType(expr.getChildren().get(0), varTypes);
            String rt = inferType(expr.getChildren().get(1), varTypes);
            if (lt.equals("Ljava/lang/String;") || rt.equals("Ljava/lang/String;"))
                return "Ljava/lang/String;";
            return (lt.equals("F") || rt.equals("F")) ? "F" : "I";
        }
        if (label.equals("UnaryExpr: -"))
            return inferType(expr.getChildren().get(0), varTypes);
        if (label.equals("CollConstructor"))
            return "L" + leafVal(expr, 0) + ";";
        if (label.equals("FieldAccess")) {
            String collType  = resolveType(expr.getChildren().get(0), varTypes);
            String fieldName = ((LeafNode) expr.getChildren().get(1)).getValue();
            LinkedHashMap<String, String> fields = collectionFields.get(collType);
            if (fields != null && fields.containsKey(fieldName))
                return typeDesc(fields.get(fieldName));
            return "I";
        }
        if (label.equals("ArrayAccess")) {
            String arrType = resolveType(expr.getChildren().get(0), varTypes);
            return typeDesc(arrType.replace("[]", ""));
        }
        return "I";
    }

    /** Résout le nom de type source (ex: "INT[]", "Point") sans générer de code. */
    private String resolveType(ASTNode expr, Map<String, String> varTypes) {
        if (expr instanceof LeafNode) {
            String name = ((LeafNode) expr).getValue();
            String t = varTypes.get(name);
            if (t != null) return t;
            t = globalVarTypes.get(name);
            if (t != null) return t;
        }
        if (expr.getLabel().equals("FieldAccess")) {
            String collType  = resolveType(expr.getChildren().get(0), varTypes);
            String fieldName = ((LeafNode) expr.getChildren().get(1)).getValue();
            LinkedHashMap<String, String> fields = collectionFields.get(collType);
            if (fields != null) return fields.get(fieldName);
        }
        if (expr.getLabel().equals("ArrayAccess")) {
            String arrType = resolveType(expr.getChildren().get(0), varTypes);
            return arrType.replace("[]", "");
        }
        return "INT";
    }

    // ── Helpers JVM ───────────────────────────────────────────────────────────

    private void emitLoad(String typeName, int slot, MethodVisitor mv) {
        switch (typeName) {
            case "INT": case "BOOL": mv.visitVarInsn(ILOAD, slot); break;
            case "FLOAT":            mv.visitVarInsn(FLOAD, slot); break;
            default:                 mv.visitVarInsn(ALOAD, slot); break;
        }
    }

    private void emitStore(String typeName, int slot, MethodVisitor mv) {
        switch (typeName) {
            case "INT": case "BOOL": mv.visitVarInsn(ISTORE, slot); break;
            case "FLOAT":            mv.visitVarInsn(FSTORE, slot); break;
            default:                 mv.visitVarInsn(ASTORE, slot); break;
        }
    }

    private void emitArrayLoad(String elemType, MethodVisitor mv) {
        switch (elemType) {
            case "INT":   mv.visitInsn(IALOAD); break;
            case "FLOAT": mv.visitInsn(FALOAD); break;
            case "BOOL":  mv.visitInsn(BALOAD); break;
            default:      mv.visitInsn(AALOAD); break;
        }
    }

    private void emitArrayStore(String elemType, MethodVisitor mv) {
        switch (elemType) {
            case "INT":   mv.visitInsn(IASTORE); break;
            case "FLOAT": mv.visitInsn(FASTORE); break;
            case "BOOL":  mv.visitInsn(BASTORE); break;
            default:      mv.visitInsn(AASTORE);  break;
        }
    }

    private void emitDefaultValue(String typeName, MethodVisitor mv) {
        switch (typeName) {
            case "INT": case "BOOL": mv.visitInsn(ICONST_0);    break;
            case "FLOAT":            mv.visitInsn(FCONST_0);    break;
            default:                 mv.visitInsn(ACONST_NULL); break;
        }
    }

    private void emitDefaultReturn(String retTypeName, MethodVisitor mv) {
        switch (retTypeName) {
            case "void":
                mv.visitInsn(RETURN); break;
            case "INT": case "BOOL":
                mv.visitInsn(ICONST_0); mv.visitInsn(IRETURN); break;
            case "FLOAT":
                mv.visitInsn(FCONST_0); mv.visitInsn(FRETURN); break;
            default:
                mv.visitInsn(ACONST_NULL); mv.visitInsn(ARETURN);
        }
    }

    private String buildDescriptor(String retTypeName, ASTNode paramList) {
        StringBuilder sb = new StringBuilder("(");
        for (ASTNode p : paramList.getChildren())
            sb.append(typeDesc(leafVal(p, 0)));
        sb.append(")");
        sb.append(retTypeName.equals("void") ? "V" : typeDesc(retTypeName));
        return sb.toString();
    }

    static String typeDesc(String typeName) {
        if (typeName.endsWith("[]")) {
            String elem = typeName.substring(0, typeName.length() - 2);
            switch (elem) {
                case "INT":    return "[I";
                case "FLOAT":  return "[F";
                case "BOOL":   return "[Z";
                case "STRING": return "[Ljava/lang/String;";
                default:       return "[L" + elem + ";";
            }
        }
        switch (typeName) {
            case "INT":    return "I";
            case "FLOAT":  return "F";
            case "BOOL":   return "Z";
            case "STRING": return "Ljava/lang/String;";
            default:       return "L" + typeName + ";";
        }
    }

    String leafVal(ASTNode node, int i) {
        return ((LeafNode) node.getChildren().get(i)).getValue();
    }
}
