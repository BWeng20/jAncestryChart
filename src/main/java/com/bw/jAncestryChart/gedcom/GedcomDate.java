package com.bw.jAncestryChart.gedcom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A GEDCOM date value such as {@code 16 SEP 2011}, {@code ABT 1806}, {@code BET 1820 AND 1830},
 * {@code FROM 1901 TO 1927}, {@code JULIAN 3 MAR 1700} or {@code 44 BCE}.
 * <p>
 * Values that do not follow the grammar are kept as {@link Qualifier#UNPARSED} so they can still be shown.
 */
public final class GedcomDate implements Comparable<GedcomDate> {

    public enum Qualifier { EXACT, ABOUT, CALCULATED, ESTIMATED, BEFORE, AFTER, BETWEEN, FROM, TO, FROM_TO, UNPARSED }

    public enum Calendar {
        GREGORIAN("", "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"),
        JULIAN(" (Julian)", "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"),
        FRENCH_R(" (French Republican)", "VEND", "BRUM", "FRIM", "NIVO", "PLUV", "VENT", "GERM", "FLOR", "PRAI",
                "MESS", "THER", "FRUC", "COMP"),
        HEBREW(" (Hebrew)", "TSH", "CSH", "KSL", "TVT", "SHV", "ADR", "ADS", "NSN", "IYR", "SVN", "TMZ", "AAV", "ELL");

        private final String suffix;
        private final List<String> months;

        Calendar(String suffix, String... months) {
            this.suffix = suffix;
            this.months = List.of(months);
        }

        int month(String token) {
            return months.indexOf(token) + 1;
        }

        String monthName(int month) {
            String token = months.get(month - 1);
            if (this == GREGORIAN || this == JULIAN) {
                return token.charAt(0) + token.substring(1).toLowerCase(Locale.ROOT);
            }
            return token;
        }
    }

    /** One calendar date. Month and day are 0 when unknown; BCE years are negative. */
    public record Point(Calendar calendar, int year, int month, int day) {

        public String display() {
            StringBuilder sb = new StringBuilder();
            if (day > 0) {
                sb.append(day).append(' ');
            }
            if (month > 0) {
                sb.append(calendar.monthName(month)).append(' ');
            }
            sb.append(year < 0 ? (-year) + " BCE" : String.valueOf(year));
            return sb.append(calendar.suffix).toString();
        }

        long sortKey() {
            return year * 10_000L + month * 100L + day;
        }
    }

    private static final Pattern YEAR = Pattern.compile("(\\d{1,4})(?:/\\d{1,2})?");
    private static final Pattern ANY_YEAR = Pattern.compile("(?<!\\d)(\\d{3,4})(?!\\d)");

    private final String raw;
    private final Qualifier qualifier;
    private final Point start;
    private final Point end;
    private final String phrase;

    private GedcomDate(String raw, Qualifier qualifier, Point start, Point end, String phrase) {
        this.raw = raw;
        this.qualifier = qualifier;
        this.start = start;
        this.end = end;
        this.phrase = phrase;
    }

    /** Parses a {@code DATE} payload; returns {@code null} for {@code null} or blank input. */
    public static GedcomDate parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String raw = text.trim();
        String phrase = null;
        String s = raw;
        int open = s.indexOf('(');
        if (open >= 0) {                       // GEDCOM 5.5.1 date phrase, e.g. "INT 1900 (about then)"
            int close = s.lastIndexOf(')');
            phrase = s.substring(open + 1, close > open ? close : s.length()).trim();
            s = s.substring(0, open).trim();
        }
        s = s.toUpperCase(Locale.ROOT)
                .replace("@#DGREGORIAN@", "GREGORIAN")
                .replace("@#DJULIAN@", "JULIAN")
                .replace("@#DFRENCH R@", "FRENCH_R")
                .replace("@#DHEBREW@", "HEBREW");
        List<String> t = new ArrayList<>(Arrays.asList(s.split("\\s+")));
        t.removeIf(String::isEmpty);
        if (!t.isEmpty() && t.get(0).equals("INT")) {
            t.remove(0);
        }
        if (t.isEmpty()) {
            return new GedcomDate(raw, Qualifier.UNPARSED, null, null, phrase);
        }
        try {
            String first = t.get(0);
            List<String> rest = t.subList(1, t.size());
            return switch (first) {
                case "ABT" -> new GedcomDate(raw, Qualifier.ABOUT, point(rest), null, phrase);
                case "CAL" -> new GedcomDate(raw, Qualifier.CALCULATED, point(rest), null, phrase);
                case "EST" -> new GedcomDate(raw, Qualifier.ESTIMATED, point(rest), null, phrase);
                case "BEF" -> new GedcomDate(raw, Qualifier.BEFORE, point(rest), null, phrase);
                case "AFT" -> new GedcomDate(raw, Qualifier.AFTER, point(rest), null, phrase);
                case "BET" -> {
                    int and = t.indexOf("AND");
                    if (and < 0) {
                        throw new IllegalArgumentException("BET without AND");
                    }
                    yield new GedcomDate(raw, Qualifier.BETWEEN, point(t.subList(1, and)),
                            point(t.subList(and + 1, t.size())), phrase);
                }
                case "FROM" -> {
                    int to = t.indexOf("TO");
                    yield to < 0
                            ? new GedcomDate(raw, Qualifier.FROM, point(rest), null, phrase)
                            : new GedcomDate(raw, Qualifier.FROM_TO, point(t.subList(1, to)),
                                    point(t.subList(to + 1, t.size())), phrase);
                }
                case "TO" -> new GedcomDate(raw, Qualifier.TO, null, point(rest), phrase);
                default -> new GedcomDate(raw, Qualifier.EXACT, point(t), null, phrase);
            };
        } catch (IllegalArgumentException e) {
            return new GedcomDate(raw, Qualifier.UNPARSED, null, null, phrase);
        }
    }

    private static Point point(List<String> tokens) {
        List<String> t = new ArrayList<>(tokens);
        if (t.isEmpty()) {
            throw new IllegalArgumentException("empty date");
        }
        Calendar calendar = Calendar.GREGORIAN;
        String head = t.get(0);
        if (Arrays.stream(Calendar.values()).anyMatch(c -> c.name().equals(head))) {
            calendar = Calendar.valueOf(head);
            t.remove(0);
        } else if (head.startsWith("_") || head.startsWith("@#")) {
            throw new IllegalArgumentException("unsupported calendar " + head);
        }
        boolean bce = false;
        if (!t.isEmpty()) {
            String last = t.get(t.size() - 1);
            if (last.equals("BCE") || last.equals("BC") || last.equals("B.C.")) {
                bce = true;
                t.remove(t.size() - 1);
            }
        }
        if (t.isEmpty() || t.size() > 3) {
            throw new IllegalArgumentException("bad date");
        }
        Matcher y = YEAR.matcher(t.get(t.size() - 1));
        if (!y.matches()) {
            throw new IllegalArgumentException("bad year");
        }
        int year = Integer.parseInt(y.group(1));
        int month = 0;
        int day = 0;
        if (t.size() >= 2) {
            month = calendar.month(t.get(t.size() - 2));
            if (month == 0) {
                throw new IllegalArgumentException("bad month");
            }
        }
        if (t.size() == 3) {
            day = Integer.parseInt(t.get(0));
            if (day < 1 || day > 36) {
                throw new IllegalArgumentException("bad day");
            }
        }
        return new Point(calendar, bce ? -year : year, month, day);
    }

    public String raw() {
        return raw;
    }

    public Qualifier qualifier() {
        return qualifier;
    }

    /** First (or only) date; {@code null} for {@code TO} dates and unparsed values. */
    public Point start() {
        return start;
    }

    /** Second date of ranges and periods, or the date of a {@code TO} value. */
    public Point end() {
        return end;
    }

    public String phrase() {
        return phrase;
    }

    public boolean isParsed() {
        return qualifier != Qualifier.UNPARSED;
    }

    /** The most representative year, used for sorting and short labels. */
    public OptionalInt year() {
        if (start != null) {
            return OptionalInt.of(start.year());
        }
        if (end != null) {
            return OptionalInt.of(end.year());
        }
        Matcher m = ANY_YEAR.matcher(raw);
        return m.find() ? OptionalInt.of(Integer.parseInt(m.group(1))) : OptionalInt.empty();
    }

    /** Compact year text for tree labels: {@code 1806}, {@code ~1806}, {@code <1915}, {@code >1820}. */
    public String yearLabel() {
        return switch (qualifier) {
            case EXACT, FROM, FROM_TO -> year(start.year());
            case TO -> year(end.year());
            case ABOUT, CALCULATED, ESTIMATED -> "~" + year(start.year());
            case BEFORE -> "<" + year(start.year());
            case AFTER -> ">" + year(start.year());
            case BETWEEN -> "~" + year((int) Math.round((start.year() + end.year()) / 2.0));
            case UNPARSED -> year().isPresent() ? "~" + year(year().getAsInt()) : "?";
        };
    }

    /** Readable English text, e.g. {@code about 1806} or {@code between 1820 and 1830}. */
    public String toDisplayString() {
        String text = switch (qualifier) {
            case EXACT -> start.display();
            case ABOUT -> "about " + start.display();
            case CALCULATED -> "calculated " + start.display();
            case ESTIMATED -> "estimated " + start.display();
            case BEFORE -> "before " + start.display();
            case AFTER -> "after " + start.display();
            case BETWEEN -> "between " + start.display() + " and " + end.display();
            case FROM -> "from " + start.display();
            case TO -> "to " + end.display();
            case FROM_TO -> "from " + start.display() + " to " + end.display();
            case UNPARSED -> raw;
        };
        return phrase != null && qualifier != Qualifier.UNPARSED ? text + " (" + phrase + ")" : text;
    }

    private static String year(int y) {
        return y < 0 ? (-y) + " BCE" : String.valueOf(y);
    }

    private long sortKey() {
        if (start != null) {
            return start.sortKey();
        }
        if (end != null) {
            return end.sortKey();
        }
        return year().isPresent() ? year().getAsInt() * 10_000L : Long.MAX_VALUE;
    }

    @Override
    public int compareTo(GedcomDate other) {
        return Long.compare(sortKey(), other.sortKey());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof GedcomDate d && d.raw.equals(raw);
    }

    @Override
    public int hashCode() {
        return raw.hashCode();
    }

    @Override
    public String toString() {
        return toDisplayString();
    }
}
