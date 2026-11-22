# jAncestryChart

Reads **GEDCOM 7** files and draws a family chart in generation rows: the chosen person at the bottom
with their ancestors above (or at the top with their descendants below). Partners stand side by side
in the same row, with their marriage between them. Java 21 + Swing/Java2D; the only runtime dependency is [jSVGIcon](https://github.com/bweng20/jSVGIcon) (SVG icons).

## Build & run

Requires JDK 21+ (Gradle is used via the wrapper or a local install).

```
gradle jar
java -jar build/libs/jAncestryChart.jar                     # empty window, open a file with Ctrl+O
java -jar build/libs/jAncestryChart.jar family.ged --root I5
```

Render straight to a PNG without opening a window:

```
java -jar build/libs/jAncestryChart.jar family.ged --root "Anna Becker" --mode descendants --export chart.png
```

| Option | Meaning |
|---|---|
| `--root <id\|name>` | person the chart is about (default: the one with the most known ancestors) |
| `--mode ancestors\|descendants` | ancestors grow upwards, descendants downwards (default `ancestors`) |
| `--generations <n>` | how many generations to show (default 8) |
| `--spacing <0.5..3>` | distance between generations and neighbours (default 1) |
| `--compact-from <n>` | from generation n on: narrow upright boxes (default 4) |
| `--no-portraits` | plain name boxes |
| `--no-marriages` | no marriage seals (couples still stand side by side) |
| `--couple-gap <0..100>` | gap in pixels between the wide boxes of a married couple (default 12; narrow boxes always touch) |
| `--bent-lines` | ancestors: married parents always stand close together and the lines bend to reach them |
| `--export <file.png>`, `--scale <f>` | headless PNG export and its resolution factor |

## Using the window

* **Click** a person to show the chart of them; **Ctrl+F** opens a searchable person list.
* **Drag** to pan, **mouse wheel** or **+/-** to zoom, **double-click** empty space or **0** to fit.
* Upper toolbar: root person, ancestors/descendants, number of generations, fit, PNG export (Ctrl+E).
* Lower toolbar: *Spacing*, *Compact from generation*, *Couple gap*, portraits, marriage seals and straight lines on/off. GEDCOM files can also be dropped onto
  the window.
* Hover a box for dates, places, occupation and partners. A small green bud means more generations
  exist beyond the current limit.

### How people are placed

Every generation is one row. Each person stands in a row group with their partners (the root person's
partners, and everybody's in a descendant chart): the first partner to the right, the second to the
left, and so on. Married partners stand close together, with the marriage seal in the middle at their
lower edge: the wide boxes *Couple gap* apart (`--couple-gap`, default 12 px), the narrow upright boxes
touching. Divorced partners keep their distance, joined by a line with the seal on it. Only two partners
fit right next to a person; a third (or later) partner stands outside the partner on that side, and a
bracket leads from the person over that partner's head to them, with the seal on it. The line to their
children goes down through the gap between the two partners, which grows if the children need the room.

* **Tight and symmetric packing.** Branches are packed side by side as tightly as their outlines allow,
  row by row: once from the left and once from the right, and the two results are averaged. A mirrored
  family gives a mirrored chart, and small families sit evenly between large ones.
* **Ancestors:** parents stand right above their child, so every line is straight. Married parents
  stand close together if their own ancestors leave room for it; if those ancestors need more room (or the parents
  are divorced), the parents stand apart, joined by a line with the seal on it. The child may then sit
  anywhere along that line, at the spot closest to the middle of the child's own ancestors.
  With *Straight lines* off (`--bent-lines`) married parents always stand close together instead: they stand centred
  between the spots right above their own parents, and the lines bend to reach them. This makes the
  chart narrower.
* **Descendants:** the children of each couple are centred under that couple's seal.
* **Compact generations.** From generation 4 on (*Compact from generation* / `--compact-from`) the boxes
  are narrow and upright (portrait or initials on top, names below), and the seal shows only the year.
  This roughly halves the width of the outer rows.

### Marriages

Between two partners sits a small gold seal with two wedding rings, showing the marriage
date (full date if exact, otherwise the year; only the year in compact rows; no text if unknown). Divorced couples get separated, crossed-out
rings and "div.". The lines to the children start at the seal. The tooltip of a person tells when
and where their parents married. Switch the seals off with *Marriages* / `--no-marriages`.

## What is read from GEDCOM

* GEDCOM 7.0 lines: UTF-8 (with or without BOM), any line ending, `CONT`, leading `@@` escape,
  `@VOID@` pointers, extension tags are kept and ignored.
* Lenient extras, each reported as a warning (see the status bar tooltip): GEDCOM 5.5.1 `CONC`,
  non-UTF-8 files, odd indentation and level jumps, dangling pointers.
* `INDI`: `NAME` (incl. `GIVN`/`SURN`), `SEX`, `BIRT`/`CHR`/`BAPM`, `DEAT`/`BURI`/`CREM`, `OCCU`, `FAMC`/`FAMS`, `OBJE`.
* `FAM`: `HUSB`, `WIFE`, `CHIL` (in file order), `MARR`, `DIV`.
* Dates: exact, `ABT`/`CAL`/`EST`, `BEF`/`AFT`, `BET … AND …`, `FROM … TO …`, `BCE`,
  Gregorian/Julian/French Republican/Hebrew calendars; unparseable values are shown as written.
* Portraits: image `OBJE` links (JPEG, PNG, GIF, BMP, TIFF) with GEDCOM 7 `CROP`. If a file marks
  primary images (`_PRIM Y`, `_PRIMARY Y`, `_MULTIMEDIA_USES _PRIMARY_IMAGE`) only those are used.
  `FILE` paths may be URIs, Windows paths or relative paths; as a last resort the file name is
  searched next to the GEDCOM file. HEIC images cannot be read by Java and show initials instead.

Not supported (yet): GEDZIP (`.gdz`) packages, sources/notes display, editing.

## Code layout

Base package: `com.bw.jAncestryChart`.

| Package | Purpose |
|---|---|
| `gedcom` | line parser (`GedcomReader`), node tree, date parser |
| `model` | `Person`, `Family`, `FamilyTree`, and `GedcomModelBuilder` |
| `layout` | `TreeBuilder` (who is shown, incl. partner boxes) and `GenerationLayout` (where): one row per generation, couples side by side, subtrees packed by their row outlines from both sides, compact outer rows; `Couple` holds each marriage |
| `render` | Java2D painting of boxes, couples and lines; portrait cache, PNG export |
| `ui` | Swing window, canvas with pan/zoom, person search |
