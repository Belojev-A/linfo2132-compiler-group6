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

    // infos pré-calculées sur les fonctions et variables globales
    private final Map<String, String> funcDescriptors = new HashMap<>();
    private final Map<String, String> funcReturnTypes  = new HashMap<>();
    private final Map<String, String> globalVarTypes   = new LinkedHashMap<>();
    private String currentReturnType;

    public CodeGenerator(ASTNode root, String className, String outputPath) {
        this.root = root;
        this.className = className;
        this.outputPath = outputPath;
    }

    public void generate() throws IOException {
        // passe 1 : collecte des signatures de fonctions et types globaux
        for (ASTNode child : root.getChildren()) {
            if (child.getLabel().equals("FunctionDef")) {
                String retType  = leafVal(child, 0);
                String funcName = leafVal(child, 1);
                ASTNode params  = child.getChildren().get(2);
                String desc = funcName.equals("main")
                    ? "([Ljava/lang/String;)V"
                    : buildDescriptor(retType, params);
                funcDescriptors.put(funcName, desc);
                funcReturnTypes.put(funcName, retType);
            }
            String lbl = child.getLabel();
            if (lbl.equals("VarDecl") || lbl.equals("ConstDecl"))
                globalVarTypes.put(leafVal(child, 1), leafVal(child, 0));
        }

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

    void generateBlock(ASTNode block, MethodVisitor mv, Map<String, Integer> slots, int[] nextSlot, Map<String, String> varTypes) {
        for (ASTNode stmt : block.getChildren())
            generateStatement(stmt, mv, slots, nextSlot, varTypes);
    }

    private void generateStatement(ASTNode node, MethodVisitor mv, Map<String, Integer> slots, int[] nextSlot, Map<String, String> varTypes) {
        switch (node.getLabel()) {
            case "VarDecl":    generateVarDecl(node, mv, slots, nextSlot, varTypes);  break;
            case "AssignStmt": generateAssignStmt(node, mv, slots, varTypes);         break;
            case "ExprStmt":   generateExprStmt(node, mv, slots, varTypes);           break;
            case "ReturnStmt": generateReturnStmt(node, mv, slots, varTypes);         break;
            // IfStmt, WhileStmt, ForStmt → push suivants
        }
    }

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

    private void generateAssignStmt(ASTNode node, MethodVisitor mv, Map<String, Integer> slots, Map<String, String> varTypes) {
        ASTNode lhs = node.getChildren().get(0);
        ASTNode rhs = node.getChildren().get(1);

        // FieldAccess/ArrayAccess → push 8
        if (!(lhs instanceof LeafNode)) return;
        String name = ((LeafNode) lhs).getValue();
        Integer slot = slots.get(name);

        if (slot != null) {
            // variable locale
            String typeName = varTypes.get(name);
            String t = generateExpr(rhs, mv, slots, varTypes);
            if (typeName.equals("FLOAT") && t.equals("I")) mv.visitInsn(I2F);
            emitStore(typeName, slot, mv);
        } else {
            // variable globale
            String gType = globalVarTypes.get(name);
            if (gType == null) return;
            String t = generateExpr(rhs, mv, slots, varTypes);
            if (gType.equals("FLOAT") && t.equals("I")) mv.visitInsn(I2F);
            mv.visitFieldInsn(PUTSTATIC, className, name, typeDesc(gType));
        }
    }

    private void generateExprStmt(ASTNode node, MethodVisitor mv, Map<String, Integer> slots, Map<String, String> varTypes) {
        String t = generateExpr(node.getChildren().get(0), mv, slots, varTypes);
        if (!t.equals("V")) mv.visitInsn(POP);
    }

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
                    // variable globale
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

        // CollConstructor, ArrayCreation, etc. → push 8
        return "I";
    }

    private String generateFunctionCall(ASTNode expr, MethodVisitor mv, Map<String, Integer> slots, Map<String, String> varTypes) {
        String funcName = leafVal(expr, 0);
        ASTNode argList = expr.getChildren().get(1);

        if (isBuiltin(funcName)) return generateBuiltin(funcName, argList, mv, slots, varTypes);

        // fonction utilisateur
        for (ASTNode arg : argList.getChildren())
            generateExpr(arg, mv, slots, varTypes);

        mv.visitMethodInsn(INVOKESTATIC, className, funcName, funcDescriptors.get(funcName), false);

        String retType = funcReturnTypes.get(funcName);
        return (retType == null || retType.equals("void")) ? "V" : typeDesc(retType);
    }

    private boolean isBuiltin(String name) {
        switch (name) {
            case "println": case "print": case "print_INT": case "print_FLOAT":
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
            case "println": case "print": {
                if (args.isEmpty()) {
                    mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", name, "()V", false);
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
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", name, desc, false);
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

    private String generateBinaryExpr(ASTNode expr, MethodVisitor mv, Map<String, Integer> slots, Map<String, String> varTypes) {
        String op = expr.getLabel().substring("BinaryExpr: ".length());
        ASTNode l = expr.getChildren().get(0);
        ASTNode r = expr.getChildren().get(1);

        String lt = inferType(l, varTypes);
        String rt = inferType(r, varTypes);
        boolean isFloat = lt.equals("F") || rt.equals("F");

        generateExpr(l, mv, slots, varTypes);
        if (isFloat && lt.equals("I")) mv.visitInsn(I2F);
        generateExpr(r, mv, slots, varTypes);
        if (isFloat && rt.equals("I")) mv.visitInsn(I2F);

        if (lt.equals("Ljava/lang/String;") && op.equals("+")) {
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "concat",
                "(Ljava/lang/String;)Ljava/lang/String;", false);
            return "Ljava/lang/String;";
        }

        switch (op) {
            case "+": mv.visitInsn(isFloat ? FADD : IADD); return isFloat ? "F" : "I";
            case "-": mv.visitInsn(isFloat ? FSUB : ISUB); return isFloat ? "F" : "I";
            case "*": mv.visitInsn(isFloat ? FMUL : IMUL); return isFloat ? "F" : "I";
            case "/": mv.visitInsn(isFloat ? FDIV : IDIV); return isFloat ? "F" : "I";
            case "%": mv.visitInsn(IREM); return "I";
            default:  return "I"; // comparaisons / logique → push 5
        }
    }

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
            if (op.equals("%")) return "I";
            String lt = inferType(expr.getChildren().get(0), varTypes);
            String rt = inferType(expr.getChildren().get(1), varTypes);
            return (lt.equals("F") || rt.equals("F")) ? "F" : "I";
        }
        if (label.equals("UnaryExpr: -"))
            return inferType(expr.getChildren().get(0), varTypes);
        return "I";
    }

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
