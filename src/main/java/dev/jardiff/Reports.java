package dev.jardiff;

import dev.jardiff.Model.*;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;

/** Saídas: dados JSON, HTML de arquivo único e resumo no terminal. */
public final class Reports {
    private Reports() {}

    public static Map<String, Object> data(Report r) {
        Map<String, Object> classCount = new LinkedHashMap<>();
        for (Status s : Status.values()) classCount.put(s.name().toLowerCase(), count(r.classes(), s));
        Map<String, Object> entryCount = new LinkedHashMap<>();
        for (Status s : List.of(Status.ADDED, Status.REMOVED, Status.CHANGED)) {
            entryCount.put(s.name().toLowerCase(), r.entries().stream().filter(e -> e.status() == s).count());
        }
        Map<String, Object> kinds = new LinkedHashMap<>();
        for (Kind k : Kind.values()) {
            // tipos contam só membros de classes alteradas; classe nova/removida já aparece em "classes"
            kinds.put(k.name(), k == Kind.COSMETIC ? r.classes().stream().mapToLong(c -> c.cosmetic().size()).sum()
                    : r.classes().stream().filter(c -> c.status() == Status.CHANGED)
                        .flatMap(c -> c.members().stream()).filter(m -> m.kinds().contains(k)).count());
        }
        Map<String, Object> risk = new LinkedHashMap<>();
        for (String lv : List.of("alto", "medio", "nenhum")) {
            risk.put(lv, r.classes().stream().filter(c -> Model.level(c.risks()).equals(lv)).count());
        }
        Map<String, Object> tags = new LinkedHashMap<>();
        for (Risk k : Risk.values()) {
            tags.put(k.name(), r.classes().stream().flatMap(c -> c.members().stream())
                    .filter(m -> m.risks().contains(k)).count());
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("kinds", labels(Kind.values()));
        meta.put("risks", labels(Risk.values()));

        List<Object> entries = new ArrayList<>();
        for (Entry e : r.entries()) entries.add(map("path", e.path(), "status", e.status().name()));
        List<Object> classes = new ArrayList<>();
        for (Cls c : r.classes()) classes.add(cls(c));

        return map("left", r.left(), "right", r.right(), "generated", OffsetDateTime.now().withNano(0).toString(),
                "summary", map("classes", classCount, "entries", entryCount, "kinds", kinds, "risk", risk, "riskTags", tags),
                "meta", meta, "entries", entries, "classes", classes);
    }

    private static Map<String, Object> cls(Cls c) {
        List<Object> ms = new ArrayList<>();
        for (Member m : c.members()) {
            List<Object> diff = new ArrayList<>();
            for (Line l : m.diff()) diff.add(map("op", String.valueOf(l.op()), "text", l.text()));
            ms.add(map("name", m.name(), "kinds", c.status() == Status.CHANGED ? m.kinds().stream().map(Enum::name).toList() : List.of(),
                    "risk", Model.level(m.risks()), "risks", m.risks().stream().map(Enum::name).toList(), "diff", diff));
        }
        return map("name", c.name(), "status", c.status().name(), "score", c.score(),
                "risk", Model.level(c.risks()), "risks", c.risks().stream().map(Enum::name).toList(), "members", ms,
                "cosmetic", c.cosmetic());
    }

    public static void json(Report r, Path out) throws IOException {
        Files.writeString(out, Json.write(data(r), true), StandardCharsets.UTF_8);
    }

    public static void html(Report r, Path out) throws IOException {
        try (var in = Reports.class.getResourceAsStream("/report.html")) {
            String tpl = new String(Objects.requireNonNull(in, "report.html ausente").readAllBytes(), StandardCharsets.UTF_8);
            // "</" escapado para o JSON nunca fechar a tag <script>
            String json = Json.write(data(r), false).replace("</", "<\\/");
            Files.writeString(out, tpl.replace("/*__DATA__*/null", json), StandardCharsets.UTF_8);
        }
    }

    public static void console(Report r, PrintStream out) {
        Map<String, Object> s = data(r);
        out.println("jardiff  " + r.left() + "  ->  " + r.right());
        Map<?, ?> sum = (Map<?, ?>) s.get("summary");
        out.println("  classes: " + sum.get("classes"));
        out.println("  tipos:   " + kv((Map<?, ?>) sum.get("kinds"), Kind.values()));
        out.println("  risco:   " + sum.get("risk"));
        for (Cls c : r.classes()) {
            String icon = switch (c.status()) { case ADDED -> "+"; case REMOVED -> "-"; case CHANGED -> "~"; case COSMETIC -> "≈"; };
            out.println("  " + icon + " " + c.name().replace('/', '.') + (c.status() == Status.COSMETIC ? "  (só cosmética, ignorada)" : ""));
            for (String name : c.cosmetic()) out.println("      ≈ " + name + "  -> cosmética (ignorada)");
            for (Member m : c.members()) {
                if (c.status() != Status.CHANGED) break; // classe nova/removida: já resumida acima
                String tags = String.join(", ", m.kinds().stream().map(k -> k.label).toList());
                String risk = m.risks().isEmpty() ? "" : "  [risco: " + String.join(", ", m.risks().stream().map(k -> k.label).toList()) + "]";
                out.println("      " + m.name() + "  -> " + tags + risk);
            }
        }
    }

    /** Pior risco novo entre todas as classes: "alto", "medio" ou "nenhum". */
    public static String worst(Report r) {
        Set<Risk> all = EnumSet.noneOf(Risk.class);
        r.classes().forEach(c -> all.addAll(c.risks()));
        return Model.level(all);
    }

    /** Resumo em Markdown para comentário de PR (limitado a 40 linhas). */
    public static String markdown(Report r) {
        String worst = worst(r);
        StringBuilder sb = new StringBuilder("## jardiff: `" + r.left() + "` → `" + r.right() + "`\n\n");
        sb.append("**Risco novo:** ").append(worst.equals("alto") ? "🔴 alto" : worst.equals("medio") ? "🟡 médio" : "🟢 nenhum").append("  \n");
        long changed = count(r.classes(), Status.CHANGED);
        sb.append("Classes: +").append(count(r.classes(), Status.ADDED)).append(" −").append(count(r.classes(), Status.REMOVED))
          .append(" ~").append(changed).append(" ≈").append(count(r.classes(), Status.COSMETIC)).append(" (cosméticas ignoradas)\n\n");
        if (r.classes().stream().allMatch(c -> c.status() == Status.COSMETIC)) return sb.append("Nenhuma mudança de lógica.\n").toString();
        sb.append("| Classe | Membro | Mudança | Risco |\n|---|---|---|---|\n");
        int rows = 0;
        outer:
        for (Cls c : r.classes()) {
            if (c.status() == Status.COSMETIC) continue;
            String cn = "`" + c.name().replace('/', '.') + "`";
            if (c.status() != Status.CHANGED) {
                sb.append("| ").append(cn).append(" | | classe ").append(c.status() == Status.ADDED ? "adicionada" : "removida")
                  .append(" | ").append(riskText(c.risks())).append(" |\n");
                if (++rows >= 40) break;
                continue;
            }
            for (Member m : c.members()) {
                if (rows++ >= 40) { sb.append("\n_…lista truncada; veja o relatório HTML completo._\n"); break outer; }
                sb.append("| ").append(cn).append(" | `").append(m.name().replace("|", "\\|")).append("` | ")
                  .append(String.join(", ", m.kinds().stream().map(k -> k.label).toList())).append(" | ").append(riskText(m.risks())).append(" |\n");
            }
        }
        return sb.toString();
    }

    private static String riskText(Set<Risk> risks) {
        return risks.isEmpty() ? "–" : String.join(", ", risks.stream().map(k -> k.label).toList());
    }

    private static String kv(Map<?, ?> counts, Kind[] kinds) {
        StringBuilder sb = new StringBuilder();
        for (Kind k : kinds) sb.append(k.label).append(' ').append(counts.get(k.name())).append("  ");
        return sb.toString().trim();
    }

    private static long count(List<Cls> l, Status s) { return l.stream().filter(c -> c.status() == s).count(); }

    private static Map<String, Object> labels(Enum<?>[] values) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Enum<?> e : values) m.put(e.name(), e instanceof Kind k ? k.label : ((Risk) e).label);
        return m;
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
