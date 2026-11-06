package com.gathin.tvhome;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The parts of the theme font that need no Android: the key, the file check, the weight axis and the weights. */
public class ThemeFontTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void theKeyTakesOnlyANameThemeFilesWouldPush() {
        assertEquals("plus-jakarta-sans.ttf", Config.fontName("plus-jakarta-sans.ttf"));
        assertEquals("Inter.OTF", Config.fontName("Inter.OTF"));
        assertEquals("", Config.fontName(""));
        assertEquals("", Config.fontName(null));
        assertEquals("", Config.fontName("../inter.ttf"));
        assertEquals("", Config.fontName("fonts/inter.ttf"));
        assertEquals("", Config.fontName("Inter[wght].ttf"));
        assertEquals("", Config.fontName("inter.woff2"));
    }

    @Test public void aVariableFontGivesItsWeightRange() throws IOException {
        assertArrayEquals(new int[] {200, 800}, range(font(0x00010000, "wght", 200, 400, 800)));
        assertArrayEquals(new int[] {100, 900}, range(font(0x4F54544F, "wght", 100, 400, 900)));
    }

    @Test public void theWeightAxisIsFoundBehindAnother() throws IOException {
        assertArrayEquals(new int[] {100, 900}, range(font(0x00010000, "opsz", 14, 14, 32, "wght", 100, 400, 900)));
    }

    @Test public void aFontWithoutAWeightAxisIsStatic() throws IOException {
        assertSame(FontHeader.STATIC, range(font(0x00010000)));
        assertSame(FontHeader.STATIC, range(font(0x00010000, "opsz", 14, 14, 32)));
    }

    @Test public void aFileThatIsNotAFontIsRefused() throws IOException {
        assertNull(range(write(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0})));
        assertNull(range(write(new byte[] {0, 1, 0, 0})));
        assertNull(range(write(new byte[0])));
        assertFalse(FontHeader.isFont(0x774F4646));   // "wOFF"
        assertTrue(FontHeader.isFont(0x74727565));    // "true"
    }

    @Test public void tablesThatRunPastTheFileAreRefused() throws IOException {
        byte[] good = font(0x00010000, "wght", 200, 400, 800);
        byte[] cut = new byte[good.length - 8];
        System.arraycopy(good, 0, cut, 0, cut.length);
        assertNull(range(write(cut)));
    }

    @Test public void weightsStayInsideTheFontsRange() {
        int[] manrope = {200, 800};
        assertEquals(200, FontHeader.clamp(200, manrope));
        assertEquals(500, FontHeader.clamp(500, manrope));
        assertEquals(300, FontHeader.clamp(200, new int[] {300, 700}));
        assertEquals(700, FontHeader.clamp(900, new int[] {300, 700}));
        assertEquals(300, FontHeader.clamp(300, FontHeader.STATIC));
    }

    private int[] range(byte[] bytes) throws IOException {
        return range(write(bytes));
    }

    private int[] range(File f) throws IOException {
        try (RandomAccessFile in = new RandomAccessFile(f, "r")) {
            return FontHeader.weightRange(in);
        }
    }

    private File write(byte[] bytes) throws IOException {
        File f = tmp.newFile();
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(bytes);
        }
        return f;
    }

    /** A font file with one table, fvar, holding these axes as (tag, min, default, max) groups; no axes: no fvar. */
    private static byte[] font(int sfnt, Object... axes) {
        int count = axes.length / 4;
        int tables = count == 0 ? 1 : 1;
        int dir = 12 + 16 * tables;
        int fvarLength = 16 + 20 * count;
        ByteBuffer b = ByteBuffer.allocate(dir + Math.max(fvarLength, 4));
        b.putInt(sfnt).putShort((short) tables).putShort((short) 0).putShort((short) 0).putShort((short) 0);
        b.putInt(count == 0 ? 0x68656164 : 0x66766172).putInt(0).putInt(dir).putInt(count == 0 ? 4 : fvarLength);
        if (count == 0) return b.array();
        b.putShort((short) 1).putShort((short) 0).putShort((short) 16).putShort((short) 2)
                .putShort((short) count).putShort((short) 20).putShort((short) 0).putShort((short) 0);
        for (int i = 0; i < count; i++) {
            String tag = (String) axes[4 * i];
            b.put(tag.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            for (int j = 1; j <= 3; j++) b.putInt((Integer) axes[4 * i + j] << 16);
            b.putShort((short) 0).putShort((short) 0);
        }
        return b.array();
    }
}
