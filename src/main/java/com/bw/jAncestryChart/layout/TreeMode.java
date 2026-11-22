package com.bw.jAncestryChart.layout;

import java.util.Locale;

/** Which relatives of the root person the chart shows. */
public enum TreeMode {
    /** Parents, grandparents, ... in rows above the root person. */
    ANCESTORS("Ancestors"),
    /** Children, grandchildren, ... in rows below the root person. */
    DESCENDANTS("Descendants");

    private final String label;

    TreeMode(String label) {
        this.label = label;
    }

    public static TreeMode parse(String text) {
        String t = text.trim().toLowerCase(Locale.ROOT);
        if (t.startsWith("anc")) {
            return ANCESTORS;
        }
        if (t.startsWith("desc")) {
            return DESCENDANTS;
        }
        throw new IllegalArgumentException("Unknown mode '" + text + "' (use ancestors or descendants)");
    }

    @Override
    public String toString() {
        return label;
    }
}
