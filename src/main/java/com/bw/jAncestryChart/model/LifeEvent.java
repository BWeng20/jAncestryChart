package com.bw.jAncestryChart.model;

import com.bw.jAncestryChart.gedcom.GedcomDate;

/**
 * A dated event such as a birth, christening, death or marriage.
 *
 * @param tag   the GEDCOM tag the event came from ({@code BIRT}, {@code CHR}, {@code DEAT}, ...)
 * @param date  the date, or {@code null}
 * @param place the place text, or {@code null}
 */
public record LifeEvent(String tag, GedcomDate date, String place) {

    public String label() {
        return switch (tag) {
            case "BIRT" -> "Born";
            case "CHR" -> "Christened";
            case "BAPM" -> "Baptized";
            case "DEAT" -> "Died";
            case "BURI" -> "Buried";
            case "CREM" -> "Cremated";
            case "MARR" -> "Married";
            default -> tag;
        };
    }

    /** Date and place, e.g. {@code 16 Sep 2011, Filderstadt}; empty if neither is known. */
    public String describe() {
        String d = date == null ? "" : date.toDisplayString();
        String p = place == null ? "" : place.trim();
        if (d.isEmpty()) {
            return p;
        }
        return p.isEmpty() ? d : d + ", " + p;
    }
}
