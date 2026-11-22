package com.bw.jAncestryChart;

import com.bw.jAncestryChart.layout.TreeMode;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaunchOptionsTest {

    @Test
    void defaults() {
        LaunchOptions o = LaunchOptions.parse(new String[0]);
        assertNull(o.file());
        assertEquals(TreeMode.ANCESTORS, o.mode());
        assertEquals(8, o.generations());
        assertTrue(o.portraits());
        assertEquals(1, o.spacing());
        assertTrue(o.marriages());
        assertEquals(4, o.compactFrom());
        assertTrue(o.straightLines());
        assertEquals(12, o.coupleGap());
        assertFalse(o.help());
    }

    @Test
    void parsesEverything() {
        LaunchOptions o = LaunchOptions.parse(new String[] {"family.ged", "--root", "Anna Becker", "--mode", "desc",
            "--generations", "4", "--export", "out.png", "--scale", "1.5", "--no-portraits", "--spacing", "1.5",
            "--no-marriages", "--compact-from", "3", "--bent-lines", "--couple-gap", "20"});
        assertEquals(Path.of("family.ged"), o.file());
        assertEquals("Anna Becker", o.root());
        assertEquals(TreeMode.DESCENDANTS, o.mode());
        assertEquals(4, o.generations());
        assertEquals(Path.of("out.png"), o.export());
        assertEquals(1.5, o.scale());
        assertFalse(o.portraits());
        assertEquals(1.5, o.spacing());
        assertFalse(o.marriages());
        assertEquals(3, o.compactFrom());
        assertFalse(o.straightLines());
        assertEquals(20, o.coupleGap());
    }

    @Test
    void rejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse(new String[] {"--bogus"}));
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse(new String[] {"--root"}));
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse(new String[] {"--organic", "0.5"}),
                "the tree options are gone");
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse(new String[] {"--generations", "x"}));
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse(new String[] {"a.ged", "b.ged"}));
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse(new String[] {"--export", "x.png"}));
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse(new String[] {"--mode", "sideways"}));
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse(new String[] {"--spacing", "0.1"}));
    }
}
