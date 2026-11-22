package com.bw.jAncestryChart.model;

import java.awt.Rectangle;
import java.nio.file.Path;

/**
 * An image file linked to a person.
 *
 * @param file  the resolved file on disk
 * @param crop  the GEDCOM 7 {@code CROP} region in pixels ({@code width}/{@code height} &lt;= 0 mean
 *              "to the image edge"), or {@code null}
 * @param title optional title
 */
public record MediaReference(Path file, Rectangle crop, String title) {
}
