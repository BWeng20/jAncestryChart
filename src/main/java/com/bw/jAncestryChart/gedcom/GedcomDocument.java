package com.bw.jAncestryChart.gedcom;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** A parsed GEDCOM file: its level-0 records plus an index by cross-reference identifier. */
public final class GedcomDocument {

    private final List<GedcomNode> records;
    private final Map<String, GedcomNode> index;
    private final List<String> warnings;
    private final Path source;

    GedcomDocument(List<GedcomNode> records, Map<String, GedcomNode> index, List<String> warnings, Path source) {
        this.records = List.copyOf(records);
        this.index = Map.copyOf(index);
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        this.source = source;
    }

    public List<GedcomNode> records() {
        return records;
    }

    /** All level-0 records with the given tag (e.g. {@code INDI}), in file order. */
    public List<GedcomNode> records(String tag) {
        List<GedcomNode> result = new ArrayList<>();
        for (GedcomNode r : records) {
            if (r.tag().equals(tag)) {
                result.add(r);
            }
        }
        return result;
    }

    /** Record with the given identifier; accepts {@code I1} as well as {@code @I1@}. */
    public GedcomNode record(String xref) {
        return xref == null ? null : index.get(stripAt(xref));
    }

    public GedcomNode header() {
        for (GedcomNode r : records) {
            if (r.tag().equals("HEAD")) {
                return r;
            }
        }
        return null;
    }

    /** Value of {@code HEAD.GEDC.VERS}, or an empty string. */
    public String version() {
        GedcomNode head = header();
        GedcomNode vers = head == null ? null : head.find("GEDC", "VERS");
        return vers == null ? "" : vers.valueOrEmpty().trim();
    }

    public boolean isGedcom7() {
        return version().startsWith("7");
    }

    /** Name of the program that wrote the file ({@code HEAD.SOUR.NAME} or {@code HEAD.SOUR}). */
    public String sourceProduct() {
        GedcomNode head = header();
        GedcomNode sour = head == null ? null : head.child("SOUR");
        if (sour == null) {
            return "";
        }
        String name = sour.childValue("NAME");
        return name != null ? name : sour.valueOrEmpty();
    }

    /** Problems that were tolerated while reading. */
    public List<String> warnings() {
        return warnings;
    }

    /** The file this document was read from, or {@code null}. */
    public Path source() {
        return source;
    }

    static String stripAt(String id) {
        String s = id.trim();
        if (s.length() >= 2 && s.startsWith("@") && s.endsWith("@")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
