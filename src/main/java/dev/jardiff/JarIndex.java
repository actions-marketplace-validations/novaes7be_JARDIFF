package dev.jardiff;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Camada 1: hash SHA-256 de cada entrada; guarda os bytes só dos .class. */
record JarIndex(Map<String, String> hashes, Map<String, byte[]> classes) {

    static JarIndex read(Path jar) throws IOException {
        Map<String, String> hashes = new TreeMap<>();
        Map<String, byte[]> classes = new TreeMap<>();
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(jar))) {
            for (ZipEntry e; (e = zin.getNextEntry()) != null; ) {
                if (e.isDirectory()) continue;
                byte[] data = zin.readAllBytes();
                hashes.put(e.getName(), sha256(data));
                if (e.getName().endsWith(".class")) classes.put(e.getName(), data);
            }
        }
        if (hashes.isEmpty()) throw new IOException("não parece um .jar válido: " + jar);
        return new JarIndex(hashes, classes);
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
