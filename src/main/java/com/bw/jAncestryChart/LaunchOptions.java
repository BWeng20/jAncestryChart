package com.bw.jAncestryChart;

import com.bw.jAncestryChart.layout.TreeMode;

import java.nio.file.Path;

/** Command-line options. */
public record LaunchOptions(Path file, String root, TreeMode mode, int generations, Path export, double scale,
                            boolean portraits, double spacing, boolean marriages, int compactFrom,
                            boolean straightLines, double coupleGap, boolean help) {

    public static final String USAGE = """
            Usage: java -jar jFamilyTreeMaker.jar [options] [file.ged]

              file.ged              GEDCOM 7 file to open
              --root <id|name>      person the chart is about, e.g. I5 or "Brigitte Klapper"
                                    (default: the person with the most known ancestors)
              --mode <mode>         ancestors (default) or descendants
              --generations <n>     how many generations to show (default 8)
              --spacing <0.5..3>    distance between generations and neighbours (default 1)
              --compact-from <n>    narrow upright boxes from generation n on
                                    (default 4)
              --no-portraits        do not draw portraits
              --no-marriages        do not paint marriage seals
              --couple-gap <px>     gap between the wide boxes of a married couple, 0..100
                                    (default 12; narrow boxes always touch)
              --bent-lines          ancestors: married parents always touch, lines bend to
                                    reach them (default: parents right above their child)
              --export <file.png>   render the chart into a PNG file and exit (no window)
              --scale <factor>      resolution factor for --export (default 2)
              -h, --help            show this help
            """;

    public static LaunchOptions parse(String[] args) {
        Path file = null;
        String root = null;
        TreeMode mode = TreeMode.ANCESTORS;
        int generations = 8;
        Path export = null;
        double scale = 2;
        boolean portraits = true;
        double spacing = 1;
        boolean marriages = true;
        int compactFrom = 4;
        boolean straightLines = true;
        double coupleGap = 12;
        boolean help = false;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "-h", "--help" -> help = true;
                case "--root" -> root = value(args, ++i, a);
                case "--mode" -> mode = TreeMode.parse(value(args, ++i, a));
                case "--generations" -> generations = (int) number(value(args, ++i, a), a, 1, 100);
                case "--export" -> export = Path.of(value(args, ++i, a));
                case "--scale" -> scale = number(value(args, ++i, a), a, 0.1, 16);
                case "--no-portraits" -> portraits = false;
                case "--no-marriages" -> marriages = false;
                case "--bent-lines" -> straightLines = false;
                case "--couple-gap" -> coupleGap = number(value(args, ++i, a), a, 0, 100);
                case "--spacing" -> spacing = number(value(args, ++i, a), a, 0.5, 3);
                case "--compact-from" -> compactFrom = (int) number(value(args, ++i, a), a, 1, 100);
                default -> {
                    if (a.startsWith("-")) {
                        throw new IllegalArgumentException("Unknown option: " + a);
                    }
                    if (file != null) {
                        throw new IllegalArgumentException("Only one GEDCOM file can be given");
                    }
                    file = Path.of(a);
                }
            }
        }
        if (export != null && file == null) {
            throw new IllegalArgumentException("--export needs a GEDCOM file");
        }
        return new LaunchOptions(file, root, mode, generations, export, scale, portraits, spacing, marriages,
                compactFrom, straightLines, coupleGap, help);
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(option + " needs a value");
        }
        return args[index];
    }

    private static double number(String text, String option, double min, double max) {
        double v;
        try {
            v = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(option + " expects a number, not '" + text + "'");
        }
        if (v < min || v > max) {
            throw new IllegalArgumentException(option + " must be between " + fmt(min) + " and " + fmt(max));
        }
        return v;
    }

    private static String fmt(double d) {
        return d == Math.rint(d) && Math.abs(d) < 1e15 ? String.valueOf((long) d) : String.valueOf(d);
    }
}
