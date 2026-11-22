package com.bw.jAncestryChart.model;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Turns the payload of a GEDCOM {@code FILE} structure into a path on disk.
 * <p>
 * GEDCOM 7 asks for URI references ({@code file:///C:/photos/a.jpg} or relative {@code photos/a.jpg}),
 * but real files also contain Windows paths, half-URIs like {@code file://D:\photos\a.jpg} and paths of
 * the machine the file was created on. As a last resort the file name is looked up next to the GEDCOM file.
 */
final class MediaPaths {

    private MediaPaths() {
    }

    static Optional<Path> resolve(String raw, Path baseDir) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String s = raw.trim();
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http:") || lower.startsWith("https:") || lower.startsWith("ftp:")) {
            return Optional.empty();
        }
        List<String> candidates = new ArrayList<>();
        if (lower.startsWith("file:")) {
            try {
                candidates.add(Path.of(URI.create(s)).toString());
            } catch (IllegalArgumentException | UnsupportedOperationException | SecurityException ignored) {
                // not a well-formed URI, handled below
            }
            String rest = s.substring(5);
            if (rest.startsWith("//")) {
                rest = rest.substring(2);
            }
            if (rest.matches("^/[A-Za-z]:.*")) {
                rest = rest.substring(1);
            }
            candidates.add(percentDecode(rest));
            candidates.add(rest);
        } else {
            candidates.add(s);
            if (s.contains("%")) {
                candidates.add(percentDecode(s));
            }
        }

        for (String c : candidates) {
            Path p = toPath(c);
            if (p == null) {
                continue;
            }
            if (p.isAbsolute()) {
                if (Files.isRegularFile(p)) {
                    return Optional.of(p);
                }
            } else if (baseDir != null && Files.isRegularFile(baseDir.resolve(p))) {
                return Optional.of(baseDir.resolve(p));
            }
        }
        return baseDir == null ? Optional.empty() : findByName(fileName(candidates.get(candidates.size() - 1)), baseDir);
    }

    private static Optional<Path> findByName(String name, Path baseDir) {
        if (name.isEmpty() || !Files.isDirectory(baseDir)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.walk(baseDir, 2)) {
            return files.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().equalsIgnoreCase(name))
                    .findFirst();
        } catch (IOException | UncheckedIOException e) {
            return Optional.empty();
        }
    }

    private static Path toPath(String s) {
        try {
            return Path.of(s);
        } catch (InvalidPathException e) {
            try {
                return Path.of(s.replace('\\', '/'));
            } catch (InvalidPathException e2) {
                return null;
            }
        }
    }

    private static String fileName(String s) {
        int cut = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        return s.substring(cut + 1).trim();
    }

    /** Decodes {@code %XX} escapes as UTF-8 (unlike URLDecoder, a {@code +} stays a {@code +}). */
    static String percentDecode(String s) {
        if (s.indexOf('%') < 0) {
            return s;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == '%' && i + 2 < bytes.length) {
                int hi = Character.digit(bytes[i + 1], 16);
                int lo = Character.digit(bytes[i + 2], 16);
                if (hi >= 0 && lo >= 0) {
                    out.write(hi * 16 + lo);
                    i += 2;
                    continue;
                }
            }
            out.write(bytes[i]);
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
