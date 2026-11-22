package com.bw.jAncestryChart.gedcom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One GEDCOM line together with its substructures.
 * <p>
 * Continuation lines ({@code CONT}, and the GEDCOM 5.5.1 {@code CONC}) are merged into the payload
 * of the line they continue, so they never show up as children.
 */
public final class GedcomNode {

    private final int level;
    private final String xref;
    private final String tag;
    private final String pointer;
    private final boolean voidPointer;
    private final int lineNumber;
    private final List<GedcomNode> children = new ArrayList<>();
    private String value;
    private GedcomNode parent;

    GedcomNode(int level, String xref, String tag, String value, String pointer, boolean voidPointer, int lineNumber) {
        this.level = level;
        this.xref = xref;
        this.tag = tag;
        this.value = value;
        this.pointer = pointer;
        this.voidPointer = voidPointer;
        this.lineNumber = lineNumber;
    }

    public int level() {
        return level;
    }

    /** Cross-reference identifier of a record without the surrounding {@code @}, or {@code null}. */
    public String xref() {
        return xref;
    }

    public String tag() {
        return tag;
    }

    /**
     * Text payload with continuation lines merged and the leading {@code @@} escape removed.
     * {@code null} if the line has no payload or the payload is a pointer.
     */
    public String value() {
        return value;
    }

    public String valueOrEmpty() {
        return value == null ? "" : value;
    }

    /** Target identifier (without {@code @}) if the payload is a pointer, otherwise {@code null}. */
    public String pointer() {
        return pointer;
    }

    public boolean isPointer() {
        return pointer != null || voidPointer;
    }

    /** {@code true} for the GEDCOM 7 null pointer {@code @VOID@}. */
    public boolean isVoidPointer() {
        return voidPointer;
    }

    public int lineNumber() {
        return lineNumber;
    }

    public GedcomNode parent() {
        return parent;
    }

    public List<GedcomNode> children() {
        return Collections.unmodifiableList(children);
    }

    /** First direct child with the given tag, or {@code null}. */
    public GedcomNode child(String childTag) {
        for (GedcomNode c : children) {
            if (c.tag.equals(childTag)) {
                return c;
            }
        }
        return null;
    }

    /** All direct children with the given tag, in file order. */
    public List<GedcomNode> children(String childTag) {
        List<GedcomNode> result = new ArrayList<>();
        for (GedcomNode c : children) {
            if (c.tag.equals(childTag)) {
                result.add(c);
            }
        }
        return result;
    }

    /** Payload of the first child with the given tag, or {@code null}. */
    public String childValue(String childTag) {
        GedcomNode c = child(childTag);
        return c == null ? null : c.value;
    }

    public boolean has(String childTag) {
        return child(childTag) != null;
    }

    /** Follows a path of tags, e.g. {@code find("BIRT", "DATE")}; returns the first match or {@code null}. */
    public GedcomNode find(String... path) {
        GedcomNode current = this;
        for (String t : path) {
            current = current.child(t);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    void addChild(GedcomNode child) {
        child.parent = this;
        children.add(child);
    }

    void continueValue(String text, boolean newLine) {
        String base = value == null ? "" : value;
        value = newLine ? base + "\n" + text : base + text;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder().append(level).append(' ');
        if (xref != null) {
            sb.append('@').append(xref).append("@ ");
        }
        sb.append(tag);
        if (pointer != null) {
            sb.append(" @").append(pointer).append('@');
        } else if (voidPointer) {
            sb.append(" @VOID@");
        } else if (value != null) {
            sb.append(' ').append(value);
        }
        return sb.toString();
    }
}
