package com.gathin.tvhome;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ThemeIconsTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void aWideLogoFitsTheBoxByItsWidth() {
        assertArrayEquals(new int[]{250, 54}, ThemeIcons.fit(1469, 317, 250, 85));
    }

    @Test public void aTallLogoFitsTheBoxByItsHeight() {
        assertArrayEquals(new int[]{85, 85}, ThemeIcons.fit(512, 512, 250, 85));
    }

    @Test public void aSmallLogoIsNeverScaledUp() {
        assertArrayEquals(new int[]{120, 40}, ThemeIcons.fit(120, 40, 250, 85));
    }

    @Test public void aBrokenSizeStillGivesOnePixel() {
        assertArrayEquals(new int[]{1, 1}, ThemeIcons.fit(0, 40, 250, 85));
    }

    @Test public void sampleSizeNeverDropsBelowTheFittedSize() {
        assertEquals(1, ThemeIcons.sampleSize(360, 120, 250, 85));
        assertEquals(8, ThemeIcons.sampleSize(4000, 1000, 250, 85));   // 500 x 125 covers 250 x 63; 250 x 62 would not
        assertEquals(1, ThemeIcons.sampleSize(100, 30, 250, 85));
    }

    @Test public void onlyPngsOfASaneSizeAreDecoded() {
        assertTrue(ThemeIcons.accepted(360, 120, "image/png"));
        assertFalse(ThemeIcons.accepted(360, 120, "image/jpeg"));
        assertFalse(ThemeIcons.accepted(360, 120, null));
        assertFalse(ThemeIcons.accepted(5000, 120, "image/png"));
        assertFalse(ThemeIcons.accepted(0, 0, "image/png"));
    }

    @Test public void onlyAWholePngIsDrawn() {
        byte[] iend = {0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xAE, 0x42, 0x60, (byte) 0x82};
        byte[] longer = new byte[40];
        System.arraycopy(iend, 0, longer, 28, iend.length);
        assertTrue(ThemeIcons.endsWithIend(iend));
        assertTrue(ThemeIcons.endsWithIend(longer));
        assertFalse(ThemeIcons.endsWithIend(new byte[40]));
        assertFalse(ThemeIcons.endsWithIend(new byte[]{'I', 'E', 'N', 'D'}));
        assertFalse(ThemeIcons.endsWithIend(null));
    }

    @Test public void theBoxSizeIsPartOfTheCacheKey() {
        String small = ThemeIcons.key("/t/icons/a.b.png", 10, 1, 250, 85);
        assertNotEquals(small, ThemeIcons.key("/t/icons/a.b.png", 10, 1, 300, 85));
        assertNotEquals(small, ThemeIcons.key("/t/icons/a.b.png", 11, 1, 250, 85));
        assertNotEquals(small, ThemeIcons.key("/t/icons/a.b.png", 10, 2, 250, 85));
    }

    @Test public void theLogoFileIsFoundByPackage() throws Exception {
        File theme = tmp.newFolder("theme");
        assertNull(ThemeIcons.file(theme, "com.netflix.ninja"));
        File icons = new File(theme, ThemeFiles.ICONS);
        assertTrue(icons.mkdir());
        File logo = new File(icons, "com.netflix.ninja.png");
        assertTrue(logo.createNewFile());
        assertEquals(logo, ThemeIcons.file(theme, "com.netflix.ninja"));
        assertNull(ThemeIcons.file(theme, "com.vidio.android.tv"));
        assertNull(ThemeIcons.file(theme, "netflix"));
        assertNull(ThemeIcons.file(null, "com.netflix.ninja"));
    }

    @Test public void theLogoBoxFollowsTheTileAndItsZoom() {
        assertArrayEquals(new int[]{224, 79}, AppAdapter.logoBox(280, 157, 1f));
        assertArrayEquals(new int[]{246, 86}, AppAdapter.logoBox(280, 157, 1.10f));
    }
}
