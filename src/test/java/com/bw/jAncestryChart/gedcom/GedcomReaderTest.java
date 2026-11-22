package com.bw.jAncestryChart.gedcom;

import com.bw.jAncestryChart.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GedcomReaderTest {

    private final GedcomReader reader = new GedcomReader();

    @Test
    void readsRecordsAndHeader() {
        GedcomDocument doc = reader.parse(TestData.sampleText());
        assertEquals("7.0", doc.version());
        assertTrue(doc.isGedcom7());
        assertEquals("Test Fixture", doc.sourceProduct());
        assertEquals(7, doc.records("INDI").size());
        assertEquals(4, doc.records("FAM").size());
        assertEquals("TRLR", doc.records().get(doc.records().size() - 1).tag());
        assertTrue(doc.warnings().isEmpty(), doc.warnings().toString());
    }

    @Test
    void resolvesPointersAndNestedValues() {
        GedcomDocument doc = reader.parse(TestData.sampleText());
        GedcomNode anna = doc.record("@I1@");
        assertEquals(anna, doc.record("I1"));
        assertEquals("Petra Maria /Becker/", anna.childValue("NAME"));
        assertEquals("Solingen", anna.find("BIRT", "PLAC").value());
        GedcomNode famc = anna.child("FAMC");
        assertTrue(famc.isPointer());
        assertEquals("F1", famc.pointer());
        assertNull(famc.value());
    }

    @Test
    void mergesContinuationsAndUnescapesAt() {
        GedcomNode lina = reader.parse(TestData.sampleText()).record("I3");
        assertEquals("First line\nsecond line\n@home", lina.childValue("NOTE"));
        assertTrue(lina.child("NOTE").children().isEmpty(), "CONT lines must not become children");
    }

    @Test
    void recognisesVoidPointer() {
        GedcomNode husband = reader.parse(TestData.sampleText()).record("F4").child("HUSB");
        assertTrue(husband.isVoidPointer());
        assertTrue(husband.isPointer());
        assertNull(husband.pointer());
    }

    @Test
    void acceptsBomAndAllLineEndings(@TempDir Path dir) throws IOException {
        String text = "﻿0 HEAD\r1 GEDC\r\n2 VERS 7.0\n0 @I1@ INDI\r\n1 NAME Ä /Ö/\r0 TRLR";
        Path file = dir.resolve("mixed.ged");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        GedcomDocument doc = reader.read(file);
        assertEquals("7.0", doc.version());
        assertEquals("Ä /Ö/", doc.record("I1").childValue("NAME"));
        assertEquals(file, doc.source());
    }

    @Test
    void fallsBackToWindows1252WithWarning() {
        byte[] bytes = "0 HEAD\n1 GEDC\n2 VERS 5.5.1\n0 @I1@ INDI\n1 NAME Jürgen /Müller/\n0 TRLR\n"
                .getBytes(Charset.forName("windows-1252"));
        List<String> warnings = new ArrayList<>();
        String text = GedcomReader.decode(bytes, warnings);
        assertTrue(text.contains("Jürgen"));
        assertEquals(1, warnings.size());
    }

    @Test
    void warnsAboutOldVersionsButStillReadsThem() {
        GedcomDocument doc = reader.parse("0 HEAD\n1 GEDC\n2 VERS 5.5.1\n0 @I1@ INDI\n1 NAME A /B/\n"
                + "1 NOTE abc\n2 CONC def\n0 TRLR\n");
        assertFalse(doc.isGedcom7());
        assertTrue(doc.warnings().get(0).contains("5.5.1"));
        assertEquals("abcdef", doc.record("I1").childValue("NOTE"));
    }

    @Test
    void toleratesBrokenLines() {
        GedcomDocument doc = reader.parse("0 HEAD\nthis is not gedcom\n0 @I1@ INDI\n3 NAME Jumped /Level/\n0 TRLR\n");
        assertEquals("Jumped /Level/", doc.record("I1").childValue("NAME"));
        assertEquals(2, doc.warnings().stream().filter(w -> w.startsWith("Line")).count());
    }

    @Test
    void rejectsEmptyInput() {
        assertThrows(GedcomException.class, () -> reader.parse("hello\nworld\n"));
    }
}
