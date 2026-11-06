package com.gathin.tvhome;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * What ThemeFont needs to know about a font file before Android sees it: that it is a TrueType or OpenType file,
 * and the range of its weight axis if it is a variable font. Reads the table directory and the fvar table only,
 * never the whole file. Plain Java, so it runs in the JVM unit tests.
 */
final class FontHeader {
    /** No weight axis: a static font, drawn at its one weight for every weight the launcher asks for. */
    static final int[] STATIC = new int[0];

    private FontHeader() {}

    /** True for the first four bytes of a TrueType (0x00010000, "true") or OpenType ("OTTO") file. */
    static boolean isFont(int tag) {
        return tag == 0x00010000 || tag == 0x74727565 || tag == 0x4F54544F;
    }

    /**
     * {min, max} of the font's wght axis, {@link #STATIC} for a font without one, or null for a file that is not
     * a font or whose tables do not add up.
     */
    static int[] weightRange(RandomAccessFile f) throws IOException {
        long length = f.length();
        if (length < 12) return null;
        f.seek(0);
        if (!isFont(f.readInt())) return null;
        int tables = f.readUnsignedShort();
        if (tables == 0 || 12 + 16L * tables > length) return null;
        long fvar = -1, fvarLength = 0;
        for (int i = 0; i < tables; i++) {
            f.seek(12 + 16L * i);
            int tag = f.readInt();
            f.readInt();   // checksum
            long offset = f.readInt() & 0xFFFFFFFFL, size = f.readInt() & 0xFFFFFFFFL;
            if (offset + size > length) return null;
            if (tag == 0x66766172) { fvar = offset; fvarLength = size; }   // "fvar"
        }
        if (fvar < 0) return STATIC;
        if (fvarLength < 16) return null;
        f.seek(fvar + 4);
        int axesOffset = f.readUnsignedShort();
        f.readUnsignedShort();   // reserved
        int axes = f.readUnsignedShort(), axisSize = f.readUnsignedShort();
        if (axisSize < 20 || axesOffset + (long) axes * axisSize > fvarLength) return null;
        for (int i = 0; i < axes; i++) {
            f.seek(fvar + axesOffset + (long) i * axisSize);
            int tag = f.readInt();
            int min = f.readInt(), def = f.readInt(), max = f.readInt();
            if (tag == 0x77676874) {   // "wght"
                return new int[] {Math.round(min / 65536f), Math.round(max / 65536f)};
            }
        }
        return STATIC;
    }

    /** The weight to ask a font for: the launcher's weight, kept inside the font's own axis range. */
    static int clamp(int weight, int[] range) {
        if (range == null || range.length < 2) return weight;
        return Math.max(range[0], Math.min(range[1], weight));
    }
}
