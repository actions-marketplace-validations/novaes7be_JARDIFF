package dev.jardiff;

import static org.junit.jupiter.api.Assertions.*;

import dev.jardiff.Model.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JarDiffTest {
    static Report report;
    @TempDir static Path tmp;

    @BeforeAll
    static void build() throws Exception {
        Path v1 = TestJars.fromDir(Path.of("samples/v1/src"), tmp.resolve("v1.jar"));
        Path v2 = TestJars.fromDir(Path.of("samples/v2/src"), tmp.resolve("v2.jar"));
        report = JarDiff.diff(v1, v2);
    }

    static Cls cls(String n) { return report.classes().stream().filter(c -> c.name().equals(n)).findFirst().orElseThrow(); }

    static Member member(String c, String prefix) {
        return cls(c).members().stream().filter(m -> m.name().startsWith(prefix)).findFirst().orElseThrow();
    }

    @Test void camada1_classeNovaEAlteradas() {
        assertEquals(Status.ADDED, cls("loja/Cupom").status());
        for (String c : new String[] {"loja/Pedido", "loja/Desconto", "loja/Frete"}) assertEquals(Status.CHANGED, cls(c).status());
        assertEquals(4, report.classes().size());
    }

    @Test void desconto_condicaoELogica() {
        var k = member("loja/Desconto", "calcular").kinds();
        assertTrue(k.contains(Kind.CONDITION) && k.contains(Kind.LOGIC));
    }

    @Test void frete_chamadaNovaERedeComoRisco() {
        Member m = member("loja/Frete", "calcular");
        assertTrue(m.kinds().contains(Kind.CALL));
        assertTrue(m.risks().contains(Risk.REDE));
        assertEquals("alto", Model.level(m.risks()));
    }

    @Test void pedido_reflexaoEMetodoRemovido() {
        assertTrue(member("loja/Pedido", "total").risks().contains(Risk.REFLEXAO));
        assertTrue(member("loja/Pedido", "resumo").kinds().contains(Kind.METHOD_REMOVED));
    }

    @Test void renomearVariavelLocalNaoEhLogica() {
        assertTrue(cls("loja/Pedido").members().stream().noneMatch(m -> m.name().startsWith("subtotal")));
        assertEquals(1, cls("loja/Pedido").cosmetic().stream().filter(n -> n.startsWith("subtotal")).count());
    }

    @Test void classeSoComCosmeticaViraCosmetic() throws Exception {
        String a = "public class A { public int f(int x) { int a = x * 2; return a; } }";
        String b = "public class A {\n\n public int f(int x) { int dobro = x * 2; return dobro; } }";
        Report r = JarDiff.diff(TestJars.fromSource(tmp, "a1", "A", a), TestJars.fromSource(tmp, "a2", "A", b));
        assertEquals(Status.COSMETIC, r.classes().get(0).status());
        assertTrue(r.classes().get(0).members().isEmpty());
    }

    @Test void riscoSoParaInstrucoesNovas() {
        assertEquals(Risk.EXEC, RiskScanner.of("INVOKEVIRTUAL java/lang/Runtime.exec(Ljava/lang/String;)Ljava/lang/Process;"));
        assertEquals(Risk.ARQUIVOS, RiskScanner.of("NEW java/io/FileInputStream"));
        assertEquals(Risk.SERIALIZACAO, RiskScanner.of("NEW java/io/ObjectInputStream"));
        assertNull(RiskScanner.of("INVOKEVIRTUAL java/lang/String.length()I"));
    }

    @Test void saidasHtmlJsonConsole() throws Exception {
        Path html = tmp.resolve("out/r.html");
        assertEquals(0, Main.run(new String[] {tmp.resolve("v1.jar").toString(), tmp.resolve("v2.jar").toString(), "-o", html.toString()},
                new java.io.PrintStream(java.io.OutputStream.nullOutputStream()), System.err));
        String h = Files.readString(html);
        assertTrue(h.contains("loja/Frete") && !h.contains("/*__DATA__*/"));
        assertFalse(h.matches("(?s).*<(script|link)[^>]+(src|href)=.*"), "sem dependências externas");
        assertTrue(Files.readString(tmp.resolve("out/r.json")).contains("\"REDE\""));
    }

    @Test void usoIncorretoRetorna1() {
        assertEquals(1, Main.run(new String[] {"so-um.jar"}, System.out, new java.io.PrintStream(java.io.OutputStream.nullOutputStream())));
    }

    @Test void failOnRiskEMarkdown() throws Exception {
        var quiet = new java.io.PrintStream(java.io.OutputStream.nullOutputStream());
        String v1 = tmp.resolve("v1.jar").toString(), v2 = tmp.resolve("v2.jar").toString();
        Path md = tmp.resolve("ci/resumo.md");
        assertEquals(3, Main.run(new String[] {v1, v2, "-o", tmp.resolve("ci/r.html").toString(), "--markdown", md.toString(), "--fail-on-risk", "alto"}, quiet, quiet));
        assertEquals(0, Main.run(new String[] {v1, v2, "-o", tmp.resolve("ci/r.html").toString(), "--fail-on-risk", "nenhum"}, quiet, quiet));
        assertEquals(0, Main.run(new String[] {v1, v1, "-o", tmp.resolve("ci/same.html").toString(), "--fail-on-risk", "medio"}, quiet, quiet));
        String text = Files.readString(md);
        assertTrue(text.contains("loja.Frete") && text.contains("rede") && text.contains("alto"));
    }

    @Test void ignoraRuidoDoCompilador() throws Exception {
        Path jar = TestJars.fromSource(tmp, "en", "Cor", "public enum Cor { RED, GREEN }");
        var members = Normalizer.parse(JarIndex.read(jar).classes().get("Cor.class")).members();
        assertTrue(members.keySet().stream().noneMatch(k -> k.startsWith("$values") || k.startsWith("<clinit>")));
        assertTrue(members.keySet().stream().anyMatch(k -> k.startsWith("[campo] RED")), "constantes continuam comparadas");
    }
}