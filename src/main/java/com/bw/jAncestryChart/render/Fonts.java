package com.bw.jAncestryChart.render;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Set;

/** The fonts of the chart, with a fallback to logical fonts for characters a font cannot show. */
final class Fonts {

    final Font given;
    final Font surname;
    final Font years;
    final Font initials;
    final Font seal;

    private final String serif;

    Fonts() {
        Set<String> families = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        serif = families.contains("Georgia") ? "Georgia" : Font.SERIF;
        String sans = families.contains("Segoe UI") ? "Segoe UI" : Font.SANS_SERIF;
        given = new Font(sans, Font.PLAIN, 11);
        surname = new Font(serif, Font.BOLD, 15);
        years = new Font(sans, Font.PLAIN, 11).deriveFont(10.5f);
        initials = new Font(serif, Font.BOLD, 16);
        seal = new Font(sans, Font.BOLD, 10);
    }

    /** {@code font}, or a logical font of the same kind if {@code font} lacks glyphs for {@code text}. */
    Font forText(Font font, String text) {
        if (font.canDisplayUpTo(text) == -1) {
            return font;
        }
        String logical = font.getFamily().equals(serif) || font.getName().equals(serif) ? Font.SERIF : Font.SANS_SERIF;
        return new Font(logical, font.getStyle(), 12).deriveFont(font.getSize2D());
    }
}
