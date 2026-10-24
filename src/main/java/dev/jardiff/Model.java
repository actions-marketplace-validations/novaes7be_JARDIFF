package dev.jardiff;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Tipos de dados do diff: simples e imutáveis. */
public final class Model {
    private Model() {}

    public enum Status { ADDED, REMOVED, CHANGED, COSMETIC }

    public enum Kind {
        LOGIC("lógica alterada"),
        CALL("chamada nova"),
        CONDITION("condição trocada"),
        METHOD_ADDED("método adicionado"),
        METHOD_REMOVED("método removido"),
        COSMETIC("cosmética (ignorada)");

        public final String label;

        Kind(String label) { this.label = label; }
    }

    public enum Risk {
        REDE("rede", 3),
        EXEC("exec", 3),
        SERIALIZACAO("serialização", 3),
        REFLEXAO("reflexão", 2),
        ARQUIVOS("acesso a arquivos", 2);

        public final String label;
        public final int level;

        Risk(String label, int level) { this.label = label; this.level = level; }
    }

    /** "alto", "medio" ou "nenhum", pelo pior risco do conjunto. */
    public static String level(Collection<Risk> risks) {
        int max = risks.stream().mapToInt(r -> r.level).max().orElse(0);
        return max >= 3 ? "alto" : max == 2 ? "medio" : "nenhum";
    }

    /** op: ' ' igual, '-' só na v1, '+' só na v2. */
    public record Line(char op, String text) {}

    /** Método, campo ou cabeçalho de classe (nome legível já pronto). */
    public record Member(String name, Set<Kind> kinds, Set<Risk> risks, List<Line> diff) {
        public int score() { return (int) diff.stream().filter(l -> l.op() != ' ').count(); }
    }

    public record Cls(String name, Status status, List<Member> members, List<String> cosmetic) {
        public int score() { return members.stream().mapToInt(Member::score).sum(); }

        public Set<Risk> risks() {
            Set<Risk> all = EnumSet.noneOf(Risk.class);
            members.forEach(m -> all.addAll(m.risks()));
            return all;
        }
    }

    /** Camada 1: qualquer entrada do jar que mudou (por hash). */
    public record Entry(String path, Status status) {}

    public record Report(String left, String right, List<Entry> entries, List<Cls> classes) {}
}
