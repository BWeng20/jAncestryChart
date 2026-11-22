package com.bw.jAncestryChart.render;

import com.bw.jAncestryChart.layout.BranchNode;
import com.bw.jAncestryChart.layout.Couple;
import com.bw.jAncestryChart.layout.LabelGeometry;
import com.bw.jAncestryChart.layout.TreeLayout;
import com.bw.jAncestryChart.layout.TreeMode;
import com.bw.jAncestryChart.model.Family;
import com.bw.jAncestryChart.model.Person;
import com.bw.jAncestryChart.model.Sex;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Paints a {@link TreeLayout} with Java2D: lines to the children, couples with their marriage seals,
 * and the person boxes. Used both by the on-screen canvas and for image export.
 */
public final class TreeRenderer {

    /** @param portraits draw portraits on the boxes */
    public record Options(boolean portraits) {
    }

    private static final Color BACKGROUND = new Color(0xF7F3EA);
    private static final Color LINK = new Color(0x8A7A66);
    private static final Color LABEL_TOP = new Color(0xFFFDF7);
    private static final Color LABEL_BOTTOM = new Color(0xF4ECD8);
    private static final Color ROOT_TOP = new Color(0xFFF6DC);
    private static final Color ROOT_BOTTOM = new Color(0xF0DDAA);
    private static final Color MALE = new Color(0x4F7396);
    private static final Color FEMALE = new Color(0xA65A70);
    private static final Color OTHER = new Color(0x8A8478);
    private static final Color GOLD = new Color(0xB7862C);
    private static final Color GOLD_DARK = new Color(0x8C6418);
    private static final Color SEAL_TOP = new Color(0xFFF7DE);
    private static final Color SEAL_BOTTOM = new Color(0xF0DA9C);
    private static final Color INK = new Color(0x2F2A22);
    private static final Color MUTED = new Color(0x75695A);
    private static final Color BUD = new Color(0x66963F);
    private static final Color SHADOW = new Color(40, 30, 10, 45);

    private final PortraitCache portraits;
    private final Fonts fonts = new Fonts();

    public TreeRenderer(PortraitCache portraits) {
        this.portraits = portraits;
    }

