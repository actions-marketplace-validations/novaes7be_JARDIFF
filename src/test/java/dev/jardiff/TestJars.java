package dev.jardiff;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import javax.tools.ToolProvider;

/** Compila fontes (-g) e empacota em .jar para os testes. */
final class TestJars {
    private TestJars() {}

    static Path fromDir(Path src, Path jar) throws Exception {
        List<String> args = new java.util.ArrayList<>(List.of("-g", "--release", "17", "-d"));
        Path classes = Files.createTempDirectory("classes");
        args.add(classes.toString());
        try (Stream<Path> s = Files.walk(src)) {
            s.filter(p -> p.toString().endsWith(".java")).forEach(p -> args.add(p.toString()));
        }
        if (ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)) != 0) {
            throw new IllegalStateException("falha ao compilar " + src);
        }
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar)); Stream<Path> s = Files.walk(classes)) {
            for (Path p : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) {
                out.putNextEntry(new JarEntry(classes.relativize(p).toString().replace('\\', '/')));
                out.write(Files.readAllBytes(p));
                out.closeEntry();
            }
        }
        return jar;
    }

    static Path fromSource(Path dir, String jarName, String className, String code) throws Exception {
        Path src = Files.createDirectories(dir.resolve(jarName + "-src"));
        Files.writeString(src.resolve(className + ".java"), code);
        return fromDir(src, dir.resolve(jarName + ".jar"));
    }
}
