package com.bw.jAncestryChart.model;

import com.bw.jAncestryChart.gedcom.GedcomDate;
import com.bw.jAncestryChart.gedcom.GedcomDocument;
import com.bw.jAncestryChart.gedcom.GedcomNode;

import java.awt.Rectangle;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Builds the {@link FamilyTree} model from a parsed {@link GedcomDocument}. */
public final class GedcomModelBuilder {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "bmp", "tif", "tiff");

    public FamilyTree build(GedcomDocument doc) {
        List<String> warnings = new ArrayList<>(doc.warnings());
        Path baseDir = doc.source() == null ? null : doc.source().toAbsolutePath().getParent();
        boolean primaryMarkersUsed = usesPrimaryMarkers(doc);

        Map<String, Person> persons = new LinkedHashMap<>();
        for (GedcomNode indi : doc.records("INDI")) {
            if (indi.xref() == null) {
                warnings.add("Line " + indi.lineNumber() + ": INDI record without identifier ignored");
                continue;
            }
            persons.put(indi.xref(), readPerson(indi, doc, baseDir, primaryMarkersUsed, warnings));
        }

        Map<String, Family> families = new LinkedHashMap<>();
        for (GedcomNode fam : doc.records("FAM")) {
            if (fam.xref() == null) {
                warnings.add("Line " + fam.lineNumber() + ": FAM record without identifier ignored");
                continue;
            }
            families.put(fam.xref(), readFamily(fam, persons, warnings));
        }

        // Each person lists their families in the order given by FAMC/FAMS (first FAMC = birth family) ...
        for (GedcomNode indi : doc.records("INDI")) {
            Person person = persons.get(indi.xref());
            if (person == null) {
                continue;
            }
            for (GedcomNode link : indi.children("FAMC")) {
                Family f = families.get(link.pointer());
                if (f != null && f.children.contains(person) && !person.familiesAsChild.contains(f)) {
                    person.familiesAsChild.add(f);
                }
            }
            for (GedcomNode link : indi.children("FAMS")) {
                Family f = families.get(link.pointer());
                if (f != null && (f.husband == person || f.wife == person) && !person.familiesAsSpouse.contains(f)) {
                    person.familiesAsSpouse.add(f);
                }
            }
        }
        // ... followed by any family that references them without a matching link.
        for (Family f : families.values()) {
            for (Person partner : new Person[] {f.husband, f.wife}) {
                if (partner != null && !partner.familiesAsSpouse.contains(f)) {
                    partner.familiesAsSpouse.add(f);
                }
            }
            for (Person child : f.children) {
                if (!child.familiesAsChild.contains(f)) {
                    child.familiesAsChild.add(f);
                }
            }
        }
        return new FamilyTree(persons, families, doc.version(), doc.sourceProduct(), doc.source(), warnings);
    }

    private Person readPerson(GedcomNode indi, GedcomDocument doc, Path baseDir, boolean onlyPrimary,
                              List<String> warnings) {
        String[] name = parseName(indi.child("NAME"));
        List<String> occupations = new ArrayList<>();
        for (GedcomNode occu : indi.children("OCCU")) {
            if (occu.value() != null && !occu.value().isBlank()) {
                occupations.add(occu.value().trim());
            }
        }
        boolean deceased = indi.has("DEAT") || indi.has("BURI") || indi.has("CREM");
        MediaReference portrait = choosePortrait(indi, doc, baseDir, onlyPrimary,
                Person.formatName(name[0], name[1], name[2]), warnings);
        return new Person(indi.xref(), name[0], name[1], name[2],
                Sex.fromGedcom(indi.childValue("SEX")),
                firstEvent(indi, "BIRT", "CHR", "BAPM"),
                firstEvent(indi, "DEAT", "BURI", "CREM"),
                deceased, occupations, portrait);
    }

    private Family readFamily(GedcomNode fam, Map<String, Person> persons, List<String> warnings) {
        String status = fam.childValue("_RELATIONSHIP_STATUS");
        boolean divorced = fam.has("DIV") || (status != null && status.toUpperCase(Locale.ROOT).contains("DIVORCE"));
        Family family = new Family(fam.xref(), firstEvent(fam, "MARR"), divorced);
        family.husband = member(fam, "HUSB", persons, warnings);
        family.wife = member(fam, "WIFE", persons, warnings);
        for (GedcomNode chil : fam.children("CHIL")) {
            Person child = lookup(chil, persons, warnings);
            if (child != null && !family.children.contains(child)) {
                family.children.add(child);
            }
        }
        return family;
    }

    private static Person member(GedcomNode fam, String tag, Map<String, Person> persons, List<String> warnings) {
        GedcomNode link = fam.child(tag);
        return link == null ? null : lookup(link, persons, warnings);
    }

    private static Person lookup(GedcomNode link, Map<String, Person> persons, List<String> warnings) {
        if (link.pointer() == null) {
            return null;  // @VOID@ or no pointer at all
        }
        Person p = persons.get(link.pointer());
        if (p == null) {
            warnings.add("Line " + link.lineNumber() + ": " + link.tag() + " points to unknown person @"
                    + link.pointer() + "@");
        }
        return p;
    }

    /** First of the given events that carries a date or place (or merely exists). */
    private static LifeEvent firstEvent(GedcomNode record, String... tags) {
        LifeEvent bare = null;
        for (String tag : tags) {
            for (GedcomNode event : record.children(tag)) {
                GedcomDate date = GedcomDate.parse(event.childValue("DATE"));
                String place = event.childValue("PLAC");
                if (date != null || (place != null && !place.isBlank())) {
                    return new LifeEvent(tag, date, place == null || place.isBlank() ? null : place.trim());
                }
                if (bare == null) {
                    bare = new LifeEvent(tag, null, null);
                }
            }
        }
        return bare;
    }

    /** Splits {@code NAME Given /Surname/ Suffix} into {given, surname, suffix}. */
    static String[] parseName(GedcomNode name) {
        if (name == null) {
            return new String[] {"", "", ""};
        }
        String raw = name.valueOrEmpty();
        String given = raw;
        String surname = "";
        String suffix = "";
        int a = raw.indexOf('/');
        if (a >= 0) {
            int b = raw.indexOf('/', a + 1);
            given = raw.substring(0, a);
            surname = b < 0 ? raw.substring(a + 1) : raw.substring(a + 1, b);
            suffix = b < 0 ? "" : raw.substring(b + 1);
        }
        if (given.isBlank() && name.childValue("GIVN") != null) {
            given = name.childValue("GIVN").replace(',', ' ');
        }
        if (surname.isBlank() && name.childValue("SURN") != null) {
            surname = name.childValue("SURN").replace(',', ' ');
        }
        return new String[] {clean(given), clean(surname), clean(suffix)};
    }

    private static String clean(String s) {
        return s.trim().replaceAll("\\s+", " ");
    }

    // ---------------------------------------------------------------- portraits

    private static MediaReference choosePortrait(GedcomNode indi, GedcomDocument doc, Path baseDir,
                                                 boolean onlyPrimary, String personName, List<String> warnings) {
        for (GedcomNode link : indi.children("OBJE")) {
            GedcomNode record = link.pointer() != null ? doc.record(link.pointer()) : (link.isPointer() ? null : link);
            if (record == null) {
                continue;
            }
            if (onlyPrimary && !isPrimary(link) && !isPrimary(record)) {
                continue;
            }
            for (GedcomNode file : record.children("FILE")) {
                if (!looksLikeImage(file.value(), file.childValue("FORM"))) {
                    continue;
                }
                Optional<Path> path = MediaPaths.resolve(file.value(), baseDir);
                if (path.isEmpty()) {
                    warnings.add("Portrait of " + personName + " not found: " + file.value());
                    continue;
                }
                String title = file.childValue("TITL") != null ? file.childValue("TITL") : record.childValue("TITL");
                return new MediaReference(path.get(), parseCrop(link.child("CROP")), title);
            }
        }
        return null;
    }

    /** If any link is marked as primary image, only marked images are used as portraits. */
    private static boolean usesPrimaryMarkers(GedcomDocument doc) {
        for (GedcomNode indi : doc.records("INDI")) {
            for (GedcomNode link : indi.children("OBJE")) {
                GedcomNode record = link.pointer() == null ? null : doc.record(link.pointer());
                if (isPrimary(link) || (record != null && isPrimary(record))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Recognises the common vendor markers {@code _PRIM Y}, {@code _PRIMARY Y} and {@code _MULTIMEDIA_USES _PRIMARY_IMAGE}. */
    static boolean isPrimary(GedcomNode node) {
        for (GedcomNode c : node.children()) {
            String v = c.valueOrEmpty().trim().toUpperCase(Locale.ROOT);
            if ((c.tag().equals("_PRIM") || c.tag().equals("_PRIMARY")) && v.startsWith("Y")) {
                return true;
            }
            if (c.tag().equals("_MULTIMEDIA_USES") && v.contains("PRIMARY")) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeImage(String file, String form) {
        if (form != null && !form.isBlank()) {
            String f = form.trim().toLowerCase(Locale.ROOT);
            String subtype = f.startsWith("image/") ? f.substring(6) : f;
            return IMAGE_EXTENSIONS.contains(subtype);
        }
        if (file == null) {
            return false;
        }
        int dot = file.lastIndexOf('.');
        return dot >= 0 && IMAGE_EXTENSIONS.contains(file.substring(dot + 1).trim().toLowerCase(Locale.ROOT));
    }

    /** GEDCOM 7 {@code CROP} with {@code TOP}, {@code LEFT}, {@code HEIGHT}, {@code WIDTH} in pixels. */
    static Rectangle parseCrop(GedcomNode crop) {
        if (crop == null) {
            return null;
        }
        return new Rectangle(intValue(crop.childValue("LEFT"), 0), intValue(crop.childValue("TOP"), 0),
                intValue(crop.childValue("WIDTH"), -1), intValue(crop.childValue("HEIGHT"), -1));
    }

    private static int intValue(String s, int fallback) {
        if (s == null) {
            return fallback;
        }
        try {
            return (int) Math.round(Double.parseDouble(s.trim()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
