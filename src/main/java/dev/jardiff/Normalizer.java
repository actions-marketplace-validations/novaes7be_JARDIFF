package dev.jardiff;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.Printer;

/**
 * Camada 2: transforma uma classe em "membro -> instruções normalizadas".
 * Ignora números de linha, nomes de variáveis locais, frames e ordem do constant pool:
 * só instruções reais entram, e saltos viram deslocamentos relativos em vez de rótulos.
 */
final class Normalizer {
    private Normalizer() {}

    /** members: membro -> instruções normalizadas; locals: método -> nomes das variáveis locais. */
    record Parsed(Map<String, List<String>> members, Map<String, String> locals) {}

    static Parsed parse(byte[] bytes) {
        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, ClassReader.SKIP_FRAMES); // debug fica: só serve p/ detectar cosmética
        Map<String, List<String>> out = new LinkedHashMap<>();
        Map<String, String> locals = new HashMap<>();

        List<String> head = new ArrayList<>();
        head.add("access " + hex(cn.access));
        head.add("super " + cn.superName);
        cn.interfaces.stream().sorted().forEach(i -> head.add("implements " + i));
        out.put("[classe]", head);

        for (FieldNode f : cn.fields) {
            out.put("[campo] " + f.name + ": " + Type.getType(f.desc).getClassName(),
                    List.of("access " + hex(f.access), "valor " + f.value));
        }
        for (MethodNode m : cn.methods) {
            out.put(display(m), body(m));
            locals.put(display(m), m.localVariables == null ? "" : m.localVariables.stream()
                    .map(v -> v.index + "=" + v.name).sorted().collect(Collectors.joining(",")));
        }
        return new Parsed(out, locals);
    }

    private static String display(MethodNode m) {
        String args = Arrays.stream(Type.getArgumentTypes(m.desc)).map(Type::getClassName)
                .collect(Collectors.joining(", "));
        return m.name + "(" + args + "): " + Type.getReturnType(m.desc).getClassName();
    }

    private static List<String> body(MethodNode m) {
        // Rótulo -> índice da próxima instrução real; saltos viram deslocamentos relativos,
        // assim inserir código não "move" os saltos que não cruzam a inserção.
        Map<LabelNode, Integer> labelAt = new HashMap<>();
        Map<AbstractInsnNode, Integer> idx = new HashMap<>();
        int k = 0;
        for (AbstractInsnNode i : m.instructions) {
            if (i instanceof LabelNode l) labelAt.put(l, k);
            if (i.getOpcode() >= 0) idx.put(i, k++);
        }

        List<String> lines = new ArrayList<>();
        lines.add("access " + hex(m.access));
        for (AbstractInsnNode i : m.instructions) {
            if (i.getOpcode() >= 0) lines.add(text(i, idx.get(i), labelAt));
        }
        for (TryCatchBlockNode t : m.tryCatchBlocks) {
            lines.add("TRYCATCH " + t.type + " " + labelAt.get(t.start) + ".." + labelAt.get(t.end)
                    + " -> " + labelAt.get(t.handler));
        }
        return lines;
    }

    private static String text(AbstractInsnNode i, int self, Map<LabelNode, Integer> at) {
        String op = Printer.OPCODES[i.getOpcode()];
        if (i instanceof IntInsnNode n) return op + " " + n.operand;
        if (i instanceof VarInsnNode n) return op + " " + n.var;
        if (i instanceof TypeInsnNode n) return op + " " + n.desc;
        if (i instanceof FieldInsnNode n) return op + " " + n.owner + "." + n.name + ":" + n.desc;
        if (i instanceof MethodInsnNode n) return op + " " + n.owner + "." + n.name + n.desc;
        if (i instanceof InvokeDynamicInsnNode n) {
            return op + " " + n.name + n.desc + " bsm=" + n.bsm.getOwner() + "." + n.bsm.getName()
                    + " args=" + Arrays.toString(n.bsmArgs);
        }
        if (i instanceof JumpInsnNode n) return op + " " + rel(at, n.label, self);
        if (i instanceof LdcInsnNode n) {
            return op + " " + (n.cst instanceof String s ? "\"" + s.replace("\n", "\\n") + "\"" : n.cst);
        }
        if (i instanceof IincInsnNode n) return op + " " + n.var + " " + n.incr;
        if (i instanceof TableSwitchInsnNode n) {
            return op + " " + n.min + ".." + n.max + " " + n.labels.stream().map(l -> rel(at, l, self)).toList()
                    + " default " + rel(at, n.dflt, self);
        }
        if (i instanceof LookupSwitchInsnNode n) {
            return op + " " + n.keys + " " + n.labels.stream().map(l -> rel(at, l, self)).toList()
                    + " default " + rel(at, n.dflt, self);
        }
        if (i instanceof MultiANewArrayInsnNode n) return op + " " + n.desc + " " + n.dims;
        return op; // InsnNode
    }

    private static String rel(Map<LabelNode, Integer> at, LabelNode l, int self) {
        int d = at.get(l) - self;
        return d >= 0 ? "+" + d : String.valueOf(d);
    }

    private static String hex(int access) { return String.format("0x%04x", access); }
}
