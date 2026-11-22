package com.bw.jAncestryChart.render;

import com.bw.jAncestryChart.model.MediaReference;
import com.bw.jAncestryChart.TestData;
import com.bw.jAncestryChart.layout.BranchNode;
import com.bw.jAncestryChart.layout.GenerationLayout;
import com.bw.jAncestryChart.layout.TreeBuilder;
import com.bw.jAncestryChart.layout.TreeLayout;
import com.bw.jAncestryChart.layout.TreeMode;
import com.bw.jAncestryChart.model.Family;
import com.bw.jAncestryChart.model.FamilyTree;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderTest {

    @Test
    void rendersAndExportsPng(@TempDir Path dir) throws IOException {
        FamilyTree tree = TestData.sampleTree();
        for (TreeMode mode : TreeMode.values()) {
            String rootId = mode == TreeMode.ANCESTORS ? "I5" : "I2";
            BranchNode root = new TreeBuilder().build(tree.person(rootId).orElseThrow(), mode, 8);
            TreeLayout layout = new GenerationLayout(GenerationLayout.Settings.defaults().withCompactFrom(2))
                    .layout(root, mode);
            Path out = dir.resolve(mode + ".png");
            BufferedImage image = TreeExporter.writePng(layout, new PortraitCache(), 0.5, true, out);

            assertTrue(Files.size(out) > 1000);
            assertEquals((int) Math.ceil(layout.bounds().getWidth() * 0.5), image.getWidth());
            Set<Integer> colours = new HashSet<>();
            for (int y = 0; y < image.getHeight(); y += 3) {
                for (int x = 0; x < image.getWidth(); x += 3) {
                    colours.add(image.getRGB(x, y));
                }
            }
            assertTrue(colours.size() > 50, "the picture should contain more than a background");
        }
    }

    @Test
    void marriageTexts() {
        FamilyTree tree = TestData.sampleTree();
        Family married = family(tree, "F1");
        Family divorced = family(tree, "F2");
        assertEquals("12 Jun 1932", TreeRenderer.marriageNote(married, true));
        assertEquals("1932", TreeRenderer.marriageNote(married, false));
        assertEquals("div.", TreeRenderer.marriageNote(divorced, true));
        assertEquals("", TreeRenderer.marriageNote(family(tree, "F3"), true));
        assertEquals("", TreeRenderer.marriageNote(null, true));
    }

    private static Family family(FamilyTree tree, String id) {
        return tree.families().stream().filter(f -> f.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void cropsAndShrinksPortraits() {
        BufferedImage big = new BufferedImage(1000, 600, BufferedImage.TYPE_INT_RGB);
        BufferedImage cropped = PortraitCache.crop(big, new Rectangle(100, 50, 300, -1));
        assertEquals(300, cropped.getWidth());
        assertEquals(550, cropped.getHeight());
        assertSame(big, PortraitCache.crop(big, null));
        BufferedImage small = PortraitCache.shrink(big, 192);
        assertEquals(192, small.getWidth());
        assertEquals(115, small.getHeight());
    }

    @Test
    void unreadableImagesBecomeNull(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("broken.png");
        Files.writeString(file, "not an image");
        PortraitCache cache = new PortraitCache();
        assertEquals(null, cache.get(new MediaReference(file, null, null)));
        assertEquals(null, cache.get(null));
        Path ok = dir.resolve("ok.png");
        ImageIO.write(new BufferedImage(50, 80, BufferedImage.TYPE_INT_RGB), "png", ok.toFile());
        assertEquals(80, cache.get(new MediaReference(ok, null, null)).getHeight());
    }
}
