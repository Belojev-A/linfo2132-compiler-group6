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

    public CodeGenerator(ASTNode root, String className, String outputPath) {
        this.root = root;
        this.className = className;
        this.outputPath = outputPath;
    }

    public void generate() throws IOException {
        cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(V1_8, ACC_PUBLIC, className, null, "java/lang/Object", null);

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

    private void generateFunction(ASTNode node) {
        String retTypeName = leafVal(node, 0);
        String funcName    = leafVal(node, 1);
        ASTNode paramList  = node.getChildren().get(2);
        ASTNode block      = node.getChildren().get(3);

        String jvmName    = funcName.equals("main") ? "main" : funcName;
        String descriptor = funcName.equals("main")
            ? "([Ljava/lang/String;)V"
            : buildDescriptor(retTypeName, paramList);

        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, jvmName, descriptor, null, null);
        mv.visitCode();

        // slot 0 = args pour main, params à partir de 0 sinon
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
            case "VarDecl":    generateVarDecl(node, mv, slots, nextSlot, varTypes); break;
            case "AssignStmt": generateAssignStmt(node, mv, slots, varTypes);        break;
            case "ExprStmt":   generateExprStmt(node, mv, slots, varTypes);          break;
            // ReturnStmt, IfStmt, WhileStmt, ForStmt → push suivants
        }
    }

    private void generateVarDecl(ASTNode node, MethodVisitor mv, Map<String, Integer> slots, int[] nextSlot, Map<String, String> varTypes) {
        String typeName = leafVal(node, 0);
        String name     = leafVal(node, 1);
        int slot = nextSlot[0]++;
        slots.put(name, slot);
        varTypes.put(name, typeName);

        if (node.getChildren().size() > 2) {
            String exprType = generateExpr(node.getChildren().get(2), mv, slots, varTypes);
            if (typeName.equals("FLOAT") && exprType.equals("I"))
                mv.visitInsn(I2F);
        } else {
            emitDefaultValue(typeName, mv);
        }
        emitStore(typeName, slot, mv);
    }

    private void generateAssignStmt(ASTNode node, MethodVisitor mv, Map<String, Integer> slots, Map<String, String> varTypes) {
        ASTNode lhs = node.getChildren().get(0);
        ASTNode rhs = node.getChildren().get(1);

        // variable locale simple (FieldAccess/ArrayAccess → push 8, global → push 3)
        if (!(lhs instanceof LeafNode)) return;
        String name = ((LeafNode) lhs).getValue();
        Integer slot = slots.get(name);
        if (slot == null) return;

        String typeName = varTypes.get(name);
        String exprType = generateExpr(rhs, mv, slots, varTypes);
        if (typeName.equals("FLOAT") && exprType.equals("I"))
            mv.visitInsn(I2F);
        emitStore(typeName, slot, mv);
    }

    private void generateExprStmt(ASTNode node, MethodVisitor mv, Map<String, Integer> slots, Map<String, String> varTypes) {
        // appels de fonction en statement (println, etc.) → push 3
    }

    // génère le bytecode d'une expression ; retourne son type JVM ("I", "F", "Z", "Ljava/lang/String;")
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
                    if (slot == null) return "I"; // variable globale → push 3
                    emitLoad(varTypes.get(name), slot, mv);
                    return typeDesc(varTypes.get(name));
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

        // FunctionCall, CollConstructor, etc. → push 3/8
        return "I";
    }

    private String generateBinaryExpr(ASTNode expr, MethodVisitor mv, Map<String, Integer> slots, Map<String, String> varTypes) {
        String op = expr.getLabel().substring("BinaryExpr: ".length());
        ASTNode l = expr.getChildren().get(0);
        ASTNode r = expr.getChildren().get(1);

        String lt = inferType(l, varTypes);
        String rt = inferType(r, varTypes);
        boolean isFloat = lt.equals("F") || rt.equals("F");

        // génère les deux opérandes avec promotion INT→FLOAT si nécessaire
        generateExpr(l, mv, slots, varTypes);
        if (isFloat && lt.equals("I")) mv.visitInsn(I2F);
        generateExpr(r, mv, slots, varTypes);
        if (isFloat && rt.equals("I")) mv.visitInsn(I2F);

        // concaténation STRING
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

    // infère le type JVM d'une expression sans émettre de bytecode
    String inferType(ASTNode expr, Map<String, String> varTypes) {
        String label = expr.getLabel();
        if (expr instanceof LeafNode) {
            switch (label) {
                case "Integer":      return "I";
                case "FloatLiteral": return "F";
                case "BoolLiteral":  return "Z";
                case "String":       return "Ljava/lang/String;";
                case "Identifier": {
                    String t = varTypes.get(((LeafNode) expr).getValue());
                    return t != null ? typeDesc(t) : "I";
                }
            }
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
            case "INT": case "BOOL": mv.visitInsn(ICONST_0);   break;
            case "FLOAT":            mv.visitInsn(FCONST_0);   break;
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
