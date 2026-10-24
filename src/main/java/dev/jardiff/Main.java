package dev.jardiff;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** CLI: java -jar jardiff.jar v1.jar v2.jar -o relatorio.html */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(System.err, true, StandardCharsets.UTF_8);
        System.exit(run(args, out, err));
    }

    /** Retorna o código de saída: 0 ok, 1 uso incorreto, 2 erro de leitura/escrita, 3 risco acima do limite. */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        Path html = Path.of("relatorio.html"), json = null, md = null;
        String failOn = null;
        Path[] jars = new Path[2];
        int n = 0;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-h", "--help" -> { usage(out); return 0; }
                case "-o", "--output" -> {
                    if (++i >= args.length) return fail(err);
                    html = Path.of(args[i]);
                }
                case "--json" -> {
                    if (++i >= args.length) return fail(err);
                    json = Path.of(args[i]);
                }
                case "--markdown" -> {
                    if (++i >= args.length) return fail(err);
                    md = Path.of(args[i]);
                }
                case "--fail-on-risk" -> {
                    if (++i >= args.length || !List.of("alto", "medio", "nenhum").contains(args[i])) return fail(err);
                    failOn = args[i].equals("nenhum") ? null : args[i];
                }
                default -> {
                    if (n == 2 || args[i].startsWith("-")) return fail(err);
                    jars[n++] = Path.of(args[i]);
                }
            }
        }
        if (n != 2) return fail(err);
        if (json == null) json = Path.of(html.toString().replaceAll("(?i)\\.html?$", "") + ".json");

        try {
            for (Path j : jars) if (!Files.isRegularFile(j)) throw new IOException("arquivo não encontrado: " + j);
            Model.Report r = JarDiff.diff(jars[0], jars[1]);
            for (Path o : new Path[] {html, json, md == null ? html : md}) {
                if (o.toAbsolutePath().getParent() != null) Files.createDirectories(o.toAbsolutePath().getParent());
            }
            Reports.html(r, html);
            Reports.json(r, json);
            Reports.console(r, out);
            out.println();
            out.println("HTML: " + html.toAbsolutePath());
            out.println("JSON: " + json.toAbsolutePath());
            if (md != null) Files.writeString(md, Reports.markdown(r), StandardCharsets.UTF_8);
            String worst = Reports.worst(r);
            if (failOn != null && rank(worst) >= rank(failOn)) {
                err.println("jardiff: risco novo " + worst + " (limite: " + failOn + ")");
                return 3;
            }
            return 0;
        } catch (IOException e) {
            err.println("erro: " + e.getMessage());
            return 2;
        }
    }

    private static int rank(String level) { return level.equals("alto") ? 2 : level.equals("medio") ? 1 : 0; }

    private static int fail(PrintStream err) {
        usage(err);
        return 1;
    }

    private static void usage(PrintStream p) {
        p.println("uso: java -jar jardiff.jar <v1.jar> <v2.jar> [-o relatorio.html] [--json saida.json]\n"
                + "             [--markdown resumo.md] [--fail-on-risk alto|medio|nenhum]   (código 3 se o risco novo atingir o limite)");
    }
}
