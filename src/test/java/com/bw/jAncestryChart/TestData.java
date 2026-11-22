package com.bw.jAncestryChart;

import com.bw.jAncestryChart.gedcom.GedcomReader;
import com.bw.jAncestryChart.model.FamilyTree;
import com.bw.jAncestryChart.model.GedcomModelBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Shared fixtures for the tests. */
public final class TestData {

    private TestData() {
    }

    public static String sampleText() {
        try (InputStream in = TestData.class.getResourceAsStream("/sample.ged")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static FamilyTree sampleTree() {
        return new GedcomModelBuilder().build(new GedcomReader().parse(sampleText()));
    }

    /**
     * A tree from families written as {@code "F1: I2 + I3 > I4 I5"}: husband + wife &gt; children, with
     * {@code -} for an unknown partner; {@code "F1 div: ..."} for a divorced couple. Husbands are male,
     * wives female.
     */
    public static FamilyTree families(String... families) {
        java.util.Map<String, StringBuilder> people = new java.util.LinkedHashMap<>();
        StringBuilder fams = new StringBuilder();
        for (String spec : families) {
            String[] idAndRest = spec.split(":");
            String[] head = idAndRest[0].trim().split("\\s+");
            String fam = head[0];
            String[] parts = idAndRest[1].split(">");
            String[] couple = parts[0].split("\\+");
            fams.append("0 @").append(fam).append("@ FAM\n");
            if (head.length > 1 && head[1].equals("div")) {
                fams.append("1 DIV Y\n");
            }
            String[][] roles = {{couple[0].trim(), "HUSB", "M"}, {couple.length > 1 ? couple[1].trim() : "-", "WIFE", "F"}};
            for (String[] role : roles) {
                if (!role[0].equals("-")) {
                    fams.append("1 ").append(role[1]).append(" @").append(role[0]).append("@\n");
                    person(people, role[0], role[2]).append("1 FAMS @").append(fam).append("@\n");
                }
            }
            if (parts.length > 1) {
                for (String child : parts[1].trim().split("\\s+")) {
                    fams.append("1 CHIL @").append(child).append("@\n");
                    person(people, child, null).append("1 FAMC @").append(fam).append("@\n");
                }
            }
        }
        StringBuilder sb = new StringBuilder("0 HEAD\n1 GEDC\n2 VERS 7.0\n");
        people.values().forEach(sb::append);
        sb.append(fams).append("0 TRLR\n");
        return new GedcomModelBuilder().build(new GedcomReader().parse(sb.toString()));
    }

    private static StringBuilder person(java.util.Map<String, StringBuilder> people, String id, String sex) {
        StringBuilder p = people.computeIfAbsent(id, k -> new StringBuilder("0 @" + k + "@ INDI\n1 NAME Person "
                + k + " /Test/\n"));
        if (sex != null && p.indexOf("1 SEX") < 0) {
            p.append("1 SEX ").append(sex).append('\n');
        }
        return p;
    }

    /** A complete pedigree: person 1 has parents 2 and 3, person n has parents 2n and 2n+1. */
    public static FamilyTree fullPedigree(int generations) {
        int people = (1 << (generations + 1)) - 1;
        StringBuilder sb = new StringBuilder("0 HEAD\n1 GEDC\n2 VERS 7.0\n");
        for (int i = 1; i <= people; i++) {
            sb.append("0 @I").append(i).append("@ INDI\n");
            sb.append("1 NAME Person").append(i).append(" /Family").append(Integer.highestOneBit(i)).append("/\n");
            sb.append("1 SEX ").append(i % 2 == 0 ? "M" : "F").append('\n');
            sb.append("1 BIRT\n2 DATE ").append(2000 - 25 * (31 - Integer.numberOfLeadingZeros(i))).append('\n');
            if (2 * i + 1 <= people) {
                sb.append("1 FAMC @F").append(i).append("@\n");
            }
        }
        for (int i = 1; 2 * i + 1 <= people; i++) {
            sb.append("0 @F").append(i).append("@ FAM\n1 HUSB @I").append(2 * i).append("@\n1 WIFE @I")
                    .append(2 * i + 1).append("@\n1 CHIL @I").append(i).append("@\n");
        }
        sb.append("0 TRLR\n");
        return new GedcomModelBuilder().build(new GedcomReader().parse(sb.toString()));
    }
}
