package dev.jardiff;

import dev.jardiff.Model.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Camada 1 (entradas por hash) + camada 2 (bytecode normalizado) + classificação + risco. */
public final class JarDiff {
    private JarDiff() {}

    private static final Set<String> COND_JUMPS = Set.of("IFEQ", "IFNE", "IFLT", "IFGE", "IFGT", "IFLE",
            "IF_ICMPEQ", "IF_ICMPNE", "IF_ICMPLT", "IF_ICMPGE", "IF_ICMPGT", "IF_ICMPLE",
            "IF_ACMPEQ", "IF_ACMPNE", "IFNULL", "IFNONNULL");
    private static final Set<String> COMPARES = Set.of("LCMP", "FCMPL", "FCMPG", "DCMPL", "DCMPG");

    public static Report diff(Path left, Path right) throws IOException {
        JarIndex a = JarIndex.read(left), b = JarIndex.read(right);
        List<Entry> entries = new ArrayList<>();
        List<Cls> classes = new ArrayList<>();

        Set<String> paths = new TreeSet<>(a.hashes().keySet());
        paths.addAll(b.hashes().keySet());
        for (String p : paths) {
            String ha = a.hashes().get(p), hb = b.hashes().get(p);
            if (Objects.equals(ha, hb)) continue;
            Status s = ha == null ? Status.ADDED : hb == null ? Status.REMOVED : Status.CHANGED;
            entries.add(new Entry(p, s));
            if (p.endsWith(".class")) {
                classes.add(compare(p.substring(0, p.length() - 6), s, a.classes().get(p), b.classes().get(p)));
            }
        }
        return new Report(left.getFileName().toString(), right.getFileName().toString(), entries, classes);
    }

    private static Cls compare(String name, Status status, byte[] a, byte[] b) {
        Normalizer.Parsed pa = a == null ? null : Normalizer.parse(a), pb = b == null ? null : Normalizer.parse(b);
        Map<String, List<String>> ma = pa == null ? Map.of() : pa.members();
        Map<String, List<String>> mb = pb == null ? Map.of() : pb.members();
        Set<String> keys = new LinkedHashSet<>(ma.keySet());
        keys.addAll(mb.keySet());

        List<Member> members = new ArrayList<>();
        List<String> cosmetic = new ArrayList<>();
        for (String k : keys) {
            List<String> la = ma.get(k), lb = mb.get(k);
            if (la != null && la.equals(lb)) {
                // mesmo bytecode; se só o nome de variável local mudou, é cosmética (listada, nunca lógica)
                if (!Objects.equals(pa.locals().get(k), pb.locals().get(k))) cosmetic.add(k);
                continue;
            }
            members.add(member(k, la, lb));
        }
        // hash mudou mas nada de lógica: números de linha, constant pool, metadados
        if (status == Status.CHANGED && members.isEmpty()) {
            status = Status.COSMETIC;
            if (cosmetic.isEmpty()) cosmetic.add("(números de linha / constant pool)");
        }
        return new Cls(name, status, members, cosmetic);
    }

    static Member member(String name, List<String> a, List<String> b) {
        List<Line> diff = lcs(a == null ? List.of() : a, b == null ? List.of() : b);
        Set<Kind> kinds = a == null ? EnumSet.of(Kind.METHOD_ADDED)
                : b == null ? EnumSet.of(Kind.METHOD_REMOVED) : classify(diff);
        Set<Risk> risks = EnumSet.noneOf(Risk.class); // só risco NOVO (linhas '+')
        for (Line l : diff) {
            if (l.op() != '+') continue;
            Risk r = RiskScanner.of(l.text());
            if (r != null) risks.add(r);
        }
        return new Member(name, kinds, risks, diff);
    }

    static Set<Kind> classify(List<Line> diff) {
        Set<Kind> kinds = EnumSet.noneOf(Kind.class);
        Map<String, Integer> removedCalls = new HashMap<>();
        for (Line l : diff) {
            if (l.op() == '-' && isCall(l.text())) removedCalls.merge(l.text(), 1, Integer::sum);
        }
        boolean other = false;
        for (Line l : diff) {
            if (l.op() == ' ') continue;
            if (isCall(l.text())) {
                // chamada só conta como nova se não existia (mesmo texto) do outro lado
                if (l.op() == '+' && removedCalls.merge(l.text(), -1, Integer::sum) < 0) kinds.add(Kind.CALL);
            } else if (!COND_JUMPS.contains(op(l.text())) && !COMPARES.contains(op(l.text()))) {
                other = true;
            }
        }
        if (!jumps(diff, '-').equals(jumps(diff, '+'))) kinds.add(Kind.CONDITION);
        if (other || kinds.isEmpty()) kinds.add(Kind.LOGIC);
        return kinds;
    }

    private static boolean isCall(String t) { return t.startsWith("INVOKE"); }

    private static String op(String t) {
        int sp = t.indexOf(' ');
        return sp < 0 ? t : t.substring(0, sp);
    }

    private static List<String> jumps(List<Line> diff, char side) {
        List<String> out = new ArrayList<>();
        for (Line l : diff) {
            if ((l.op() == ' ' || l.op() == side) && COND_JUMPS.contains(op(l.text()))) out.add(op(l.text()));
        }
        return out;
    }

    /** Diff por maior subsequência comum. */
    static List<Line> lcs(List<String> a, List<String> b) {
        int n = a.size(), m = b.size();
        List<Line> out = new ArrayList<>();
        if ((long) n * m > 4_000_000L) { // shortcut: método gigante, sem LCS; mostra tudo como trocado
            a.forEach(s -> out.add(new Line('-', s)));
            b.forEach(s -> out.add(new Line('+', s)));
            return out;
        }
        int[][] t = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                t[i][j] = a.get(i).equals(b.get(j)) ? t[i + 1][j + 1] + 1 : Math.max(t[i + 1][j], t[i][j + 1]);
            }
        }
        int i = 0, j = 0;
        while (i < n && j < m) {
            if (a.get(i).equals(b.get(j))) { out.add(new Line(' ', a.get(i))); i++; j++; }
            else if (t[i + 1][j] >= t[i][j + 1]) out.add(new Line('-', a.get(i++)));
            else out.add(new Line('+', b.get(j++)));
        }
        while (i < n) out.add(new Line('-', a.get(i++)));
        while (j < m) out.add(new Line('+', b.get(j++)));
        return out;
    }
}
