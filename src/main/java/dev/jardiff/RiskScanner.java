package dev.jardiff;

import dev.jardiff.Model.Risk;
import java.util.Set;

/** Classifica uma instrução normalizada em uma categoria de risco (ou null). */
final class RiskScanner {
    private RiskScanner() {}

    private static final Set<String> REFLECT_ON_CLASS = Set.of("forName", "getMethod", "getMethods",
            "getDeclaredMethod", "getDeclaredMethods", "getField", "getDeclaredField", "getDeclaredFields",
            "getDeclaredConstructor", "newInstance");
    private static final Set<String> FILES = Set.of("java/io/File", "java/io/FileInputStream",
            "java/io/FileOutputStream", "java/io/FileReader", "java/io/FileWriter", "java/io/RandomAccessFile");

    static Risk of(String insn) {
        int sp = insn.indexOf(' ');
        if (sp < 0) return null;
        String op = insn.substring(0, sp);
        if (!(op.startsWith("INVOKE") && !op.equals("INVOKEDYNAMIC")) && !op.equals("NEW")) return null;
        String rest = insn.substring(sp + 1);
        int dot = rest.indexOf('.');
        String owner = dot < 0 ? rest : rest.substring(0, dot);
        String name = dot < 0 ? "" : rest.substring(dot + 1);

        if ((owner.startsWith("java/net/") || owner.startsWith("javax/net/")) && !owner.endsWith("Exception")) {
            return Risk.REDE;
        }
        if ((owner.equals("java/lang/Runtime") && name.startsWith("exec"))
                || owner.equals("java/lang/ProcessBuilder") || owner.equals("java/lang/Process")) {
            return Risk.EXEC;
        }
        if (owner.equals("java/io/ObjectInputStream") || owner.equals("java/io/ObjectOutputStream")
                || owner.equals("java/beans/XMLDecoder")) {
            return Risk.SERIALIZACAO;
        }
        if (owner.startsWith("java/lang/reflect/") || owner.equals("java/lang/ClassLoader")
                || (owner.equals("java/lang/Class") && REFLECT_ON_CLASS.stream().anyMatch(name::startsWith))) {
            return Risk.REFLEXAO;
        }
        if (FILES.contains(owner) || owner.startsWith("java/nio/file/")) return Risk.ARQUIVOS;
        return null;
    }
}