    public static void applyQualityHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
    }

    /** Plain background, painted in screen coordinates before the chart. */
    public void paintBackground(Graphics2D g, double width, double height) {
        g.setColor(BACKGROUND);
        g.fill(new Rectangle2D.Double(0, 0, width, height));
    }

    /** Paints the chart in world coordinates (the caller sets up the transform). */
    public void paint(Graphics2D g, TreeLayout layout, Options options, BranchNode highlighted) {
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(LINK);
        for (Path2D link : layout.links()) {
            g.draw(link);
        }
        for (Couple c : layout.couples()) {
            paintCoupleConnector(g, c);
        }
        boolean budBelow = layout.mode() == TreeMode.DESCENDANTS;
        for (BranchNode n : layout.nodes()) {
            paintLabel(g, n, n == highlighted, options.portraits(), budBelow);
        }
        for (Couple c : layout.couples()) {
            if (c.hasSeal()) {
                paintMarriageSeal(g, c);
            }
        }
    }

    /** Renders the whole chart into an image, reducing the scale if the image would become too large. */
    public BufferedImage renderImage(TreeLayout layout, double scale, Options options) {
        Rectangle2D b = layout.bounds();
        double s = Math.min(scale, Math.sqrt(50_000_000 / (b.getWidth() * b.getHeight())));
        s = Math.min(s, 20_000 / Math.max(b.getWidth(), b.getHeight()));
        int w = Math.max(1, (int) Math.ceil(b.getWidth() * s));
        int h = Math.max(1, (int) Math.ceil(b.getHeight() * s));
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            applyQualityHints(g);
            paintBackground(g, w, h);
            g.scale(s, s);
            g.translate(-b.getX(), -b.getY());
            paint(g, layout, options, null);
        } finally {
            g.dispose();
        }
        return image;
    }

    // ------------------------------------------------------------------ boxes

    private void paintLabel(Graphics2D g, BranchNode node, boolean highlighted, boolean showPortraits,
                            boolean budBelow) {
        Person person = node.person();
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            double scale = LabelGeometry.scale(node);
            g2.translate(node.x(), node.y());
            g2.scale(scale, scale);
            double w = node.width() / scale;
            double h = node.height() / scale;
            double top = -h / 2;
            RoundRectangle2D box = new RoundRectangle2D.Double(-w / 2, top, w, h, 22, 22);
            Color accent = colorFor(person.sex());
            boolean root = node.isRoot();

            g2.setColor(SHADOW);
            g2.fill(new RoundRectangle2D.Double(-w / 2 + 1.5, top + 3, w, h, 22, 22));
            g2.setPaint(new GradientPaint(0, (float) top, root ? ROOT_TOP : LABEL_TOP, 0, (float) (top + h),
                    root ? ROOT_BOTTOM : LABEL_BOTTOM));
            g2.fill(box);
            if (highlighted) {
                g2.setColor(alpha(GOLD, 90));
                g2.setStroke(new BasicStroke(6f));
                g2.draw(box);
            }
            g2.setColor(root ? GOLD : accent);
            g2.setStroke(new BasicStroke(root || highlighted ? 2.4f : 1.4f));
            g2.draw(box);

            boolean slot = LabelGeometry.hasPortraitSlot(node, showPortraits);
            Color frame = root ? GOLD : accent;
            if (node.isNarrow()) {
                paintNarrowContent(g2, person, w, top, slot, frame);
                if (node.isTruncated()) {
                    paintBud(g2, budBelow ? top + h + 13 : top - (slot ? LabelGeometry.PORTRAIT_OUT : 0));
                }
                return;
            }
            double textX = 0;
            double textMax = w - 20;
            boolean centered = true;
            if (slot) {
                // an upright oval, a little taller than the box, sticking out above and below it
                double px = -w / 2 + LabelGeometry.PORTRAIT_INSET;
                double py = top - LabelGeometry.PORTRAIT_OUT;
                paintPortrait(g2, person, new Ellipse2D.Double(px, py, LabelGeometry.PORTRAIT_WIDTH,
                        LabelGeometry.PORTRAIT_HEIGHT), frame);
                textX = px + LabelGeometry.PORTRAIT_WIDTH + 9;
                textMax = w / 2 - 9 - textX;
                centered = false;
            }
            text(g2, person.givenNames(), fonts.given, MUTED, textX, top + 18, textMax, centered);
            text(g2, person.surname().isEmpty() && person.givenNames().isEmpty() ? person.fullName() : person.surname(),
                    fonts.surname, INK, textX, top + 35, textMax, centered);
            text(g2, person.lifeSpan(), fonts.years, MUTED, textX, top + 50, textMax, centered);

            if (node.isTruncated()) {
                double out = slot ? LabelGeometry.PORTRAIT_OUT : 0;
                paintBud(g2, budBelow ? top + h + out + 13 : top - out);
            }
        } finally {
            g2.dispose();
        }
    }

    /**
     * Content of a narrow upright box: the portrait on top (sticking out above), then the given names in
     * up to two lines, the surname (shrunk to fit) and the years, all centred.
     */
    private void paintNarrowContent(Graphics2D g, Person person, double w, double top, boolean slot, Color frame) {
        double textTop = top;
        if (slot) {
            paintPortrait(g, person, new Ellipse2D.Double(-LabelGeometry.PORTRAIT_WIDTH / 2,
                    top - LabelGeometry.PORTRAIT_OUT, LabelGeometry.PORTRAIT_WIDTH, LabelGeometry.PORTRAIT_HEIGHT),
                    frame);
            textTop = top + LabelGeometry.NARROW_PORTRAIT;
        }
        double max = w - 12;
        List<String> given = wrap(g, person.givenNames(), fonts.given, max, 2);
        if (given.size() == 1) {
            text(g, given.get(0), fonts.given, MUTED, 0, textTop + 20, max, true);
        } else {
            for (int i = 0; i < given.size(); i++) {
                text(g, given.get(i), fonts.given, MUTED, 0, textTop + 14 + 13 * i, max, true);
            }
        }
        String surname = person.surname().isEmpty() && person.givenNames().isEmpty()
                ? person.fullName() : person.surname();
        text(g, surname, shrunk(g, fonts.surname.deriveFont(14f), surname, max, 10.5f), INK, 0, textTop + 46, max,
                true);
        text(g, person.lifeSpan(), fonts.years, MUTED, 0, textTop + 61, max, true);
    }

    /** Words distributed over at most {@code lines} lines; the last line takes whatever is left. */
    private List<String> wrap(Graphics2D g, String s, Font font, double max, int lines) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isBlank()) {
            return out;
        }
        Font f = fonts.forText(font, s);
        StringBuilder line = new StringBuilder();
        for (String word : s.trim().split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (line.isEmpty() || out.size() == lines - 1
                    || f.getStringBounds(candidate, g.getFontRenderContext()).getWidth() <= max) {
                line = new StringBuilder(candidate);
            } else {
                out.add(line.toString());
                line = new StringBuilder(word);
            }
        }
        out.add(line.toString());
        return out;
    }

    /** {@code font}, made smaller (down to {@code minSize}) until {@code s} fits into {@code max}. */
    private Font shrunk(Graphics2D g, Font font, String s, double max, float minSize) {
        Font f = fonts.forText(font, s == null ? "" : s);
        while (s != null && f.getSize2D() > minSize
                && f.getStringBounds(s, g.getFontRenderContext()).getWidth() > max) {
            f = f.deriveFont(f.getSize2D() - 0.5f);
        }
        return f;
    }

    /** The portrait in an oval cameo frame (cut to fill the oval, biased towards the face at the top). */
    private void paintPortrait(Graphics2D g, Person person, Ellipse2D oval, Color accent) {
        double frame = 2.5;
        Ellipse2D outer = new Ellipse2D.Double(oval.getX() - frame, oval.getY() - frame,
                oval.getWidth() + 2 * frame, oval.getHeight() + 2 * frame);
        g.setColor(SHADOW);
        g.fill(new Ellipse2D.Double(outer.getX() + 1.5, outer.getY() + 3, outer.getWidth(), outer.getHeight()));
        g.setPaint(new GradientPaint(0, (float) outer.getY(), LABEL_TOP, 0, (float) outer.getMaxY(), LABEL_BOTTOM));
        g.fill(outer);
        g.setColor(alpha(accent, 110));
        g.setStroke(new BasicStroke(0.9f));
        g.draw(outer);

        BufferedImage image = portraits == null ? null : portraits.get(person.portrait());
        if (image != null) {
            Shape clip = g.getClip();
            g.clip(oval);
            double dw = oval.getWidth();
            double dh = oval.getHeight();
            double s = Math.max(dw / image.getWidth(), dh / image.getHeight());
            AffineTransform at = new AffineTransform();
            at.translate(oval.getX() + (dw - image.getWidth() * s) / 2,
                    oval.getY() + (dh - image.getHeight() * s) * 0.3);
            at.scale(s, s);
            g.drawImage(image, at, null);
            g.setClip(clip);
        } else {
            g.setColor(alpha(accent, 40));
            g.fill(oval);
            String initials = initials(person);
            Font f = fonts.forText(fonts.initials, initials);
            g.setFont(f);
            Rectangle2D r = f.getStringBounds(initials, g.getFontRenderContext());
            g.setColor(accent);
            g.drawString(initials, (float) (oval.getCenterX() - r.getWidth() / 2),
                    (float) (oval.getCenterY() + r.getHeight() * 0.32));
        }
        g.setColor(accent);
        g.setStroke(new BasicStroke(1.4f));
        g.draw(oval);
    }

    /** A little green bud with a plus: more relatives exist beyond the generation limit. */
    private static void paintBud(Graphics2D g, double bottom) {
        Ellipse2D bud = new Ellipse2D.Double(-6.5, bottom - 13, 13, 13);
        g.setColor(BUD);
        g.fill(bud);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(-3, bottom - 6.5, 3, bottom - 6.5));
        g.draw(new Line2D.Double(0, bottom - 9.5, 0, bottom - 3.5));
    }

    // ------------------------------------------------------------------ couples

    /** The line joining two partners, from the edge of one box to the edge of the other (none if they touch). */
    private static void paintCoupleConnector(Graphics2D g, Couple c) {
        if (c.bracket() != null) {
            g.setColor(c.hasSeal() ? GOLD : LINK);
            g.setStroke(new BasicStroke(c.hasSeal() ? 1.6f : 1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(c.bracket());
            return;
        }
        double from = c.first().x() + c.first().width() / 2;
        double to = c.second().x() - c.second().width() / 2;
        if (to - from < 0.5) {
            return;
        }
        g.setColor(c.hasSeal() ? GOLD : LINK);
        g.setStroke(new BasicStroke(c.hasSeal() ? 1.6f : 1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(from, c.y(), to, c.y()));
    }

    /** The marriage as a small gold seal on the connector between the partners (or on the seam of glued ones). */
    private void paintMarriageSeal(Graphics2D g, Couple couple) {
        Family family = couple.family();
        String label = couple.label();
        Font font = fonts.forText(fonts.seal, label.isEmpty() ? "0" : label);
        double textWidth = label.isEmpty() ? 0 : font.getStringBounds(label, g.getFontRenderContext()).getWidth();
        double h = LabelGeometry.BADGE_HEIGHT;
        double w = textWidth > 0 ? 27 + textWidth + 8 : 26;
        double x = couple.x();
        double y = couple.y() - h / 2;
        RoundRectangle2D pill = new RoundRectangle2D.Double(x - w / 2, y, w, h, h, h);
        g.setColor(new Color(40, 30, 10, 50));
        g.fill(new RoundRectangle2D.Double(x - w / 2 + 1, y + 1.5, w, h, h, h));
        g.setPaint(new GradientPaint(0, (float) y, SEAL_TOP, 0, (float) (y + h), SEAL_BOTTOM));
        g.fill(pill);
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(pill);
        double cy = y + h / 2;
        paintRings(g, textWidth > 0 ? x - w / 2 + 13 : x, cy, 4.2, family.isDivorced(), GOLD_DARK);
        if (textWidth > 0) {
            g.setFont(font);
            g.setColor(INK);
            g.drawString(label, (float) (x - w / 2 + 23), (float) (cy + 3.6));
        }
    }

    /** Two interlocking wedding rings (drawn apart and crossed out for a divorce). */
    private static void paintRings(Graphics2D g, double cx, double cy, double r, boolean divorced, Color color) {
        double offset = divorced ? r * 1.15 : r * 0.62;
        g.setColor(color);
        g.setStroke(new BasicStroke((float) (r * 0.34)));
        g.draw(new Ellipse2D.Double(cx - offset - r, cy - r, 2 * r, 2 * r));
        g.draw(new Ellipse2D.Double(cx + offset - r, cy - r, 2 * r, 2 * r));
        if (divorced) {
            g.setStroke(new BasicStroke((float) (r * 0.3), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(cx - r * 0.45, cy + r * 1.2, cx + r * 0.45, cy - r * 1.2));
        }
    }

    /** See {@link Couple#note(Family, boolean)}. */
    static String marriageNote(Family family, boolean full) {
        return Couple.note(family, full);
    }

    // ------------------------------------------------------------------ helpers

    private void text(Graphics2D g, String s, Font font, Color color, double x, double baseline, double maxWidth,
                      boolean centered) {
        if (s == null || s.isBlank()) {
            return;
        }
        Font f = fonts.forText(font, s);
        String t = s;
        double width = f.getStringBounds(t, g.getFontRenderContext()).getWidth();
        if (width > maxWidth) {
            while (t.length() > 1 && width > maxWidth) {
                t = t.substring(0, t.length() - 1);
                width = f.getStringBounds(t.stripTrailing() + "…", g.getFontRenderContext()).getWidth();
            }
            t = t.stripTrailing() + "…";
        }
        g.setFont(f);
        g.setColor(color);
        g.drawString(t, (float) (centered ? x - width / 2 : x), (float) baseline);
    }

    private static String initials(Person p) {
        StringBuilder sb = new StringBuilder();
        if (!p.givenNames().isEmpty()) {
            sb.appendCodePoint(p.givenNames().codePointAt(0));
        }
        if (!p.surname().isEmpty() && Character.isLetter(p.surname().codePointAt(0))) {
            sb.appendCodePoint(p.surname().codePointAt(0));
        }
        return sb.isEmpty() ? "?" : sb.toString().toUpperCase();
    }

    private static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    static Color colorFor(Sex sex) {
        return switch (sex) {
            case MALE -> MALE;
            case FEMALE -> FEMALE;
            default -> OTHER;
        };
    }
}
