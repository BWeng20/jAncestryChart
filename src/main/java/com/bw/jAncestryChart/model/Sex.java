package com.bw.jAncestryChart.model;

import java.util.Locale;

/** Values of the GEDCOM {@code SEX} structure. */
public enum Sex {
    MALE, FEMALE, INTERSEX, UNKNOWN;

    public static Sex fromGedcom(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "M" -> MALE;
            case "F" -> FEMALE;
            case "X" -> INTERSEX;
            default -> UNKNOWN;
        };
    }
}
