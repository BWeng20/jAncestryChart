package com.bw.jAncestryChart.layout;

import com.bw.jAncestryChart.gedcom.GedcomDate;
import com.bw.jAncestryChart.model.Family;

import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/**
 * Two partners standing side by side in the same row. Normally they stand close together (a small gap
 * between wide boxes, touching narrow ones) and the marriage seal (if marriages are shown) sits in the
 * middle at their lower edge. Divorced partners, and parents held apart by their own ancestors, stand
 * apart, joined by a connector with the seal on it. A partner further out (the third or later) is reached
 * by a bracket over the head of the partner in between.
 *
 * @param family    the couple's family
 * @param first     the left partner
 * @param second    the right partner
 * @param x         where the seal sits and the line to the children starts
 * @param y         the same, vertically: the lower edge of close partners, or the row's centre
 * @param sealWidth space reserved for the seal; 0 if marriages are not shown
 * @param glued     the partners stand close together (seal at the lower edge)
 * @param bracket   the line from the person over the partners in between to a partner further out, with
 *                  the seal on it; {@code null} if the two stand next to each other
 */
public record Couple(Family family, BranchNode first, BranchNode second, double x, double y, double sealWidth,
                     boolean glued, Path2D bracket) {

    public boolean hasSeal() {
        return sealWidth > 0;
    }

    /** The text on the seal: the full date if it is exact (only the year on narrow boxes); "div." for a divorce. */
    public String label() {
        return note(family, !first.isNarrow());
    }

    public Rectangle2D sealBounds() {
        double h = LabelGeometry.BADGE_HEIGHT;
        return new Rectangle2D.Double(x - sealWidth / 2, y - h / 2, sealWidth, h);
    }

    /**
     * Short marriage text: the full date if it is exact and {@code full} is set, otherwise the year;
     * with "div." for a divorce. Empty if nothing is known.
     */
    public static String note(Family family, boolean full) {
        if (family == null) {
            return "";
        }
        String date = "";
        if (family.marriage() != null && family.marriage().date() != null) {
            GedcomDate d = family.marriage().date();
            date = full && d.qualifier() == GedcomDate.Qualifier.EXACT ? d.toDisplayString() : d.yearLabel();
        }
        if (family.isDivorced()) {
            return date.isEmpty() ? "div." : date + ", div.";
        }
        return date;
    }
}
