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

        // "main" → public static void main(String[] args)
        String jvmName    = funcName.equals("main") ? "main" : funcName;
        String descriptor = funcName.equals("main")
            ? "([Ljava/lang/String;)V"
            : buildDescriptor(retTypeName, paramList);

        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, jvmName, descriptor, null, null);
        mv.visitCode();

        // slot 0 = args pour main, sinon les params commencent à 0
        Map<String, Integer> slots = new LinkedHashMap<>();
        int[] nextSlot = { funcName.equals("main") ? 1 : 0 };
        for (ASTNode p : paramList.getChildren())
            slots.put(leafVal(p, 1), nextSlot[0]++);

        generateBlock(block, mv, slots, nextSlot);

        emitDefaultReturn(retTypeName, mv);
        mv.visitMaxs(-1, -1);
        mv.visitEnd();
    }

    void generateBlock(ASTNode block, MethodVisitor mv, Map<String, Integer> slots, int[] nextSlot) {
        // complété push 2
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
