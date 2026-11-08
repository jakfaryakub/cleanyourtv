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

    /** A PNG start as tools/logo.py --shadow writes it: signature, IHDR, then the margin's tEXt chunk. */
    private static byte[] pngWithMargin(String text) {
        byte[] sig = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        byte[] ihdr = chunk("IHDR", new byte[13]);
        byte[] t = chunk("tEXt", text.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        byte[] idat = chunk("IDAT", new byte[4]);
        byte[] out = new byte[sig.length + ihdr.length + t.length + idat.length];
        System.arraycopy(sig, 0, out, 0, sig.length);
        System.arraycopy(ihdr, 0, out, sig.length, ihdr.length);
        System.arraycopy(t, 0, out, sig.length + ihdr.length, t.length);
        System.arraycopy(idat, 0, out, sig.length + ihdr.length + t.length, idat.length);
        return out;
    }

    private static byte[] chunk(String type, byte[] body) {
        byte[] c = new byte[12 + body.length];
        c[0] = (byte) (body.length >>> 24); c[1] = (byte) (body.length >>> 16);
        c[2] = (byte) (body.length >>> 8); c[3] = (byte) body.length;
        System.arraycopy(type.getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, c, 4, 4);
        System.arraycopy(body, 0, c, 8, body.length);
        return c;   // the CRC is not checked
    }

    @Test public void theShadowMarginIsReadFromItsChunk() {
        assertEquals(27, ThemeIcons.shadowMargin(pngWithMargin(ThemeIcons.SHADOW_KEY + "\u000027")));
        assertEquals(0, ThemeIcons.shadowMargin(pngWithMargin("Software\u0000rsvg")));
        assertEquals(0, ThemeIcons.shadowMargin(pngWithMargin(ThemeIcons.SHADOW_KEY + "\u0000lots")));
        assertEquals(0, ThemeIcons.shadowMargin(pngWithMargin(ThemeIcons.SHADOW_KEY + "\u00009999")));
        assertEquals(0, ThemeIcons.shadowMargin(new byte[0]));
        assertEquals(0, ThemeIcons.shadowMargin(null));
    }

    @Test public void aMarginMustLeaveSomeLogo() {
        assertEquals(27, ThemeIcons.usableMargin(27, 414, 144));
        assertEquals(0, ThemeIcons.usableMargin(72, 414, 144));
        assertEquals(0, ThemeIcons.usableMargin(-1, 414, 144));
    }

    @Test public void theLogoNotItsMarginFitsTheBox() {
        // 360 x 90 logo in a 27 px margin, box 250 x 85: the logo is scaled to 250 x 62 and its margin to 19 px.
        assertArrayEquals(new int[]{288, 100}, ThemeIcons.fullSize(414, 144, 27, 250, 85));
        assertArrayEquals(new int[]{250, 62}, ThemeIcons.fullSize(360, 90, 0, 250, 85));
    }

    @Test public void theShadowSpillsIntoTheTilesMargins() {
        // A 280 x 157 tile leaves 28 px and 39 px around its 224 x 78.5 box; a 27 px margin on a 360 px logo drawn
        // 224 px wide is about 17 px, taken off the padding.
        assertArrayEquals(new int[]{28, 39}, AppAdapter.logoPadding(280, 157, 360, 90, 0));
        assertArrayEquals(new int[]{11, 22}, AppAdapter.logoPadding(280, 157, 360, 90, 27));
        assertArrayEquals(new int[]{0, 0}, AppAdapter.logoPadding(280, 157, 10, 10, 200));
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
