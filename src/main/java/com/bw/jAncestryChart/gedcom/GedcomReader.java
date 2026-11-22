package com.bw.jAncestryChart.gedcom;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads GEDCOM 7 files into a tree of {@link GedcomNode}s.
 * <p>
 * The reader follows the GEDCOM 7.0 line grammar (UTF-8, optional byte order mark, any line ending,
 * {@code CONT} continuations, the leading {@code @@} escape, the {@code @VOID@} pointer). It is lenient
 * where that costs nothing: GEDCOM 5.5.1 {@code CONC} lines, indentation, UTF-16 and non-UTF-8 files
 * are accepted, and every tolerated problem is reported in {@link GedcomDocument#warnings()}.
 */
public final class GedcomReader {

    private static final Pattern LINE =
            Pattern.compile("^[ \\t]*(\\d{1,3})[ \\t]+(?:(@[^@\\s]+@)[ \\t]+)?([A-Za-z0-9_]+)(?: (.*))?$");
    private static final Pattern POINTER = Pattern.compile("@([^@#\\s][^@\\s]*)@");

    /** Reads and parses a GEDCOM file. */
    public GedcomDocument read(Path file) throws IOException {
        List<String> warnings = new ArrayList<>();
        String text = decode(Files.readAllBytes(file), warnings);
        return parse(text, file, warnings);
    }

    /** Reads and parses GEDCOM data from a stream; media paths are then resolved against the working directory. */
    public GedcomDocument read(InputStream in) throws IOException {
        List<String> warnings = new ArrayList<>();
        String text = decode(in.readAllBytes(), warnings);
        return parse(text, null, warnings);
    }

    /** Parses GEDCOM text that has already been decoded. */
    public GedcomDocument parse(String text) {
        return parse(text, null, new ArrayList<>());
    }

    private GedcomDocument parse(String text, Path source, List<String> warnings) {
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<GedcomNode> records = new ArrayList<>();
        Map<String, GedcomNode> index = new HashMap<>();
        List<GedcomNode> stack = new ArrayList<>();

        String[] lines = text.split("\\r\\n|\\r|\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            int lineNumber = i + 1;
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            Matcher m = LINE.matcher(line);
            if (!m.matches()) {
                warnings.add("Line " + lineNumber + ": not a GEDCOM line, skipped: " + abbreviate(line));
                continue;
            }
            int level = Integer.parseInt(m.group(1));
            String xref = m.group(2) == null ? null : m.group(2).substring(1, m.group(2).length() - 1);
            String tag = m.group(3);
            String payload = m.group(4);

            if (tag.equals("CONT") || tag.equals("CONC")) {
                if (level == 0 || stack.size() < level) {
                    warnings.add("Line " + lineNumber + ": " + tag + " without a line to continue, skipped");
                } else {
                    stack.get(level - 1).continueValue(unescape(payload == null ? "" : payload), tag.equals("CONT"));
                }
                continue;
            }
            if (level > stack.size()) {
                warnings.add("Line " + lineNumber + ": level " + level + " follows level " + (stack.size() - 1)
                        + "; treated as level " + stack.size());
                level = stack.size();
            }
            while (stack.size() > level) {
                stack.remove(stack.size() - 1);
            }

            String value = null;
            String pointer = null;
            boolean voidPointer = false;
            if (payload != null) {
                Matcher p = POINTER.matcher(payload);
                if (p.matches()) {
                    if (p.group(1).equals("VOID")) {
                        voidPointer = true;
                    } else {
                        pointer = p.group(1);
                    }
                } else {
                    value = unescape(payload);
                }
            }

            GedcomNode node = new GedcomNode(level, xref, tag, value, pointer, voidPointer, lineNumber);
            if (level == 0) {
                records.add(node);
                if (xref != null && index.putIfAbsent(xref, node) != null) {
                    warnings.add("Line " + lineNumber + ": duplicate identifier @" + xref + "@ ignored");
                }
            } else {
                stack.get(level - 1).addChild(node);
            }
            stack.add(node);
        }

        if (records.isEmpty()) {
            throw new GedcomException("No GEDCOM records found" + (source == null ? "" : " in " + source.getFileName()));
        }
        if (!records.get(0).tag().equals("HEAD")) {
            warnings.add("The file does not start with a HEAD record");
        }
        GedcomDocument doc = new GedcomDocument(records, index, warnings, source);
        String version = doc.version();
        if (!doc.isGedcom7()) {
            warnings.add(0, "GEDCOM version " + (version.isEmpty() ? "unknown" : version)
                    + " – read leniently; jFamilyTreeMaker is built for GEDCOM 7");
        }
        return new GedcomDocument(records, index, warnings, source);
    }

    /** GEDCOM 7: a payload starting with {@code @@} stands for a single leading {@code @}. */
    private static String unescape(String payload) {
        return payload.startsWith("@@") ? payload.substring(1) : payload;
    }

    static String decode(byte[] bytes, List<String> warnings) {
        if (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            return decodeUtf8(bytes, 3, warnings);
        }
        if (bytes.length >= 2 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        if (bytes.length >= 2 && bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        return decodeUtf8(bytes, 0, warnings);
    }

    private static String decodeUtf8(byte[] bytes, int offset, List<String> warnings) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
                    .toString();
        } catch (CharacterCodingException e) {
            warnings.add("The file is not valid UTF-8 (required by GEDCOM 7); read as Windows-1252 instead");
            return new String(bytes, offset, bytes.length - offset, Charset.forName("windows-1252"));
        }
    }

    private static String abbreviate(String s) {
        return s.length() <= 60 ? s : s.substring(0, 57) + "...";
    }
}
