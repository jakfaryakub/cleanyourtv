package com.gathin.tvhome;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

    @Test public void names() {
        assertEquals("Purple Eyes", ThemeSlots.name("  Purple Eyes ", "purple-eyes"));
        assertEquals("purple-eyes", ThemeSlots.name("", "purple-eyes"));
        assertEquals("purple-eyes", ThemeSlots.name(null, "purple-eyes"));
        assertEquals(40, ThemeSlots.name("x".repeat(60), "a").length());
    }

    @Test public void activeId() {
        File root = new File("/r");
        assertEquals("builtin", ThemeSlots.active(root, null));
        assertEquals("legacy", ThemeSlots.active(root, root));
        assertEquals("red-eyes", ThemeSlots.active(root, new File("/r/themes/red-eyes")));
    }

    private static void write(File f, int n) throws IOException {
        f.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(f)) { out.write(new byte[n]); }
    }

    @Test public void slotSizeCountsOneFolderDown() throws IOException {
        File slot = Files.createTempDirectory("slot").toFile();
        write(new File(slot, "config.json"), 100);
        write(new File(slot, "wallpaper.jpg"), 1000);
        write(new File(slot, "fonts/a.ttf"), 10000);
        write(new File(slot, "icons/com.a.b.png"), 50);
        write(new File(slot, "icons/deep/x.png"), 7);   // two down: not a theme file
        assertEquals(11150, ThemeSlots.bytes(slot, 0));
    }

    @Test public void filesMustStayUnderTheRoot() throws IOException {
        File root = Files.createTempDirectory("root").toFile();
        assertTrue(ThemeFiles.inside(root, new File(root, "themes/a/config.json")));
        assertFalse(ThemeFiles.inside(root, new File(root, "themes/../../x")));
        assertFalse(ThemeFiles.inside(root, root));
        File outside = Files.createTempDirectory("outside").toFile();
        File link = new File(root, "themes");
        Files.createSymbolicLink(link.toPath(), outside.toPath());
        assertFalse(ThemeFiles.inside(root, new File(root, "themes/a/config.json")));
    }

    @Test public void writingThePointer() throws IOException {
        File root = Files.createTempDirectory("root").toFile();
        assertTrue(ThemeSlots.write(root, "red-eyes"));
        assertEquals("red-eyes", ThemeSlots.pointer(new File(root, "active_theme")));
        assertTrue(ThemeSlots.write(root, "builtin"));
        assertEquals("builtin", ThemeSlots.pointer(new File(root, "active_theme")));
        assertTrue(ThemeSlots.write(root, "legacy"));
        assertFalse(ThemeSlots.write(root, "../x"));
        assertFalse(ThemeSlots.write(root, "Red"));
        assertFalse(ThemeSlots.write(null, "red-eyes"));
        assertEquals("legacy", ThemeSlots.pointer(new File(root, "active_theme")));
        assertFalse(new File(root, "active_theme.tmp").exists());
    }
}
