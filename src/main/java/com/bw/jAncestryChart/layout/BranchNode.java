package com.bw.jAncestryChart.layout;

import com.bw.jAncestryChart.model.Family;
import com.bw.jAncestryChart.model.Person;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A person placed in the chart. The person the chart is about is the root; {@link #outer()} are the nodes
 * one generation further out (parents in an ancestor chart, children in a descendant chart).
 * {@link #partners()} are drawn as boxes of their own in the same row.
 * Coordinates are world units with y pointing down; the root sits at {@code (0, 0)}.
 */
public final class BranchNode {

    private final Person person;
    private final int generation;
    private final BranchNode inner;
    private final Family partnerFamily;
    final List<BranchNode> outer = new ArrayList<>();
    final List<BranchNode> partners = new ArrayList<>();
    boolean truncated;
    Family parentFamily;
    int size = 1;

    double x;
    double y;
    double width;
    double height;
    double overhang;
    double underhang;
    boolean narrow;
    /** Layout scratch: position of this subtree's anchor relative to the anchor of {@link #inner}. */
    double offset;
    /** Layout scratch: extra room before each partner box (index as in {@link #partners}), or null. */
    double[] extraGap;

    BranchNode(Person person, int generation, BranchNode inner) {
        this(person, generation, inner, null);
    }

    /** A partner box: {@code inner} is the person it is married to, {@code family} their family. */
    BranchNode(Person person, int generation, BranchNode inner, Family family) {
        this.person = person;
        this.generation = generation;
        this.inner = inner;
        this.partnerFamily = family;
    }

    public Person person() {
        return person;
    }

    /** 0 for the root person (and their partners), 1 for parents (or children), ... */
    public int generation() {
        return generation;
    }

    /**
     * The node this one grows out of, or {@code null} for the root. For a partner box, the person it is
     * married to.
     */
    public BranchNode inner() {
        return inner;
    }

    public List<BranchNode> outer() {
        return Collections.unmodifiableList(outer);
    }

    /** Partner boxes shown next to this person (the root's, and everybody's in a descendant chart). */
    public List<BranchNode> partners() {
        return Collections.unmodifiableList(partners);
    }

    /** {@code true} for a partner box (a person shown only as somebody's partner). */
    public boolean isPartner() {
        return partnerFamily != null;
    }

    /** For a partner box: the family of this partner and {@link #inner()}; {@code null} otherwise. */
    public Family partnerFamily() {
        return partnerFamily;
    }

    /**
     * The family this person is a child of, if it is shown: in an ancestor chart when both parents are
     * shown, in a descendant chart the family the person was born into.
     */
    public Family parentFamily() {
        return parentFamily;
    }

    /** How far the portrait sticks out above the box (0 if there is none). */
    public double overhang() {
        return overhang;
    }

    /** How far the portrait sticks out below the box (0 if there is none). */
    public double underhang() {
        return underhang;
    }

    /** The box plus the portrait sticking out of it; used to keep boxes apart and for hit tests. */
    public Rectangle2D collisionBounds() {
        return new Rectangle2D.Double(x - width / 2, y - height / 2 - overhang, width, height + overhang + underhang);
    }

    /** {@code true} for the narrow upright box of a compact (outer) generation. */
    public boolean isNarrow() {
        return narrow;
    }

    /** {@code true} if the person has further relatives that were cut off by the generation limit. */
    public boolean isTruncated() {
        return truncated;
    }

    public boolean isRoot() {
        return inner == null;
    }

    public boolean isLeaf() {
        return outer.isEmpty();
    }

    /** Number of nodes in this subtree including itself (partner boxes not counted). */
    public int size() {
        return size;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }

    public Point2D.Double center() {
        return new Point2D.Double(x, y);
    }

    public Rectangle2D bounds() {
        return new Rectangle2D.Double(x - width / 2, y - height / 2, width, height);
    }

    @Override
    public String toString() {
        return person.fullName() + " (gen " + generation + (isPartner() ? ", partner" : "") + ")";
    }
}
