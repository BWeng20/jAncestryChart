package com.bw.jAncestryChart.layout;

import com.bw.jAncestryChart.model.Person;

import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

/** The complete geometry of one chart, ready to be painted. */
public final class TreeLayout {

    private final BranchNode root;
    private final TreeMode mode;
    private final List<BranchNode> nodes;
    private final List<Couple> couples;
    private final List<Path2D> links;
    private final Rectangle2D bounds;
    private final int maxGeneration;
    private final boolean marriages;

    TreeLayout(BranchNode root, TreeMode mode, List<BranchNode> nodes, List<Couple> couples, List<Path2D> links,
               Rectangle2D bounds, int maxGeneration, boolean marriages) {
        this.root = root;
        this.mode = mode;
        this.nodes = List.copyOf(nodes);
        this.couples = List.copyOf(couples);
        this.links = List.copyOf(links);
        this.bounds = bounds;
        this.maxGeneration = maxGeneration;
        this.marriages = marriages;
    }

    /** {@code true} if marriage seals are to be painted. */
    public boolean showsMarriages() {
        return marriages;
    }

    public BranchNode root() {
        return root;
    }

    public TreeMode mode() {
        return mode;
    }

    /** All boxes including partner boxes, generation by generation (root first). */
    public List<BranchNode> nodes() {
        return nodes;
    }

    /** Every couple shown side by side. */
    public List<Couple> couples() {
        return couples;
    }

    /** Lines from parents (or a couple) to their children. */
    public List<Path2D> links() {
        return links;
    }

    /** Area covered by everything that is drawn. */
    public Rectangle2D bounds() {
        return bounds;
    }

    /** Highest generation present (0 if only the root is shown). */
    public int maxGeneration() {
        return maxGeneration;
    }

    /** Number of different people shown. */
    public int personCount() {
        return (int) nodes.stream().map(BranchNode::person).distinct().count();
    }

    public String title() {
        return titleFor(mode, root.person());
    }

    static String titleFor(TreeMode mode, Person person) {
        return (mode == TreeMode.ANCESTORS ? "Ancestors of " : "Descendants of ") + person.fullName();
    }

    /** The node whose box (or portrait) contains the point, or {@code null}. */
    public BranchNode nodeAt(double x, double y) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            BranchNode n = nodes.get(i);
            if (n.collisionBounds().contains(x, y)) {
                return n;
            }
        }
        return null;
    }
}
