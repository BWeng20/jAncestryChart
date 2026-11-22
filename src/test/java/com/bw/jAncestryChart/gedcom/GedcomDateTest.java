package com.bw.jAncestryChart.gedcom;

import com.bw.jAncestryChart.gedcom.GedcomDate.Qualifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GedcomDateTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "16 SEP 2011          | EXACT      | 16 Sep 2011                 | 2011",
        "JUN 1842             | EXACT      | Jun 1842                    | 1842",
        "1864                 | EXACT      | 1864                        | 1864",
        "ABT 1806             | ABOUT      | about 1806                  | ~1806",
        "CAL 1806             | CALCULATED | calculated 1806             | ~1806",
        "EST 1806             | ESTIMATED  | estimated 1806              | ~1806",
        "BEF 1915             | BEFORE     | before 1915                 | <1915",
        "AFT 3 JAN 1820       | AFTER      | after 3 Jan 1820            | >1820",
        "BET 1820 AND 1830    | BETWEEN    | between 1820 and 1830       | ~1825",
        "FROM 1901 TO 1927    | FROM_TO    | from 1901 to 1927           | 1901",
        "FROM 1901            | FROM       | from 1901                   | 1901",
        "TO 1927              | TO         | to 1927                     | 1927",
        "44 BCE               | EXACT      | 44 BCE                      | 44 BCE",
        "JULIAN 3 MAR 1700    | EXACT      | 3 Mar 1700 (Julian)         | 1700",
        "@#DJULIAN@ 1700      | EXACT      | 1700 (Julian)               | 1700",
        "FRENCH_R 1 VEND 3    | EXACT      | 1 VEND 3 (French Republican)| 3",
        "abt 1806             | ABOUT      | about 1806                  | ~1806",
    })
    void parsesGrammar(String text, Qualifier qualifier, String display, String yearLabel) {
        GedcomDate date = GedcomDate.parse(text);
        assertEquals(qualifier, date.qualifier());
        assertEquals(display, date.toDisplayString());
        assertEquals(yearLabel, date.yearLabel());
        assertTrue(date.isParsed());
    }

    @Test
    void keepsUnparseableTextAndFindsAYear() {
        GedcomDate date = GedcomDate.parse("sometime around 1850, maybe");
        assertFalse(date.isParsed());
        assertEquals("sometime around 1850, maybe", date.toDisplayString());
        assertEquals(1850, date.year().getAsInt());
        assertEquals("~1850", date.yearLabel());
        assertEquals("?", GedcomDate.parse("unknown").yearLabel());
    }

    @Test
    void rejectsInvalidPieces() {
        assertFalse(GedcomDate.parse("32 FOO 1900").isParsed());
        assertFalse(GedcomDate.parse("BET 1900").isParsed());
        assertFalse(GedcomDate.parse("_MYCAL 1900").isParsed());
    }

    @Test
    void keepsLegacyPhrase() {
        GedcomDate date = GedcomDate.parse("INT 1900 (about the turn of the century)");
        assertEquals(Qualifier.EXACT, date.qualifier());
        assertEquals("about the turn of the century", date.phrase());
        assertEquals("1900 (about the turn of the century)", date.toDisplayString());
    }

    @Test
    void blankIsNull() {
        assertNull(GedcomDate.parse(null));
        assertNull(GedcomDate.parse("  "));
    }

    @Test
    void sortsChronologically() {
        List<GedcomDate> dates = new ArrayList<>(List.of(GedcomDate.parse("1900"), GedcomDate.parse("3 MAR 1850"),
                GedcomDate.parse("MAR 1850"), GedcomDate.parse("44 BCE"), GedcomDate.parse("garbage")));
        dates.sort(null);
        assertEquals(List.of("44 BCE", "MAR 1850", "3 MAR 1850", "1900", "garbage"),
                dates.stream().map(GedcomDate::raw).toList());
    }
}
