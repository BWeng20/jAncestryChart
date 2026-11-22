package com.bw.jAncestryChart.model;

import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** All people and families of one GEDCOM file. */
public final class FamilyTree {

    /** Orders people by surname, then given names, then birth year (accents ignored). */
    public static final Comparator<Person> BY_NAME = Comparator
            .comparing((Person p) -> normalize(p.surname()))
            .thenComparing(p -> normalize(p.givenNames()))
            .thenComparingInt(p -> p.birthYear().orElse(Integer.MAX_VALUE));

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    private final Map<String, Person> persons;
    private final Map<String, Family> families;
    private final String gedcomVersion;
    private final String sourceProduct;
    private final Path source;
    private final List<String> warnings;

    FamilyTree(Map<String, Person> persons, Map<String, Family> families, String gedcomVersion,
               String sourceProduct, Path source, List<String> warnings) {
        this.persons = Collections.unmodifiableMap(new LinkedHashMap<>(persons));
        this.families = Collections.unmodifiableMap(new LinkedHashMap<>(families));
        this.gedcomVersion = gedcomVersion;
        this.sourceProduct = sourceProduct;
        this.source = source;
        this.warnings = List.copyOf(warnings);
    }

    /** People in file order. */
    public Collection<Person> persons() {
        return persons.values();
    }

    /** Families in file order. */
    public Collection<Family> families() {
        return families.values();
    }

    /** Person by identifier; accepts {@code I5} as well as {@code @I5@}. */
    public Optional<Person> person(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(persons.get(stripAt(id)));
    }

    /** Looks a person up by identifier first, then by name (first match of {@link #search}). */
    public Optional<Person> findPerson(String idOrName) {
        Optional<Person> byId = person(idOrName);
        if (byId.isPresent()) {
            return byId;
        }
        List<Person> matches = search(idOrName);
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
    }

    /**
     * People whose name, identifier or life span contains every word of the query
     * (case and accents ignored), sorted by name.
     */
    public List<Person> search(String query) {
        String[] words = normalize(query == null ? "" : query).trim().split("\\s+");
        return persons.values().stream()
                .filter(p -> {
                    String haystack = normalize(p.fullName() + " " + p.id() + " " + p.lifeSpan());
                    for (String w : words) {
                        if (!w.isEmpty() && !haystack.contains(w)) {
                            return false;
                        }
                    }
                    return true;
                })
                .sorted(BY_NAME)
                .toList();
    }

    /**
     * A good default person for an ancestor tree: the one with the most known ancestors
     * (ties go to the youngest).
     */
    public Person suggestRoot() {
        Person best = null;
        int bestCount = -1;
        int bestYear = Integer.MIN_VALUE;
        for (Person p : persons.values()) {
            int count = countAncestors(p);
            int year = p.birthYear().orElse(Integer.MIN_VALUE);
            if (count > bestCount || (count == bestCount && year > bestYear)) {
                best = p;
                bestCount = count;
                bestYear = year;
            }
        }
        return best;
    }

    /** Number of distinct known ancestors of a person. */
    public static int countAncestors(Person person) {
        Set<Person> seen = new HashSet<>();
        Deque<Person> todo = new ArrayDeque<>(person.parents());
        while (!todo.isEmpty()) {
            Person p = todo.pop();
            if (seen.add(p)) {
                todo.addAll(p.parents());
            }
        }
        seen.remove(person);
        return seen.size();
    }

    public String gedcomVersion() {
        return gedcomVersion;
    }

    public String sourceProduct() {
        return sourceProduct;
    }

    /** The GEDCOM file, or {@code null} if the data did not come from a file. */
    public Path source() {
        return source;
    }

    /** Problems found while reading the file and building the model. */
    public List<String> warnings() {
        return warnings;
    }

    static String normalize(String s) {
        return MARKS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private static String stripAt(String id) {
        String s = id.trim();
        return s.length() >= 2 && s.startsWith("@") && s.endsWith("@") ? s.substring(1, s.length() - 1) : s;
    }
}
