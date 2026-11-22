package com.bw.jAncestryChart.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

/** An individual ({@code INDI} record). Relationships are navigable through its families. */
public final class Person {

    private final String id;
    private final String givenNames;
    private final String surname;
    private final String suffix;
    private final Sex sex;
    private final LifeEvent birth;
    private final LifeEvent death;
    private final boolean deceased;
    private final List<String> occupations;
    private final MediaReference portrait;
    final List<Family> familiesAsChild = new ArrayList<>();
    final List<Family> familiesAsSpouse = new ArrayList<>();

    Person(String id, String givenNames, String surname, String suffix, Sex sex, LifeEvent birth, LifeEvent death,
           boolean deceased, List<String> occupations, MediaReference portrait) {
        this.id = id;
        this.givenNames = givenNames;
        this.surname = surname;
        this.suffix = suffix;
        this.sex = sex;
        this.birth = birth;
        this.death = death;
        this.deceased = deceased;
        this.occupations = List.copyOf(occupations);
        this.portrait = portrait;
    }

    /** Identifier without {@code @}, e.g. {@code I5}. */
    public String id() {
        return id;
    }

    public String givenNames() {
        return givenNames;
    }

    public String surname() {
        return surname;
    }

    public String suffix() {
        return suffix;
    }

    public String fullName() {
        return formatName(givenNames, surname, suffix);
    }

    static String formatName(String givenNames, String surname, String suffix) {
        StringBuilder sb = new StringBuilder();
        for (String part : new String[] {givenNames, surname, suffix}) {
            if (!part.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(part);
            }
        }
        return sb.isEmpty() ? "(unnamed)" : sb.toString();
    }

    public Sex sex() {
        return sex;
    }

    /** Birth, or christening/baptism if no birth is recorded; may be {@code null}. */
    public LifeEvent birth() {
        return birth;
    }

    /** Death, or burial/cremation if no death is recorded; may be {@code null}. */
    public LifeEvent death() {
        return death;
    }

    public boolean isDeceased() {
        return deceased;
    }

    public List<String> occupations() {
        return occupations;
    }

    /** The primary portrait, or {@code null}. */
    public MediaReference portrait() {
        return portrait;
    }

    public List<Family> familiesAsChild() {
        return Collections.unmodifiableList(familiesAsChild);
    }

    public List<Family> familiesAsSpouse() {
        return Collections.unmodifiableList(familiesAsSpouse);
    }

    public Person father() {
        return familiesAsChild.isEmpty() ? null : familiesAsChild.get(0).husband();
    }

    public Person mother() {
        return familiesAsChild.isEmpty() ? null : familiesAsChild.get(0).wife();
    }

    /** Father and mother of the first family this person is a child of (only those that are known). */
    public List<Person> parents() {
        List<Person> result = new ArrayList<>(2);
        if (father() != null) {
            result.add(father());
        }
        if (mother() != null && mother() != father()) {
            result.add(mother());
        }
        return result;
    }

    /** Children of all families, in family order and then in the order given in the file. */
    public List<Person> children() {
        Set<Person> result = new LinkedHashSet<>();
        for (Family f : familiesAsSpouse) {
            result.addAll(f.children());
        }
        result.remove(this);
        return new ArrayList<>(result);
    }

    /** Known partners of all families, in family order. */
    public List<Person> partners() {
        Set<Person> result = new LinkedHashSet<>();
        for (Family f : familiesAsSpouse) {
            Person partner = f.partnerOf(this);
            if (partner != null) {
                result.add(partner);
            }
        }
        return new ArrayList<>(result);
    }

    public OptionalInt birthYear() {
        return birth == null || birth.date() == null ? OptionalInt.empty() : birth.date().year();
    }

    /** Short life span for labels, e.g. {@code 1909 – 2000}, {@code * 1939}, {@code † 1915}. */
    public String lifeSpan() {
        String b = birth == null || birth.date() == null ? null : birth.date().yearLabel();
        String d = death == null || death.date() == null ? null : death.date().yearLabel();
        if (b != null && d != null) {
            return b + " – " + d;
        }
        if (b != null) {
            return deceased ? b + " – †" : "* " + b;
        }
        if (d != null) {
            return "† " + d;
        }
        return deceased ? "†" : "";
    }

    @Override
    public String toString() {
        return fullName();
    }
}
