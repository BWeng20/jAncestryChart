package com.bw.jAncestryChart.model;

import com.bw.jAncestryChart.TestData;
import com.bw.jAncestryChart.gedcom.GedcomReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GedcomModelBuilderTest {

    private final FamilyTree tree = TestData.sampleTree();

    private Person person(String id) {
        return tree.person(id).orElseThrow();
    }

    @Test
    void readsNamesAndSex() {
        assertEquals("Petra Maria", person("I1").givenNames());
        assertEquals("Becker", person("I1").surname());
        assertEquals("Paul Schmidt Jr.", person("I4").fullName());
        assertEquals(Sex.FEMALE, person("I1").sex());
        assertEquals(Sex.INTERSEX, person("I6").sex());
        assertEquals("(unnamed)", person("I6").fullName());
    }

    @Test
    void readsEventsWithFallbacks() {
        Person anna = person("I1");
        assertEquals("7 Sep 1945, Solingen", anna.birth().describe());
        assertFalse(anna.isDeceased());
        assertEquals("* 1945", anna.lifeSpan());

        Person lina = person("I3");
        assertEquals("CHR", lina.birth().tag(), "christening stands in for a missing birth");
        assertTrue(lina.isDeceased(), "DEAT Y marks a death without date");
        assertEquals("1908 – †", lina.lifeSpan());

        Person karl = person("I2");
        assertEquals("~1905 – <1980", karl.lifeSpan());
        assertEquals(List.of("Baker"), karl.occupations());
    }

    @Test
    void linksFamilies() {
        Person anna = person("I1");
        assertSame(person("I2"), anna.father());
        assertSame(person("I3"), anna.mother());
        assertEquals(List.of(person("I5")), anna.children());
        assertEquals(List.of(person("I4")), anna.partners());

        Person paul = person("I4");
        assertEquals(List.of(person("I1"), person("I7")), paul.partners());
        assertEquals(List.of(person("I5"), person("I6")), paul.children());
        assertTrue(paul.familiesAsSpouse().get(0).isDivorced());
        assertEquals("12 Jun 1932, Solingen", anna.familiesAsChild().get(0).marriage().describe());
    }

    @Test
    void reportsDanglingPointers() {
        assertTrue(tree.warnings().stream().anyMatch(w -> w.contains("@I99@")), tree.warnings().toString());
        assertNull(tree.families().stream().filter(f -> f.id().equals("F4")).findFirst().orElseThrow().husband());
    }

    @Test
    void searchesIgnoringCaseAndAccents() {
        assertEquals(List.of(person("I3")), tree.search("hoffmann LINA"));
        assertEquals(List.of(person("I3")), tree.search("Lína"));
        assertEquals(person("I5"), tree.findPerson("@I5@").orElseThrow());
        assertEquals(person("I7"), tree.findPerson("greta").orElseThrow());
    }

    @Test
    void suggestsPersonWithMostAncestors() {
        assertEquals(person("I5"), tree.suggestRoot());
        assertEquals(2, FamilyTree.countAncestors(person("I1")));
        assertEquals(4, FamilyTree.countAncestors(person("I5")));
    }

    @Test
    void picksPrimaryPortraitAndCrop(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("photos"));
        ImageIO.write(new BufferedImage(12, 14, BufferedImage.TYPE_INT_RGB), "png", dir.resolve("photos/karl.png").toFile());
        ImageIO.write(new BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB), "png", dir.resolve("photos/document.png").toFile());
        Path ged = dir.resolve("family.ged");
        Files.writeString(ged, TestData.sampleText(), StandardCharsets.UTF_8);

        FamilyTree loaded = new GedcomModelBuilder().build(new GedcomReader().read(ged));
        MediaReference portrait = loaded.person("I2").orElseThrow().portrait();
        assertNotNull(portrait);
        assertEquals(dir.resolve("photos/karl.png"), portrait.file());
        assertEquals(new Rectangle(1, 2, 8, 10), portrait.crop());
        assertTrue(loaded.warnings().stream().noneMatch(w -> w.contains("Portrait")), loaded.warnings().toString());
    }

    @Test
    void reportsMissingPortraitFiles() {
        assertNull(person("I2").portrait(), "the sample has no files next to it when parsed from text");
        assertTrue(tree.warnings().stream().anyMatch(w -> w.contains("Portrait of Karl Becker")));
    }

    @Test
    void resolvesMediaPathVariants(@TempDir Path dir) throws IOException {
        Path photo = Files.createDirectories(dir.resolve("my photos")).resolve("a b.png");
        Files.writeString(photo, "x");
        assertEquals(photo, MediaPaths.resolve(photo.toUri().toString(), null).orElseThrow());
        assertEquals(photo, MediaPaths.resolve("file://" + photo, null).orElseThrow());
        assertEquals(photo, MediaPaths.resolve("my photos/a b.png", dir).orElseThrow());
        assertEquals(photo, MediaPaths.resolve("my%20photos/a%20b.png", dir).orElseThrow());
        assertEquals(photo, MediaPaths.resolve("/some/other/computer/a b.png", dir).orElseThrow(),
                "falls back to the file name next to the GEDCOM file");
        assertTrue(MediaPaths.resolve("https://example.org/a.png", dir).isEmpty());
        assertEquals("a+b c", MediaPaths.percentDecode("a+b%20c"));
    }
}
