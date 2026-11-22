package com.bw.jAncestryChart.gedcom;

/** Thrown when a file cannot be interpreted as GEDCOM at all. */
public class GedcomException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GedcomException(String message) {
        super(message);
    }
}
