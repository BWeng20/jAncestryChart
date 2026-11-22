package com.bw.jAncestryChart.layout;

import com.bw.jAncestryChart.model.Family;

import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Places the people of a {@link BranchNode} tree in generation rows: every generation is one row, and
 * partners stand side by side in the same row with their marriage between them. Ancestors grow upwards
 * from the root, descendants downwards.
 *
 * <ol>
 *   <li>Each person forms a <em>row group</em> with their partner boxes: the first partner to the right,
 *       the second to the left, and so on. Partners stand close together, with the marriage seal in the
 *       middle at their lower edge: {@link Settings#coupleGap()} apart for wide boxes, touching for the
 *       narrow upright boxes used from {@link Settings#compactFrom()} on. Divorced partners keep a gap
 *       wide enough for the seal. A third or later partner cannot stand next to the person: they stand
 *       outside the partner on that side, and a bracket leads from the person over that partner's head
 *       to them, with the seal on it above the gap between the two partners.</li>
 *   <li>Bottom-up, the subtrees of a person's parents (or children) are packed side by side as tightly
 *       as their outlines allow, row by row (as in Reingold&ndash;Tilford tree drawing). Packing is done
 *       from the left and from the right and the two results are averaged, so the drawing of a mirrored
 *       tree is the mirrored drawing and small families are spread evenly between large ones.</li>
 *   <li>In an ancestor chart with {@link Settings#straightLines()} the parents stand right above their
 *       child, so every line is straight. Married parents stand close if their own ancestors leave room
 *       for it; otherwise (and if divorced) they stand apart, joined by a connector with the seal on it, and
 *       the child may sit anywhere along that connector, as close as possible to the middle of the
 *       child's own ancestors. Without straight lines married parents always stand close and centred
 *       between the spots right above their own parents; the lines bend to reach them. In a descendant
 *       chart the children of each couple are centred under that couple's seal.</li>
 *   <li>Rows are stacked with a fixed gap; lines lead from each couple (or single parent) to the
 *       children.</li>
 * </ol>
 */
public final class GenerationLayout {

    /**
     * @param portraits   reserve room for portraits on the boxes
     * @param marriages   show marriage seals between partners
     * @param spacing     distance between neighbours and between rows: 1 = normal, from
     *                    {@value #MIN_SPACING} to {@value #MAX_SPACING}
     * @param compactFrom   first generation drawn with narrow upright boxes
     * @param straightLines ancestors: parents stand right above their child, moving apart where their
     *                      own parents need the room; otherwise married parents always stand close and
     *                      the lines bend
     * @param coupleGap     gap between the wide boxes of a married couple, from 0 (touching) to
     *                      {@value #MAX_COUPLE_GAP}; narrow boxes always touch
     */
    public record Settings(boolean portraits, boolean marriages, double spacing, int compactFrom,
                           boolean straightLines, double coupleGap) {

        public static final double MIN_SPACING = 0.5;
        public static final double MAX_SPACING = 3;
        public static final int DEFAULT_COMPACT_FROM = 4;
        public static final double DEFAULT_COUPLE_GAP = 12;
        public static final double MAX_COUPLE_GAP = 100;

        public Settings {
            spacing = Double.isNaN(spacing) ? 1 : Math.max(MIN_SPACING, Math.min(MAX_SPACING, spacing));
            compactFrom = Math.max(1, compactFrom);
            coupleGap = Double.isNaN(coupleGap) ? DEFAULT_COUPLE_GAP : Math.max(0, Math.min(MAX_COUPLE_GAP, coupleGap));
        }

        public static Settings defaults() {
            return new Settings(true, true, 1, DEFAULT_COMPACT_FROM, true, DEFAULT_COUPLE_GAP);
        }

        public Settings withPortraits(boolean value) {
            return new Settings(value, marriages, spacing, compactFrom, straightLines, coupleGap);
        }

        public Settings withMarriages(boolean value) {
            return new Settings(portraits, value, spacing, compactFrom, straightLines, coupleGap);
        }

        public Settings withSpacing(double value) {
            return new Settings(portraits, marriages, value, compactFrom, straightLines, coupleGap);
        }

        public Settings withCompactFrom(int value) {
            return new Settings(portraits, marriages, spacing, value, straightLines, coupleGap);
        }

        public Settings withStraightLines(boolean value) {
            return new Settings(portraits, marriages, spacing, compactFrom, value, coupleGap);
        }

        public Settings withCoupleGap(double value) {
            return new Settings(portraits, marriages, spacing, compactFrom, straightLines, value);
        }
    }

    /** Free space between neighbouring boxes in a row (at spacing 1). */
    static final double GAP = 24;
    /** Free space between two rows (at spacing 1); the lines to the children run here. */
    static final double ROW_GAP = 64;
    /** Least gap between two partners who stand apart, when no seal is shown. */
    static final double COUPLE_GAP = 40;
    /** Visible connector on each side of a seal. */
    static final double CONNECTOR = 14;
    /** Vertical distance between the horizontal lines of different families of one person. */
    private static final double BUS_STEP = 8;
    /** Height of the first bracket to a partner further out, above the top of the row. */
    static final double BRACKET_RISE = 18;
    /** Vertical distance between nested brackets. */
    static final double BRACKET_STEP = 24;
    /** Distance of a bracket's foot from the side of the person's box. */
    private static final double BRACKET_INSET = 16;

    private final Settings settings;

    public GenerationLayout(Settings settings) {
        this.settings = Objects.requireNonNull(settings);
    }

    public TreeLayout layout(BranchNode root, TreeMode mode) {
        LabelGeometry.assignSizes(root, settings.portraits(), settings.compactFrom(), settings.marriages());
        arrange(root, mode);
        Map<BranchNode, Double> sealX = new IdentityHashMap<>();
        place(root, 0, sealX);

        List<List<BranchNode>> rows = rows(root);
        Rows r = stackRows(rows, mode);
        List<BranchNode> nodes = new ArrayList<>();
        rows.forEach(nodes::addAll);

        List<Couple> couples = new ArrayList<>();
        Map<BranchNode, Couple> byPartner = new IdentityHashMap<>();    // partner box -> couple
        Map<BranchNode, Couple> byChild = new IdentityHashMap<>();      // ancestors: child -> parents
        for (BranchNode n : nodes) {
            for (int i = 0; i < n.partners.size(); i++) {
                BranchNode p = n.partners.get(i);
                boolean left = p.x < n.x;
                int level = level(i);
                Couple c = level == 0
                        ? couple(p.partnerFamily(), left ? p : n, left ? n : p, sealX.get(p))
                        : bracketCouple(n, p, level, sealX.get(p), r);
                couples.add(c);
                byPartner.put(p, c);
            }
            if (mode == TreeMode.ANCESTORS && n.parentFamily != null) {
                // the seal sits in the middle between close parents, otherwise right above the child
                BranchNode a = n.outer.get(0);
                BranchNode b = n.outer.get(1);
                Couple c = couple(n.parentFamily, a, b,
                        close(n.parentFamily, a, b) ? (a.x + a.width / 2 + b.x - b.width / 2) / 2 : n.x);
                couples.add(c);
                byChild.put(n, c);
            }
        }

        List<Path2D> links = new ArrayList<>();
        for (BranchNode n : nodes) {
            if (n.outer.isEmpty()) {
                continue;
            }
            double mid = r.between(n.generation());
            if (mode == TreeMode.ANCESTORS) {
                links.add(ancestorLink(n, byChild.get(n), mid));
            } else {
                links.addAll(descendantLinks(n, byPartner, mid));
            }
        }

        Rectangle2D bounds = root.collisionBounds();
        for (BranchNode n : nodes) {
            bounds.add(n.collisionBounds());
        }
        for (Couple c : couples) {
            if (c.hasSeal()) {
                bounds.add(c.sealBounds());
            }
            if (c.bracket() != null) {
                bounds.add(c.bracket().getBounds2D());
            }
        }
        for (Path2D link : links) {
            bounds.add(link.getBounds2D());
        }
        double pad = 30;
        bounds.setRect(bounds.getX() - pad, bounds.getY() - pad, bounds.getWidth() + 2 * pad,
                bounds.getHeight() + 2 * pad);
        return new TreeLayout(root, mode, nodes, couples, links, bounds, rows.size() - 1, settings.marriages());
    }

    // ------------------------------------------------------------------ couples

    private double gap() {
        return GAP * settings.spacing();
    }

    /** Partners may stand close together, unless they are divorced. */
    private static boolean mayBeClose(Family family) {
        return family != null && !family.isDivorced();
    }

    /** Gap between partners standing close: the configured couple gap for wide boxes, none for narrow ones. */
    private double closeGap(BranchNode box) {
        return box.isNarrow() ? 0 : settings.coupleGap();
    }

    /** Room between two partners in a row group: {@link #closeGap} if they may be close, otherwise {@link #spread}. */
    private double coupleGap(Family family, BranchNode box) {
        return mayBeClose(family) ? closeGap(box) : spread(family, box);
    }

    /**
     * Least room between two partners who do not touch: the seal plus a bit of connector on either
     * side. {@code box} is one of the two partners.
     */
    private double spread(Family family, BranchNode box) {
        return settings.marriages() ? sealWidth(family, box) + 2 * CONNECTOR : COUPLE_GAP;
    }

    /** The seal shows the full date on wide boxes and only the year on narrow ones. */
    private double sealWidth(Family family, BranchNode box) {
        return settings.marriages() ? LabelGeometry.badgeWidth(Couple.note(family, !box.isNarrow())) : 0;
    }

    /** The partners stand {@link #closeGap} apart (not pushed apart by their ancestors). */
    private boolean close(Family family, BranchNode first, BranchNode second) {
        return mayBeClose(family)
                && second.x - second.width / 2 - (first.x + first.width / 2) <= closeGap(first) + 1e-6;
    }

    private Couple couple(Family family, BranchNode first, BranchNode second, double x) {
        boolean glued = close(family, first, second);
        // close partners: in the middle at the lower edge (of the lower box, if the root is one of them)
        double y = glued ? first.y + Math.min(first.height, second.height) / 2 : first.y;
        return new Couple(family, first, second, x, y, sealWidth(family, first), glued, null);
    }

    /**
     * How far out the partner with this index stands: 0 right next to the person (the first partner to
     * the right, the second to the left), 1 one partner further out, and so on.
     */
    private static int level(int partnerIndex) {
        return partnerIndex / 2;
    }

    /**
     * A partner standing further out: a bracket leads from the top of the person's box up over the
     * partners in between and down to the partner; the seal sits on it above the gap next to the partner,
     * where the line to their children goes down.
     */
    private Couple bracketCouple(BranchNode n, BranchNode p, int level, double sealX, Rows r) {
        boolean left = p.x < n.x;
        int g = n.generation();
        double y = r.y()[g] - r.top()[g] - BRACKET_RISE - (level - 1) * BRACKET_STEP;
        double inset = BRACKET_INSET + (level - 1) * BRACKET_STEP / 3;
        if (left && !n.isNarrow() && LabelGeometry.hasPortraitSlot(n, settings.portraits())) {
            inset += (LabelGeometry.PORTRAIT_INSET + LabelGeometry.PORTRAIT_WIDTH) * LabelGeometry.scale(n);
        }
        double from = left ? n.x - n.width / 2 + inset : n.x + n.width / 2 - inset;
        Path2D.Double bracket = new Path2D.Double();
        bracket.moveTo(from, n.y - n.height / 2);
        bracket.lineTo(from, y);
        bracket.lineTo(p.x, y);
        bracket.lineTo(p.x, p.y - p.height / 2);
        return new Couple(p.partnerFamily(), left ? p : n, left ? n : p, sealX, y, sealWidth(p.partnerFamily(), n),
                false, bracket);
    }

    // ------------------------------------------------------------------ horizontal placement

    /**
     * A person's box with their partners' boxes, left to right; positions relative to the group's left edge.
     * {@code anchor} is the centre of the person's own box.
     */
    private record Group(List<BranchNode> boxes, double[] lefts, double[] sealX, double width, double anchor) {

        /** Centre of the seal between the person and the partner of {@code family}, or of the person's box. */
        double sealOf(BranchNode n, Family family) {
            Object partner = family == null ? null : family.partnerOf(n.person());
            for (int i = 0; i < boxes.size(); i++) {
                if (partner != null && boxes.get(i).isPartner() && boxes.get(i).person() == partner) {
                    return sealX[i];
                }
            }
            return anchor;
        }
    }

    private Group group(BranchNode n) {
        List<BranchNode> right = new ArrayList<>();
        List<BranchNode> left = new ArrayList<>();
        for (int i = 0; i < n.partners.size(); i++) {
            (i % 2 == 0 ? right : left).add(n.partners.get(i));
        }
        double[] extra = new double[n.partners.size() + 1];     // by box index, filled below
        List<BranchNode> boxes = new ArrayList<>(left.reversed());
        int self = boxes.size();
        boxes.add(n);
        boxes.addAll(right);
        if (n.extraGap != null) {
            for (int i = 0; i < boxes.size(); i++) {
                int index = n.partners.indexOf(boxes.get(i));
                extra[i] = index < 0 ? 0 : n.extraGap[index];
            }
        }

        double[] lefts = new double[boxes.size()];
        double[] seals = new double[boxes.size()];
        double cursor = 0;
        for (int i = 0; i < boxes.size(); i++) {
            if (i > 0) {
                // the gap belongs to the partner on the outer side of it; partners further out are
                // reached by a bracket and only keep the normal distance from their neighbour
                int partner = i <= self ? i - 1 : i;
                boolean further = Math.abs(partner - self) > 1;
                double g = (further ? gap() : coupleGap(boxes.get(partner).partnerFamily(), boxes.get(partner)))
                        + extra[partner];
                seals[partner] = cursor + g / 2;
                cursor += g;
            }
            lefts[i] = cursor;
            cursor += boxes.get(i).width;
        }
        return new Group(boxes, lefts, seals, cursor, lefts[self] + n.width / 2);
    }

    /** Left and right edge of a subtree per row, relative to the subtree's anchor. */
    private static final class Contour {
        final List<double[]> rows = new ArrayList<>();

        int size() {
            return rows.size();
        }

        double left(int row) {
            return rows.get(row)[0];
        }

        double right(int row) {
            return rows.get(row)[1];
        }

        /** Middle between the leftmost and the rightmost point of all rows. */
        double middle() {
            double l = Double.POSITIVE_INFINITY;
            double r = Double.NEGATIVE_INFINITY;
            for (double[] e : rows) {
                l = Math.min(l, e[0]);
                r = Math.max(r, e[1]);
            }
            return (l + r) / 2;
        }

        void include(int row, double l, double r) {
            if (row == rows.size()) {
                rows.add(new double[] {l, r});
            } else {
                double[] e = rows.get(row);
                e[0] = Math.min(e[0], l);
                e[1] = Math.max(e[1], r);
            }
        }

        void include(Contour other, double shift, int rowOffset) {
            for (int i = 0; i < other.size(); i++) {
                include(i + rowOffset, other.left(i) + shift, other.right(i) + shift);
            }
        }
    }

    /**
     * Bottom-up pass: packs the subtrees and stores each one's position relative to its inner node.
     * Returns the outline of the subtree relative to the centre of {@code n}'s box.
     */
    private Contour arrange(BranchNode n, TreeMode mode) {
        Contour outer = null;
        if (!n.outer.isEmpty()) {
            outer = mode == TreeMode.ANCESTORS ? arrangeParents(n) : arrangeChildren(n);
        }
        Group g = group(n);         // after arranging the children, which may move partners apart
        Contour result = new Contour();
        result.include(0, -g.anchor(), g.width() - g.anchor());
        if (outer != null) {
            result.include(outer, 0, 1);
        }
        return result;
    }

    /**
     * Father and mother side by side, right above their child. Married parents touch if their own
     * ancestors leave room for it, and the child stands under the seam. Otherwise they stand at least
     * {@link #spread} apart and the child stands under their marriage, which may sit anywhere along the
     * connector between them; the spot closest to the middle of the child's ancestors is taken.
     */
    private Contour arrangeParents(BranchNode n) {
        Family family = n.parentFamily;
        if (family == null) {
            Packed p = pack(n.outer, gap(), TreeMode.ANCESTORS);
            return p.apply(n.outer, -p.pos()[0]);                // a single parent: right above
        }
        if (!settings.straightLines() && mayBeClose(family)) {
            return arrangeGluedParents(n);
        }
        BranchNode father = n.outer.get(0);
        double spread = spread(family, father);
        double close = mayBeClose(family) ? closeGap(father) : spread;
        Packed p = pack(n.outer, close, TreeMode.ANCESTORS);
        double room = rowGap(p);
        if (room > close + 1e-6 && room < spread) {
            // held apart by their ancestors, but not far enough for the seal: make room for it
            p = pack(n.outer, spread, TreeMode.ANCESTORS);
        }
        double from = p.pos()[0] + p.subs()[0].right(0);           // right edge of the father
        double to = p.pos()[1] + p.subs()[1].left(0);              // left edge of the mother
        double center;
        if (to - from <= close + 1e-6) {
            center = (from + to) / 2;                              // close: under the middle
        } else {
            double margin = CONNECTOR + sealWidth(family, father) / 2;
            center = from + margin <= to - margin
                    ? Math.max(from + margin, Math.min(to - margin, p.run().middle()))
                    : (from + to) / 2;
        }
        return p.apply(n.outer, -center);
    }

    /**
     * Married parents without straight lines: they stand close, so they cannot both stand right under
     * their own parents. Their ancestors are packed as usual (row by row from the grandparents on), and
     * the couple stands centred between the two spots where father and mother would ideally stand; the
     * lines from the grandparents bend to reach them. The child stands under the middle of the couple.
     */
    private Contour arrangeGluedParents(BranchNode n) {
        BranchNode father = n.outer.get(0);
        BranchNode mother = n.outer.get(1);
        Contour f = arrange(father, TreeMode.ANCESTORS);
        Contour m = arrange(mother, TreeMode.ANCESTORS);
        double close = closeGap(father);
        double touching = father.width / 2 + close + mother.width / 2;
        double need = Double.NEGATIVE_INFINITY;
        for (int row = 1; row < Math.min(f.size(), m.size()); row++) {
            need = Math.max(need, f.right(row) + gap() - m.left(row));
        }
        double idealMother = need == Double.NEGATIVE_INFINITY ? touching : need;   // father ideally at 0
        double seam = (father.width / 2 + idealMother - mother.width / 2) / 2;
        double fatherX = seam - close / 2 - father.width / 2;
        double motherX = seam + close / 2 + mother.width / 2;
        // the grandparents stay where the packing put them, relative to the moved parents
        for (BranchNode c : father.outer) {
            c.offset -= fatherX;
        }
        for (BranchNode c : mother.outer) {
            c.offset -= motherX - idealMother;
        }
        father.offset = fatherX - seam;
        mother.offset = motherX - seam;
        Contour run = new Contour();
        run.include(0, -close / 2 - father.width, close / 2 + mother.width);
        for (int row = 1; row < f.size(); row++) {
            run.include(row, f.left(row) - seam, f.right(row) - seam);
        }
        for (int row = 1; row < m.size(); row++) {
            run.include(row, m.left(row) + idealMother - seam, m.right(row) + idealMother - seam);
        }
        return run;
    }

    /** Free space between the first two boxes of a packed run. */
    private static double rowGap(Packed p) {
        return p.pos()[1] + p.subs()[1].left(0) - (p.pos()[0] + p.subs()[0].right(0));
    }

    /**
     * The children family by family, each family centred under the seal of its couple (or under the
     * person if the other parent is unknown). Families that would collide are pushed apart: once to the
     * right and once to the left, and the two results are averaged.
     */
    private Contour arrangeChildren(BranchNode n) {
        Map<Family, List<BranchNode>> families = new LinkedHashMap<>();
        for (BranchNode c : n.outer) {
            families.computeIfAbsent(c.parentFamily, f -> new ArrayList<>()).add(c);
        }
        Map<Family, Packed> packed = new LinkedHashMap<>();
        for (Map.Entry<Family, List<BranchNode>> e : families.entrySet()) {
            packed.put(e.getKey(), pack(e.getValue(), gap(), TreeMode.DESCENDANTS));
        }
        n.extraGap = new double[n.partners.size()];
        List<Block> blocks = childBlocks(n, families, packed);
        for (int round = 0; round < 2 * families.size() && makeRoomForChildren(n, blocks); round++) {
            blocks = childBlocks(n, families, packed);
        }
        int k = blocks.size();

        double[] right = new double[k];
        Contour all = new Contour();
        for (int i = 0; i < k; i++) {
            Contour run = blocks.get(i).packed().run();
            double shift = blocks.get(i).target();
            for (int row = 0; row < Math.min(all.size(), run.size()); row++) {
                shift = Math.max(shift, all.right(row) + gap() - run.left(row));
            }
            right[i] = shift;
            all.include(run, shift, 0);
        }
        double[] left = new double[k];
        all = new Contour();
        for (int i = k - 1; i >= 0; i--) {
            Contour run = blocks.get(i).packed().run();
            double shift = blocks.get(i).target();
            for (int row = 0; row < Math.min(all.size(), run.size()); row++) {
                shift = Math.min(shift, all.left(row) - gap() - run.right(row));
            }
            left[i] = shift;
            all.include(run, shift, 0);
        }

        Contour result = new Contour();
        List<BranchNode> order = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            Block b = blocks.get(i);
            result.include(b.packed().apply(b.children(), (left[i] + right[i]) / 2), 0, 0);
            order.addAll(b.children());
        }
        n.outer.clear();
        n.outer.addAll(order);      // left to right
        return result;
    }

    /** The children of one family, packed, and where they would stand right under their couple's seal. */
    private record Block(Family family, List<BranchNode> children, double target, Packed packed) {
    }

    private List<Block> childBlocks(BranchNode n, Map<Family, List<BranchNode>> families, Map<Family, Packed> packed) {
        Group g = group(n);
        List<Block> blocks = new ArrayList<>();
        for (Map.Entry<Family, List<BranchNode>> e : families.entrySet()) {
            Packed p = packed.get(e.getKey());
            blocks.add(new Block(e.getKey(), e.getValue(), g.sealOf(n, e.getKey()) - g.anchor() - p.center(), p));
        }
        blocks.sort(Comparator.comparingDouble(Block::target));
        return blocks;
    }

    /**
     * If the children of two neighbouring families cannot both stand right under their seals, moves the
     * outer of the two partners further out (if that does not part a close couple), so that the lines
     * stay straight. Returns {@code true} if something was moved.
     */
    private boolean makeRoomForChildren(BranchNode n, List<Block> blocks) {
        for (int i = 0; i + 1 < blocks.size(); i++) {
            Block a = blocks.get(i);
            Block b = blocks.get(i + 1);
            Contour ra = a.packed().run();
            Contour rb = b.packed().run();
            double need = Double.NEGATIVE_INFINITY;
            for (int row = 0; row < Math.min(ra.size(), rb.size()); row++) {
                need = Math.max(need, ra.right(row) + gap() - rb.left(row));
            }
            double missing = need - (b.target() - a.target());
            if (missing <= 1e-6) {
                continue;
            }
            // b's partner on the right, or a's partner on the left, can move outwards
            int pb = partnerIndex(n, b.family());
            int pa = partnerIndex(n, a.family());
            int move = pb >= 0 && pb % 2 == 0 && movable(n, pb) ? pb
                    : pa >= 0 && pa % 2 == 1 && movable(n, pa) ? pa : -1;
            if (move >= 0) {
                n.extraGap[move] += 2 * missing;       // the seal sits in the middle of the gap
                return true;
            }
        }
        return false;
    }

    private static int partnerIndex(BranchNode n, Family family) {
        for (int i = 0; i < n.partners.size(); i++) {
            if (n.partners.get(i).partnerFamily() == family) {
                return i;
            }
        }
        return -1;
    }

    /** The gap before this partner may grow: they are reached by a bracket, or divorced. */
    private static boolean movable(BranchNode n, int partnerIndex) {
        return level(partnerIndex) > 0 || !mayBeClose(n.partners.get(partnerIndex).partnerFamily());
    }

    /** Subtrees packed side by side as tightly as their outlines allow. */
    private record Packed(Contour[] subs, double[] pos, Contour run) {

        /** Middle between the first and the last subtree. */
        double center() {
            return (pos[0] + pos[pos.length - 1]) / 2;
        }

        /** Moves the packed run by {@code shift}, records each subtree's offset and returns the outline. */
        Contour apply(List<BranchNode> nodes, double shift) {
            for (int i = 0; i < nodes.size(); i++) {
                nodes.get(i).offset = pos[i] + shift;
            }
            Contour moved = new Contour();
            moved.include(run, shift, 0);
            return moved;
        }
    }

    /**
     * Packs the subtrees of {@code nodes} side by side: once from the left (each as far left as the
     * ones before allow) and once from the right, and averages the two. Both packings keep every pair
     * of subtrees far enough apart and have the same total width, so their average does too.
     *
     * @param firstGap space between the first two boxes in their own row (a couple needs room for the seal)
     */
    private Packed pack(List<BranchNode> nodes, double firstGap, TreeMode mode) {
        int k = nodes.size();
        Contour[] subs = new Contour[k];
        for (int i = 0; i < k; i++) {
            subs[i] = arrange(nodes.get(i), mode);
        }
        // need[i][j]: smallest distance from subtree i to subtree j (i left of j)
        double[][] need = new double[k][k];
        for (int i = 0; i < k; i++) {
            for (int j = i + 1; j < k; j++) {
                double d = Double.NEGATIVE_INFINITY;
                for (int row = 0; row < Math.min(subs[i].size(), subs[j].size()); row++) {
                    double space = row == 0 && i == 0 && j == 1 ? firstGap : gap();
                    d = Math.max(d, subs[i].right(row) + space - subs[j].left(row));
                }
                need[i][j] = d;
            }
        }
        double[] fromLeft = new double[k];
        for (int j = 1; j < k; j++) {
            fromLeft[j] = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < j; i++) {
                fromLeft[j] = Math.max(fromLeft[j], fromLeft[i] + need[i][j]);
            }
        }
        double[] fromRight = new double[k];
        for (int i = k - 2; i >= 0; i--) {
            fromRight[i] = Double.POSITIVE_INFINITY;
            for (int j = i + 1; j < k; j++) {
                fromRight[i] = Math.min(fromRight[i], fromRight[j] - need[i][j]);
            }
        }
        double[] pos = new double[k];
        Contour run = new Contour();
        for (int i = 0; i < k; i++) {
            pos[i] = (fromLeft[i] + fromRight[i] - fromRight[0]) / 2;
            run.include(subs[i], pos[i], 0);
        }
        return new Packed(subs, pos, run);
    }

    /** Top-down pass: absolute x positions; {@code x} is the centre of {@code n}'s box. */
    private void place(BranchNode n, double x, Map<BranchNode, Double> sealX) {
        Group g = group(n);
        double left = x - g.anchor();
        for (int i = 0; i < g.boxes().size(); i++) {
            BranchNode b = g.boxes().get(i);
            b.x = left + g.lefts()[i] + b.width / 2;
            if (b.isPartner()) {
                sealX.put(b, left + g.sealX()[i]);
            }
        }
        for (BranchNode c : n.outer) {
            place(c, x + c.offset, sealX);
        }
    }

    // ------------------------------------------------------------------ rows

    private static List<List<BranchNode>> rows(BranchNode root) {
        List<List<BranchNode>> rows = new ArrayList<>();
        List<BranchNode> current = List.of(root);
        while (!current.isEmpty()) {
            List<BranchNode> row = new ArrayList<>();
            List<BranchNode> next = new ArrayList<>();
            for (BranchNode n : current) {
                row.add(n);
                row.addAll(n.partners);
                next.addAll(n.outer);
            }
            rows.add(row);
            current = next;
        }
        return rows;
    }

    /** Vertical position of every row, and where the lines between two rows run.
     * @param top   distance from a row's centre to the top of its highest box (portraits included)
     * @param above the same, including the brackets to partners further out
     */
    private record Rows(double[] y, double[] top, double[] above, double[] below, boolean upwards) {
        /** Middle of the free space between row {@code g} and the next row further out. */
        double between(int g) {
            return upwards
                    ? ((y[g] - above[g]) + (y[g + 1] + below[g + 1])) / 2
                    : ((y[g] + below[g]) + (y[g + 1] - above[g + 1])) / 2;
        }
    }

    private Rows stackRows(List<List<BranchNode>> rows, TreeMode mode) {
        int count = rows.size();
        double[] above = new double[count];
        double[] below = new double[count];
        for (int g = 0; g < count; g++) {
            for (BranchNode n : rows.get(g)) {
                above[g] = Math.max(above[g], n.height / 2 + n.overhang);
                below[g] = Math.max(below[g], n.height / 2 + n.underhang);
            }
        }
        double[] top = above.clone();
        for (int g = 0; g < count; g++) {
            int levels = 0;
            for (BranchNode n : rows.get(g)) {
                levels = Math.max(levels, n.partners.isEmpty() ? 0 : level(n.partners.size() - 1));
            }
            if (levels > 0) {
                above[g] += BRACKET_RISE + (levels - 1) * BRACKET_STEP + LabelGeometry.BADGE_HEIGHT / 2 + 2;
            }
        }
        boolean upwards = mode == TreeMode.ANCESTORS;
        double rowGap = ROW_GAP * settings.spacing();
        double[] y = new double[count];
        for (int g = 1; g < count; g++) {
            y[g] = upwards ? y[g - 1] - (above[g - 1] + rowGap + below[g])
                    : y[g - 1] + (below[g - 1] + rowGap + above[g]);
        }
        for (int g = 0; g < count; g++) {
            for (BranchNode n : rows.get(g)) {
                n.y = y[g];
            }
        }
        return new Rows(y, top, above, below, upwards);
    }

    // ------------------------------------------------------------------ lines

    /** From the parents' marriage (or the single parent) down to the child. */
    private static Path2D ancestorLink(BranchNode child, Couple parents, double mid) {
        double sx;
        double sy;
        if (parents != null) {
            sx = parents.x();
            sy = parents.y();
        } else {
            BranchNode parent = child.outer.get(0);
            sx = parent.x;
            sy = parent.y + parent.height / 2;
        }
        Path2D.Double p = new Path2D.Double();
        p.moveTo(sx, sy);
        p.lineTo(sx, mid);
        p.lineTo(child.x, mid);
        p.lineTo(child.x, child.y - child.height / 2);
        return p;
    }

    /**
     * One line per family: from the couple (or the single parent) down to a horizontal bar, and from
     * there down to each child of that family.
     */
    private static List<Path2D> descendantLinks(BranchNode n, Map<BranchNode, Couple> byPartner, double mid) {
        Map<Family, List<BranchNode>> families = new LinkedHashMap<>();
        for (BranchNode c : n.outer) {
            families.computeIfAbsent(c.parentFamily, f -> new ArrayList<>()).add(c);
        }
        List<Path2D> links = new ArrayList<>();
        int j = 0;
        for (Map.Entry<Family, List<BranchNode>> e : families.entrySet()) {
            Couple couple = coupleWith(n, e.getKey(), byPartner);
            double sx = couple != null ? couple.x() : n.x;
            double sy = couple != null ? couple.y() : n.y + n.height / 2;
            double bus = mid + (j++ - (families.size() - 1) / 2.0) * BUS_STEP;
            double from = sx;
            double to = sx;
            for (BranchNode c : e.getValue()) {
                from = Math.min(from, c.x);
                to = Math.max(to, c.x);
            }
            Path2D.Double p = new Path2D.Double();
            p.moveTo(sx, sy);
            p.lineTo(sx, bus);
            p.moveTo(from, bus);
            p.lineTo(to, bus);
            for (BranchNode c : e.getValue()) {
                p.moveTo(c.x, bus);
                p.lineTo(c.x, c.y - c.height / 2);
            }
            links.add(p);
        }
        return links;
    }

    private static Couple coupleWith(BranchNode n, Family family, Map<BranchNode, Couple> byPartner) {
        if (family == null) {
            return null;
        }
        for (BranchNode p : n.partners) {
            if (p.person() == family.partnerOf(n.person())) {
                return byPartner.get(p);
            }
        }
        return null;
    }
}
