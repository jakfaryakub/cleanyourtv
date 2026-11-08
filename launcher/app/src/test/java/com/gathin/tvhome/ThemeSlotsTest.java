package com.gathin.tvhome;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ThemeSlotsTest {
    private static final ThemeSlots.Usable ONLY_RED = slug -> "red-eyes".equals(slug);

    @Test public void noPointerKeepsTheRootAsBefore() {
        assertEquals("legacy", ThemeSlots.resolve(null, true, ONLY_RED));
        assertEquals("legacy", ThemeSlots.resolve(null, false, ONLY_RED));
    }

    @Test public void builtinIgnoresTheRoot() {
        assertNull(ThemeSlots.resolve("builtin", true, ONLY_RED));
        assertNull(ThemeSlots.resolve("builtin", false, ONLY_RED));
    }

    @Test public void legacyNeedsARootConfig() {
        assertEquals("legacy", ThemeSlots.resolve("legacy", true, ONLY_RED));
        assertNull(ThemeSlots.resolve("legacy", false, ONLY_RED));
    }

    @Test public void aUsableSlotIsUsed() {
        assertEquals("red-eyes", ThemeSlots.resolve("red-eyes", true, ONLY_RED));
        assertEquals("red-eyes", ThemeSlots.resolve("red-eyes", false, ONLY_RED));
    }

    @Test public void aBrokenOrMissingSlotFallsBack() {
        assertEquals("legacy", ThemeSlots.resolve("purple-eyes", true, ONLY_RED));
        assertNull(ThemeSlots.resolve("purple-eyes", false, ONLY_RED));
        assertNull(ThemeSlots.resolve("purple-eyes", false, null));
    }

    @Test public void aBadSlugFallsBackWithoutAsking() {
        ThemeSlots.Usable never = slug -> { throw new AssertionError("asked for " + slug); };
        assertEquals("legacy", ThemeSlots.resolve("../etc", true, never));
        assertEquals("legacy", ThemeSlots.resolve("Red", true, never));
        assertNull(ThemeSlots.resolve("a/b", false, never));
    }

    @Test public void pointerText() {
        assertEquals("red-eyes", ThemeSlots.parse("red-eyes\n"));
        assertEquals("red-eyes", ThemeSlots.parse("  red-eyes \r\nmore"));
        assertNull(ThemeSlots.parse(""));
        assertNull(ThemeSlots.parse(" \n"));
        assertNull(ThemeSlots.parse(null));
    }

    @Test public void folders() {
        File root = new File("/r");
        assertEquals(root, ThemeSlots.folder(root, "legacy"));
        assertEquals(new File("/r/themes/red-eyes"), ThemeSlots.folder(root, "red-eyes"));
        assertNull(ThemeSlots.folder(root, null));
        assertNull(ThemeSlots.folder(null, "red-eyes"));
    }
}
