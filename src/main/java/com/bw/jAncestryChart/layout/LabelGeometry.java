package com.bw.jAncestryChart.layout;

/**
 * Sizes of the person boxes. The layout reserves exactly this space and the renderer draws into it,
 * so both agree without the layout needing fonts.
 *
 * <p>There are two kinds of boxes: the normal wide box (portrait on the left, text to the right) and,
 * in the compact outer generations, a narrow upright box (portrait on top, text below).
 */
public final class LabelGeometry {

    public static final double WIDTH = 176;
    public static final double HEIGHT = 58;
    /** The portrait is an upright oval, a little taller than the wide box it sits on. */
    public static final double PORTRAIT_WIDTH = 58;
    public static final double PORTRAIT_HEIGHT = 74;
    /** How far the portrait sticks out above (and, on a wide box, below) the box. */
    public static final double PORTRAIT_OUT = (PORTRAIT_HEIGHT - HEIGHT) / 2;
    /** Distance of the portrait from the left edge of a wide box. */
    public static final double PORTRAIT_INSET = 8;
    public static final double PORTRAIT_EXTRA = 64;
    /** Width of a narrow box, and the height of its text part (two lines of given names, surname, years). */
    public static final double NARROW_WIDTH = 100;
    public static final double NARROW_TEXT = 72;
    /** Part of a narrow box taken by the portrait (which sticks out {@link #PORTRAIT_OUT} above it). */
    public static final double NARROW_PORTRAIT = PORTRAIT_HEIGHT - PORTRAIT_OUT + 2;
    /** Height of the marriage seal. */
    public static final double BADGE_HEIGHT = 17;
    /** The root person's box is drawn larger. */
    public static final double ROOT_SCALE = 1.2;

    private LabelGeometry() {
    }

    /**
     * {@code true} if the box shows a portrait: a wide box only if there is a picture, a narrow box always
     * (initials stand in for a missing picture, so that all boxes of a compact row are alike).
     */
    public static boolean hasPortraitSlot(BranchNode node, boolean portraits) {
        return portraits && (node.isNarrow() || node.person().portrait() != null);
    }

    public static double scale(BranchNode node) {
        return node.isRoot() ? ROOT_SCALE : 1;
    }

    /**
     * Width of a marriage seal showing the given text (empty: rings only). A generous estimate without
     * fonts; the renderer measures the real text.
     */
    public static double badgeWidth(String text) {
        return text.isEmpty() ? 26 : 36 + 6.2 * text.length();
    }

    /**
     * Sizes the node, its partner boxes and everything further out.
     *
     * @param compactFrom first generation drawn with narrow boxes
     * @param marriages   seals are shown (they sit on the lower edge of touching boxes)
     */
    static void assignSizes(BranchNode node, boolean portraits, int compactFrom, boolean marriages) {
        size(node, portraits, compactFrom, marriages);
        for (BranchNode p : node.partners) {
            size(p, portraits, compactFrom, marriages);
        }
        for (BranchNode c : node.outer) {
            assignSizes(c, portraits, compactFrom, marriages);
        }
    }

    private static void size(BranchNode node, boolean portraits, int compactFrom, boolean marriages) {
        node.narrow = !node.isRoot() && node.generation() >= compactFrom;
        boolean slot = hasPortraitSlot(node, portraits);
        if (node.narrow) {
            node.width = NARROW_WIDTH;
            node.height = NARROW_TEXT + (slot ? NARROW_PORTRAIT : 0);
            node.overhang = slot ? PORTRAIT_OUT : 0;
            node.underhang = marriages ? BADGE_HEIGHT / 2 + 1 : 0;
            return;
        }
        double s = scale(node);
        node.width = (WIDTH + (slot ? PORTRAIT_EXTRA : 0)) * s;
        node.height = HEIGHT * s;
        double out = slot ? PORTRAIT_OUT * s : 0;
        node.overhang = out;
        node.underhang = Math.max(out, marriages ? BADGE_HEIGHT / 2 + 1 : 0);
    }
}
