package com.bw.jAncestryChart.layout;

import com.bw.jAncestryChart.TestData;
import com.bw.jAncestryChart.model.FamilyTree;
import com.bw.jAncestryChart.model.Person;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.geom.Rectangle2D;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayoutTest {

    private static final GenerationLayout.Settings SETTINGS = GenerationLayout.Settings.defaults();

    @Test
    void buildsAncestorTreeWithGenerationLimit() {
        FamilyTree tree = TestData.fullPedigree(5);
        Person root = tree.person("I1").orElseThrow();
        BranchNode full = new TreeBuilder().build(root, TreeMode.ANCESTORS, 10);
        assertEquals(63, full.size());

        BranchNode limited = new TreeBuilder().build(root, TreeMode.ANCESTORS, 2);
        assertEquals(7, limited.size());
        BranchNode grandfather = limited.outer().get(0).outer().get(0);
        assertEquals("I4", grandfather.person().id());
        assertTrue(grandfather.isTruncated());
        assertFalse(limited.isTruncated());
    }

    @Test
    void buildsDescendantTreeWithPartnerBoxes() {
        FamilyTree tree = TestData.sampleTree();
        BranchNode karl = new TreeBuilder().build(tree.person("I2").orElseThrow(), TreeMode.DESCENDANTS, 5);
        assertEquals(List.of("I3"), ids(karl.partners()));
        assertEquals(List.of("I1"), ids(karl.outer()));
        BranchNode anna = karl.outer().get(0);
        assertEquals("F1", anna.parentFamily().id());
        assertEquals(List.of("I4"), ids(anna.partners()));
        BranchNode paul = anna.partners().get(0);
        assertTrue(paul.isPartner());
        assertSame(anna, paul.inner());
        assertEquals("F2", paul.partnerFamily().id());
        assertEquals(1, paul.generation());
        assertEquals("I5", anna.outer().get(0).person().id());
        assertEquals("F2", anna.outer().get(0).parentFamily().id());
    }

    @Test
    void parentsMarriageBelongsToTheChild() {
        FamilyTree tree = TestData.sampleTree();
        BranchNode eva = new TreeBuilder().build(tree.person("I5").orElseThrow(), TreeMode.ANCESTORS, 5);
        assertEquals("F2", eva.parentFamily().id());
        assertTrue(eva.parentFamily().isDivorced());
        BranchNode anna = child(eva, "I1");
        assertEquals("F1", anna.parentFamily().id());
        assertEquals("12 Jun 1932, Solingen", anna.parentFamily().marriage().describe());
        assertNull(child(anna, "I2").parentFamily(), "Karl's parents are unknown");

        BranchNode annaAtRoot = new TreeBuilder().build(tree.person("I1").orElseThrow(), TreeMode.ANCESTORS, 3);
        assertEquals(List.of("I4"), ids(annaAtRoot.partners()),
                "the root person shows their own partners even in an ancestor chart");
        assertTrue(child(annaAtRoot, "I2").partners().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 6})
    void boxesNeverOverlap(int generations) {
        TreeLayout layout = layout(TestData.fullPedigree(6), "I1", TreeMode.ANCESTORS, generations, SETTINGS);
        assertEquals(generations, layout.maxGeneration());
        assertNoOverlaps(layout);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.5, 1, 3})
    void boxesNeverOverlapForAnySpacing(double spacing) {
        assertNoOverlaps(layout(TestData.fullPedigree(5), "I1", TreeMode.ANCESTORS, 5,
                SETTINGS.withSpacing(spacing)));
        assertNoOverlaps(layout(TestData.sampleTree(), "I2", TreeMode.DESCENDANTS, 5,
                SETTINGS.withSpacing(spacing)));
    }

    @ParameterizedTest
    @EnumSource(TreeMode.class)
    void everyGenerationIsOneRow(TreeMode mode) {
        String root = mode == TreeMode.ANCESTORS ? "I5" : "I2";
        TreeLayout layout = layout(TestData.sampleTree(), root, mode, 5, SETTINGS);
        assertEquals(0, layout.root().y());
        double previous = Double.NaN;
        for (int g = 0; g <= layout.maxGeneration(); g++) {
            int generation = g;
            List<BranchNode> row = layout.nodes().stream().filter(n -> n.generation() == generation).toList();
            double y = row.get(0).y();
            assertTrue(row.stream().allMatch(n -> n.y() == y), "row " + g);
            if (g > 0) {
                assertTrue(mode == TreeMode.ANCESTORS ? y < previous : y > previous,
                        "ancestors grow upwards, descendants downwards");
            }
            previous = y;
        }
    }

    @ParameterizedTest
    @EnumSource(TreeMode.class)
    void partnersTouchUnlessDivorced(TreeMode mode) {
        String root = mode == TreeMode.ANCESTORS ? "I1" : "I2";
        TreeLayout layout = layout(TestData.sampleTree(), root, mode, 5, SETTINGS);
        int glued = 0;
        int divorced = 0;
        for (Couple c : layout.couples()) {
            assertEquals(c.first().y(), c.second().y(), "married people on the same height");
            if (c.family().isDivorced() || mode == TreeMode.DESCENDANTS) {
                assertEquals(!c.family().isDivorced(), c.glued(), c.family().id());
            }
            double gapFrom = c.first().bounds().getMaxX();
            double gapTo = c.second().bounds().getMinX();
            assertTrue(c.hasSeal());
            Rectangle2D seal = c.sealBounds();
            if (c.glued()) {
                glued++;
                assertEquals(gapFrom + closeGap(c), gapTo, 1e-6, "close together");
                assertEquals(gapFrom + closeGap(c) / 2, c.x(), 1e-6, "the seal sits in the middle");
                assertEquals(Math.min(c.first().bounds().getMaxY(), c.second().bounds().getMaxY()), c.y(), 1e-6,
                        "... at the lower edge");
            } else {
                divorced++;
                assertEquals(c.first().y(), c.y());
                assertTrue(seal.getMinX() >= gapFrom + GenerationLayout.CONNECTOR - 1e-6
                        && seal.getMaxX() <= gapTo - GenerationLayout.CONNECTOR + 1e-6, "the seal fits between them");
            }
            for (BranchNode n : layout.nodes()) {
                if (n != c.first() && n != c.second()) {
                    assertFalse(seal.intersects(n.collisionBounds()), c.family().id() + " covers " + n);
                }
            }
        }
        assertTrue(glued > 0);
        assertTrue(divorced > 0);
    }

    @Test
    void divorcedPartnersKeepTheirDistance() {
        FamilyTree tree = TestData.families("F1 div: I1 + I2 > I3", "F2: I1 + I4 > I5 I6");
        TreeLayout layout = layout(tree, "I1", TreeMode.DESCENDANTS, 2, SETTINGS);
        Couple first = layout.couples().stream().filter(c -> c.family().id().equals("F1")).findFirst().orElseThrow();
        Couple second = layout.couples().stream().filter(c -> c.family().id().equals("F2")).findFirst().orElseThrow();
        assertFalse(first.glued());
        assertTrue(second.glued());
        assertTrue(first.second().bounds().getMinX() - first.first().bounds().getMaxX()
                >= first.sealWidth() + 2 * GenerationLayout.CONNECTOR - 1e-6);
        assertEquals(second.first().bounds().getMaxX() + SETTINGS.coupleGap(), second.second().bounds().getMinX(),
                1e-6);
        assertNoOverlaps(layout);
    }

    @Test
    void ancestorStandsUnderTheParentsMarriage() {
        TreeLayout layout = layout(TestData.fullPedigree(4), "I1", TreeMode.ANCESTORS, 4, SETTINGS);
        assertEquals(1 + 2 + 4 + 8, layout.couples().size(), "one couple per pair of parents");
        for (Couple c : layout.couples()) {
            BranchNode child = c.first().inner();
            assertSame(child, c.second().inner());
            assertEquals(child.x(), c.x(), 1e-6, "right under the parents' marriage");
            assertEquals(List.of(c.first(), c.second()), child.outer(), "father left, mother right");
        }
        assertEquals(15, layout.links().size(), "a line from every couple to its child");
    }

    @Test
    void fullPedigreeIsSymmetricAndCompact() {
        TreeLayout layout = layout(TestData.fullPedigree(3), "I1", TreeMode.ANCESTORS, 3,
                SETTINGS.withPortraits(false));
        BranchNode father = layout.root().outer().get(0);
        BranchNode mother = layout.root().outer().get(1);
        assertEquals(-father.x(), mother.x(), 1e-6);
        List<BranchNode> top = layout.nodes().stream().filter(n -> n.generation() == 3).toList();
        for (int i = 1; i < top.size(); i++) {
            double gap = top.get(i).bounds().getMinX() - top.get(i - 1).bounds().getMaxX();
            boolean couple = i % 2 == 1;
            assertEquals(couple ? SETTINGS.coupleGap() : GenerationLayout.GAP, gap, 1e-6,
                    "outer row packed tightly, gap " + i);
        }
    }

    @Test
    void personSlidesAlongTheParentsConnectorTowardsTheMiddleOfTheirAncestors() {
        // divorced parents stand apart; the father's side is one generation deeper than the mother's
        FamilyTree tree = TestData.families("F1 div: I2 + I3 > I1", "F2: I4 + I5 > I2", "F5: I6 + I7 > I3",
                "F3: I8 + I9 > I4",
                "F4: I10 + I11 > I5");
        TreeLayout layout = layout(tree, "I1", TreeMode.ANCESTORS, 5, SETTINGS);
        BranchNode root = layout.root();
        Couple parents = layout.couples().stream().filter(c -> c.family().id().equals("F1")).findFirst()
                .orElseThrow();
        double gapFrom = parents.first().bounds().getMaxX();
        double gapTo = parents.second().bounds().getMinX();
        double gapMiddle = (gapFrom + gapTo) / 2;
        assertEquals(root.x(), parents.x(), 1e-6, "the seal stays right above the child");
        assertTrue(root.x() < gapMiddle - 1, "moved towards the deeper side");
        assertTrue(parents.sealBounds().getMinX() >= gapFrom + GenerationLayout.CONNECTOR - 1e-6);
        double chartMiddle = layout.nodes().stream().mapToDouble(n -> n.bounds().getCenterX()).average()
                .orElseThrow();
        assertTrue(Math.abs(root.x() - chartMiddle) < Math.abs(gapMiddle - chartMiddle));
        assertNoOverlaps(layout);
    }

    @Test
    void smallFamiliesAreSpreadEvenlyBetweenLargeOnes() {
        // three children: the outer two have four children each, the middle one none
        FamilyTree tree = TestData.families("F1: I1 + I2 > I3 I4 I5", "F2: I3 + - > I6 I7 I8 I9",
                "F3: I5 + - > I10 I11 I12 I13");
        TreeLayout layout = layout(tree, "I1", TreeMode.DESCENDANTS, 3, SETTINGS);
        List<BranchNode> children = layout.root().outer();
        assertEquals(List.of("I3", "I4", "I5"), ids(children));
        assertEquals(children.get(1).x() - children.get(0).x(), children.get(2).x() - children.get(1).x(), 1e-6,
                "the middle child stands halfway, not squeezed to one side");
        assertTrue(children.get(1).x() - children.get(0).x() > LabelGeometry.WIDTH + GenerationLayout.GAP + 1,
                "there is slack to share");
        assertNoOverlaps(layout);
    }

    @ParameterizedTest
    @EnumSource(TreeMode.class)
    void compactGenerationsUseNarrowBoxesAndTouchingCouples(TreeMode mode) {
        FamilyTree tree = mode == TreeMode.ANCESTORS ? TestData.fullPedigree(5) : TestData.sampleTree();
        String root = mode == TreeMode.ANCESTORS ? "I1" : "I2";
        TreeLayout compact = layout(tree, root, mode, 5, SETTINGS.withCompactFrom(1));
        TreeLayout wide = layout(tree, root, mode, 5, SETTINGS.withCompactFrom(99));
        for (BranchNode n : compact.nodes()) {
            assertEquals(n.generation() > 0, n.isNarrow(), n.toString());
            if (n.isNarrow()) {
                assertEquals(LabelGeometry.NARROW_WIDTH, n.width());
            }
        }
        long glued = 0;
        for (Couple c : compact.couples()) {
            if (!c.glued()) {
                continue;       // divorced, or held apart by their parents
            }
            glued++;
            assertEquals(c.first().bounds().getMaxX() + closeGap(c), c.second().bounds().getMinX(), 1e-6,
                    "narrow boxes touch, wide ones keep the couple gap");
            assertEquals(c.first().bounds().getMaxX() + closeGap(c) / 2, c.x(), 1e-6, "the seal sits in the middle");
            assertEquals(Math.min(c.first().bounds().getMaxY(), c.second().bounds().getMaxY()), c.y(), 1e-6,
                        "... at the lower edge");
        }
        assertTrue(glued > 0);
        assertNoOverlaps(compact);
        // the small sample is as wide as its (never compact) root row; the pedigree shrinks a lot
        double factor = mode == TreeMode.ANCESTORS ? 0.75 : 1;
        assertTrue(compact.bounds().getWidth() < wide.bounds().getWidth() * factor,
                compact.bounds().getWidth() + " vs " + wide.bounds().getWidth());
    }

    @Test
    void parentsStandRightAboveTheirChild() {
        TreeLayout layout = layout(TestData.fullPedigree(4), "I1", TreeMode.ANCESTORS, 4,
                SETTINGS.withCompactFrom(2));
        assertEquals(15, layout.links().size());
        assertTrue(layout.couples().stream().anyMatch(Couple::glued), "the outer couples touch");
        assertTrue(layout.couples().stream().anyMatch(c -> !c.glued()), "the inner ones are held apart");
        for (Couple c : layout.couples()) {
            BranchNode child = c.first().inner();
            double[] start = null;
            double[] end = null;
            for (java.awt.geom.Path2D link : layout.links()) {
                double[] first = point(link, true);
                if (Math.abs(first[0] - c.x()) < 1e-6 && Math.abs(first[1] - c.y()) < 1e-6) {
                    start = first;
                    end = point(link, false);
                }
            }
            assertTrue(start != null, "a line starts at the seal of " + c.family().id());
            assertEquals(child.x(), start[0], 1e-6, "... right above the child");
            assertEquals(child.x(), end[0], 1e-6, "... and ends on the child's box");
            assertEquals(child.bounds().getMinY(), end[1], 1e-6);
        }
        assertNoOverlaps(layout);
    }

    @Test
    void withBentLinesMarriedParentsAlwaysTouch() {
        TreeLayout layout = layout(TestData.fullPedigree(4), "I1", TreeMode.ANCESTORS, 4,
                SETTINGS.withCompactFrom(2).withStraightLines(false));
        TreeLayout straight = layout(TestData.fullPedigree(4), "I1", TreeMode.ANCESTORS, 4,
                SETTINGS.withCompactFrom(2));
        assertEquals(15, layout.links().size());
        for (Couple c : layout.couples()) {
            assertTrue(c.glued(), c.family().id());
            assertEquals(c.first().bounds().getMaxX() + closeGap(c) / 2, c.x(), 1e-6, "the seal sits in the middle");
            BranchNode child = c.first().inner();
            double[] end = null;
            for (java.awt.geom.Path2D link : layout.links()) {
                double[] first = point(link, true);
                if (Math.abs(first[0] - c.x()) < 1e-6 && Math.abs(first[1] - c.y()) < 1e-6) {
                    end = point(link, false);
                }
            }
            assertTrue(end != null, "a line starts at the seal of " + c.family().id());
            assertEquals(child.x(), end[0], 1e-6, "... and bends to the child's box");
        }
        assertTrue(layout.root().outer().stream().anyMatch(p -> p.x() != p.outer().get(0).x()
                + (p.outer().get(1).x() - p.outer().get(0).x()) / 2), "some lines bend");
        assertTrue(layout.bounds().getWidth() <= straight.bounds().getWidth() + 1e-6);
        assertNoOverlaps(layout);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 12, 40})
    void wideCouplesKeepTheConfiguredGap(double coupleGap) {
        TreeLayout layout = layout(TestData.fullPedigree(4), "I1", TreeMode.ANCESTORS, 4,
                SETTINGS.withCoupleGap(coupleGap));
        int close = 0;
        for (Couple c : layout.couples()) {
            double gap = c.second().bounds().getMinX() - c.first().bounds().getMaxX();
            if (c.glued()) {
                close++;
                assertEquals(c.first().isNarrow() ? 0 : coupleGap, gap, 1e-6, c.family().id());
            } else {
                assertTrue(gap > coupleGap, "held apart by their parents");
            }
        }
        assertTrue(close > 0);
        assertNoOverlaps(layout);
    }

    @Test
    void aThirdPartnerIsReachedByABracketOverTheSecond() {
        FamilyTree tree = TestData.families("F1: I1 + I2 > I5", "F2 div: I1 + I3 > I6", "F3 div: I1 + I4 > I7");
        TreeLayout layout = layout(tree, "I1", TreeMode.DESCENDANTS, 2, SETTINGS);
        BranchNode root = layout.root();
        Couple first = couple(layout, "F1");
        Couple third = couple(layout, "F3");
        assertNull(first.bracket());
        assertTrue(third.bracket() != null);
        BranchNode inner = first.second();
        BranchNode outer = third.second();
        assertTrue(root.x() < inner.x() && inner.x() < outer.x(), "the third partner stands outside the first");
        double gap = outer.bounds().getMinX() - inner.bounds().getMaxX();
        assertTrue(gap >= GenerationLayout.GAP - 1e-6, "at least the normal distance");
        assertTrue(gap < LabelGeometry.WIDTH, "only as much more as the children need: " + gap);
        Rectangle2D b = third.bracket().getBounds2D();
        assertTrue(b.getMinX() > root.bounds().getMinX() && b.getMinX() < root.bounds().getMaxX(),
                "starts on the person's box");
        assertEquals(outer.x(), b.getMaxX(), 1e-6, "ends on the partner's box");
        assertTrue(third.sealBounds().getMaxY() < inner.collisionBounds().getMinY(), "the seal is above the row");
        assertTrue(third.x() > inner.bounds().getMaxX() && third.x() < outer.bounds().getMinX(),
                "... above the gap, where the line to the children goes down");
        BranchNode child = root.outer().stream().filter(c -> c.person().id().equals("I7")).findFirst().orElseThrow();
        assertEquals(third.x(), child.x(), 1e-6);
        assertNoOverlaps(layout);
    }

    private static Couple couple(TreeLayout layout, String family) {
        return layout.couples().stream().filter(c -> c.family().id().equals(family)).findFirst().orElseThrow();
    }

    /** Gap between married partners standing close together under the test settings. */
    private static double closeGap(Couple c) {
        return c.first().isNarrow() ? 0 : SETTINGS.coupleGap();
    }

    /** First or last point of a path. */
    private static double[] point(java.awt.geom.Path2D path, boolean first) {
        double[] result = null;
        double[] c = new double[6];
        for (java.awt.geom.PathIterator it = path.getPathIterator(null); !it.isDone(); it.next()) {
            it.currentSegment(c);
            if (result == null || !first) {
                result = new double[] {c[0], c[1]};
            }
        }
        return result;
    }

    @Test
    void marriageSealsCanBeSwitchedOff() {
        TreeLayout layout = layout(TestData.fullPedigree(3), "I1", TreeMode.ANCESTORS, 3,
                SETTINGS.withMarriages(false));
        assertFalse(layout.showsMarriages());
        assertEquals(7, layout.couples().size(), "couples are still side by side");
        assertTrue(layout.couples().stream().noneMatch(Couple::hasSeal));
        assertNoOverlaps(layout);
    }

    @Test
    void descendantsHangBelowTheirParentsCouple() {
        TreeLayout layout = layout(TestData.sampleTree(), "I2", TreeMode.DESCENDANTS, 5, SETTINGS);
        BranchNode karl = layout.root();
        Couple karlAndLina = layout.couples().stream()
                .filter(c -> c.family().id().equals("F1")).findFirst().orElseThrow();
        BranchNode anna = karl.outer().get(0);
        assertEquals(karlAndLina.x(), anna.x(), 1e-6, "an only child stands under the parents' seal");
        assertTrue(anna.y() > karl.y());
        assertEquals(anna.y(), anna.partners().get(0).y());
    }

    @Test
    void findsNodeUnderPoint() {
        TreeLayout layout = layout(TestData.fullPedigree(2), "I1", TreeMode.ANCESTORS, 2, SETTINGS);
        BranchNode mother = layout.root().outer().get(1);
        assertEquals(mother, layout.nodeAt(mother.x(), mother.y()));
        assertNull(layout.nodeAt(layout.bounds().getMaxX() + 10, 0));
    }

    @Test
    void spacingMovesGenerationsApart() {
        FamilyTree tree = TestData.fullPedigree(4);
        TreeLayout tight = layout(tree, "I1", TreeMode.ANCESTORS, 4, SETTINGS);
        TreeLayout wide = layout(tree, "I1", TreeMode.ANCESTORS, 4, SETTINGS.withSpacing(2));
        assertTrue(wide.bounds().getHeight() > tight.bounds().getHeight() * 1.2);
        assertTrue(wide.bounds().getWidth() > tight.bounds().getWidth());
    }

    @Test
    void settingsAreClamped() {
        assertEquals(GenerationLayout.Settings.MIN_SPACING, SETTINGS.withSpacing(0).spacing());
        assertEquals(GenerationLayout.Settings.MAX_SPACING, SETTINGS.withSpacing(99).spacing());
        assertEquals(1, SETTINGS.withCompactFrom(-3).compactFrom());
    }

    private static List<String> ids(List<BranchNode> nodes) {
        return nodes.stream().map(n -> n.person().id()).toList();
    }

    private static BranchNode child(BranchNode node, String id) {
        return node.outer().stream().filter(n -> n.person().id().equals(id)).findFirst().orElseThrow();
    }

    private static TreeLayout layout(FamilyTree tree, String rootId, TreeMode mode, int generations,
                                     GenerationLayout.Settings settings) {
        BranchNode root = new TreeBuilder().build(tree.person(rootId).orElseThrow(), mode, generations);
        return new GenerationLayout(settings).layout(root, mode);
    }

    private static void assertNoOverlaps(TreeLayout layout) {
        List<BranchNode> nodes = layout.nodes();
        for (int i = 0; i < nodes.size(); i++) {
            assertTrue(layout.bounds().contains(nodes.get(i).collisionBounds()), nodes.get(i) + " outside bounds");
            for (int j = i + 1; j < nodes.size(); j++) {
                Rectangle2D a = nodes.get(i).collisionBounds();
                Rectangle2D b = nodes.get(j).collisionBounds();
                Rectangle2D shrunk = new Rectangle2D.Double(a.getX() + 0.5, a.getY() + 0.5, a.getWidth() - 1,
                        a.getHeight() - 1);
                assertFalse(shrunk.intersects(b), nodes.get(i) + " overlaps " + nodes.get(j));
            }
        }
    }
}
