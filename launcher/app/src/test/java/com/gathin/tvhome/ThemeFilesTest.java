package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ThemeFilesTest {
    @Test public void themeFilesAreAllowed() {
        assertEquals("config.json", ThemeFiles.path(Collections.singletonList("config.json")));
        assertEquals("wallpaper.jpg", ThemeFiles.path(Collections.singletonList("wallpaper.jpg")));
        assertEquals("wallpaper.webp", ThemeFiles.path(Collections.singletonList("wallpaper.webp")));
        assertEquals("theme_revision", ThemeFiles.path(Collections.singletonList("theme_revision")));
    }

    @Test public void collectionImagesAreAllowed() {
        assertEquals("wallpapers/dusk.jpg", ThemeFiles.path(Arrays.asList("wallpapers", "dusk.jpg")));
        assertEquals("wallpapers/Night_2.WEBP", ThemeFiles.path(Arrays.asList("wallpapers", "Night_2.WEBP")));
    }

    @Test public void theCollectionsCaptionsAreAllowed() {
        assertEquals("wallpapers/captions.txt", ThemeFiles.path(Arrays.asList("wallpapers", "captions.txt")));
        assertNull(ThemeFiles.path(Collections.singletonList("captions.txt")));
        assertNull(ThemeFiles.path(Arrays.asList("wallpapers", "notes.txt")));
        assertFalse(ThemeFiles.collectionImage("captions.txt"));
    }

    @Test public void anythingElseIsRefused() {
        assertNull(ThemeFiles.path(null));
        assertNull(ThemeFiles.path(Collections.emptyList()));
        assertNull(ThemeFiles.path(Collections.singletonList("cards.json")));
        assertNull(ThemeFiles.path(Collections.singletonList("..")));
        assertNull(ThemeFiles.path(Arrays.asList("..", "config.json")));
        assertNull(ThemeFiles.path(Arrays.asList("wallpapers", "..")));
        assertNull(ThemeFiles.path(Arrays.asList("wallpapers", "run.sh")));
        assertNull(ThemeFiles.path(Arrays.asList("wallpapers", "a b.jpg")));
        assertNull(ThemeFiles.path(Arrays.asList("wallpapers", "../config.jpg")));
        assertNull(ThemeFiles.path(Arrays.asList("wallpapers", ".jpg")));
        assertNull(ThemeFiles.path(Arrays.asList("other", "dusk.jpg")));
        assertNull(ThemeFiles.path(Arrays.asList("wallpapers", "sub", "dusk.jpg")));
    }

    @Test public void onlyTheBareFolderIsTheCollection() {
        assertTrue(ThemeFiles.isCollection(Collections.singletonList("wallpapers")));
        assertFalse(ThemeFiles.isCollection(Arrays.asList("wallpapers", "dusk.jpg")));
        assertFalse(ThemeFiles.isCollection(Collections.singletonList("config.json")));
    }

    @Test public void themeFontsAreAllowed() {
        assertEquals("fonts/plus-jakarta-sans.ttf", ThemeFiles.path(Arrays.asList("fonts", "plus-jakarta-sans.ttf")));
        assertEquals("fonts/Inter_2.OTF", ThemeFiles.path(Arrays.asList("fonts", "Inter_2.OTF")));
        assertTrue(ThemeFiles.fontFile("manrope.ttf"));
    }

    @Test public void otherFontPathsAreRefused() {
        assertNull(ThemeFiles.path(Collections.singletonList("inter.ttf")));
        assertNull(ThemeFiles.path(Arrays.asList("fonts", "inter.ttc")));
        assertNull(ThemeFiles.path(Arrays.asList("fonts", "inter.woff2")));
        assertNull(ThemeFiles.path(Arrays.asList("fonts", "dusk.jpg")));
        assertNull(ThemeFiles.path(Arrays.asList("fonts", "..")));
        assertNull(ThemeFiles.path(Arrays.asList("fonts", "a..ttf")));
        assertNull(ThemeFiles.path(Arrays.asList("fonts", ".ttf")));
        assertNull(ThemeFiles.path(Arrays.asList("fonts", "Inter[wght].ttf")));
        assertNull(ThemeFiles.path(Arrays.asList("fonts", "a b.ttf")));
        assertNull(ThemeFiles.path(Arrays.asList("wallpapers", "inter.ttf")));
        assertNull(ThemeFiles.path(Arrays.asList("fonts", "sub", "inter.ttf")));
    }

    @Test public void onlyTheBareFolderIsTheFonts() {
        assertTrue(ThemeFiles.isFonts(Collections.singletonList("fonts")));
        assertFalse(ThemeFiles.isFonts(Arrays.asList("fonts", "inter.ttf")));
        assertFalse(ThemeFiles.isFonts(Collections.singletonList("wallpapers")));
    }

    @Test public void appLogosAreAllowedByPackageName() {
        assertEquals("icons/com.netflix.ninja.png", ThemeFiles.path(Arrays.asList("icons", "com.netflix.ninja.png")));
        assertEquals("icons/com.bstar.intl.tv.PNG", ThemeFiles.path(Arrays.asList("icons", "com.bstar.intl.tv.PNG")));
        assertEquals("icons/io.github.a_b.App2.png", ThemeFiles.path(Arrays.asList("icons", "io.github.a_b.App2.png")));
    }

    @Test public void otherLogoPathsAreRefused() {
        assertNull(ThemeFiles.path(Arrays.asList("icons", "netflix.png")));           // one segment is no package
        assertNull(ThemeFiles.path(Arrays.asList("icons", "com.netflix.ninja.webp")));
        assertNull(ThemeFiles.path(Arrays.asList("icons", "com..ninja.png")));
        assertNull(ThemeFiles.path(Arrays.asList("icons", ".com.netflix.png")));
        assertNull(ThemeFiles.path(Arrays.asList("icons", "com.1netflix.png")));
        assertNull(ThemeFiles.path(Arrays.asList("icons", "com.net-flix.png")));
        assertNull(ThemeFiles.path(Arrays.asList("icons", "..")));
        assertNull(ThemeFiles.path(Collections.singletonList("com.netflix.ninja.png")));
        assertNull(ThemeFiles.path(Arrays.asList("icons", "sub", "com.netflix.ninja.png")));
    }

    @Test public void onlyTheBareFolderIsTheIcons() {
        assertTrue(ThemeFiles.isIcons(Collections.singletonList("icons")));
        assertFalse(ThemeFiles.isIcons(Arrays.asList("icons", "com.netflix.ninja.png")));
        assertFalse(ThemeFiles.isIcons(Collections.singletonList("fonts")));
        assertFalse(ThemeFiles.isIcons(null));
    }

    @Test public void aPackageNamesItsLogo() {
        assertEquals("com.netflix.ninja.png", ThemeFiles.iconName("com.netflix.ninja"));
        assertNull(ThemeFiles.iconName("netflix"));
        assertNull(ThemeFiles.iconName("../x.y"));
    }

    @Test public void slotFilesAreAllowed() {
        assertEquals("themes/red-eyes/config.json", ThemeFiles.path(Arrays.asList("themes", "red-eyes", "config.json")));
        assertEquals("themes/red-eyes/wallpaper.jpg", ThemeFiles.path(Arrays.asList("themes", "red-eyes", "wallpaper.jpg")));
        assertEquals("themes/p1/wallpaper.webp", ThemeFiles.path(Arrays.asList("themes", "p1", "wallpaper.webp")));
        assertEquals("themes/p1/wallpapers/dusk.jpg", ThemeFiles.path(Arrays.asList("themes", "p1", "wallpapers", "dusk.jpg")));
        assertEquals("themes/p1/fonts/inter.ttf", ThemeFiles.path(Arrays.asList("themes", "p1", "fonts", "inter.ttf")));
        assertEquals("themes/p1/icons/com.netflix.ninja.png",
                ThemeFiles.path(Arrays.asList("themes", "p1", "icons", "com.netflix.ninja.png")));
        assertEquals("active_theme", ThemeFiles.path(Collections.singletonList("active_theme")));
    }

    @Test public void otherSlotPathsAreRefused() {
        assertNull(ThemeFiles.path(Arrays.asList("themes", "p1", "theme_revision")));
        assertNull(ThemeFiles.path(Arrays.asList("themes", "p1", "active_theme")));
        assertNull(ThemeFiles.path(Arrays.asList("themes", "p1")));
        assertNull(ThemeFiles.path(Collections.singletonList("themes")));
        assertNull(ThemeFiles.path(Arrays.asList("themes", "..", "config.json")));
        assertNull(ThemeFiles.path(Arrays.asList("themes", "Red", "config.json")));
        assertNull(ThemeFiles.path(Arrays.asList("themes", "-a", "config.json")));
        assertNull(ThemeFiles.path(Arrays.asList("themes", "legacy", "config.json")));
        assertNull(ThemeFiles.path(Arrays.asList("themes", "builtin", "config.json")));
        assertNull(ThemeFiles.path(Arrays.asList("themes", "p1", "themes", "p2", "config.json")));
        assertNull(ThemeFiles.path(Arrays.asList("themes", "p1", "wallpapers", "..")));
        assertNull(ThemeFiles.path(Arrays.asList("themes", "p1", "fonts", "sub", "a.ttf")));
        assertNull(ThemeFiles.path(Arrays.asList("themes", "p1", "notes.txt")));
    }

    @Test public void slugs() {
        assertTrue(ThemeFiles.slug("purple-eyes"));
        assertTrue(ThemeFiles.slug("a"));
        assertTrue(ThemeFiles.slug("0123456789abcdef0123456789abcdef"));
        assertFalse(ThemeFiles.slug("0123456789abcdef0123456789abcdef0"));
        assertFalse(ThemeFiles.slug(null));
        assertFalse(ThemeFiles.slug(""));
        assertFalse(ThemeFiles.slug("-a"));
        assertFalse(ThemeFiles.slug("Red"));
        assertFalse(ThemeFiles.slug("a_b"));
        assertFalse(ThemeFiles.slug("a.b"));
        assertFalse(ThemeFiles.slug("a/b"));
        assertFalse(ThemeFiles.slug("legacy"));
        assertFalse(ThemeFiles.slug("builtin"));
    }

    @Test public void slotFoldersAreFoundUnderTheSlot() {
        assertEquals("red-eyes", ThemeFiles.slot(Arrays.asList("themes", "red-eyes")));
        assertNull(ThemeFiles.slot(Arrays.asList("themes", "Red")));
        assertNull(ThemeFiles.slot(Arrays.asList("themes", "red-eyes", "config.json")));
        assertTrue(ThemeFiles.isThemes(Collections.singletonList("themes")));
        assertEquals("themes/p1/", ThemeFiles.prefix(Arrays.asList("themes", "p1", "icons")));
        assertTrue(ThemeFiles.isIcons(ThemeFiles.inner(Arrays.asList("themes", "p1", "icons"))));
        assertTrue(ThemeFiles.isFonts(ThemeFiles.inner(Arrays.asList("themes", "p1", "fonts"))));
        assertTrue(ThemeFiles.isCollection(ThemeFiles.inner(Arrays.asList("themes", "p1", "wallpapers"))));
        assertEquals("", ThemeFiles.prefix(Collections.singletonList("icons")));
    }
}
