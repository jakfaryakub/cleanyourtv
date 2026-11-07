package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The theme's app logos (config.json app_logos): icons/<package>.png in the theme folder, a transparent PNG that is
 * usually wider than tall, never bundled in the APK. Each logo is decoded once for the box it is drawn in, scaled
 * down to fit it, and kept in a small cache; the box size is part of the cache key, so a tile that changes size
 * gets a new copy instead of a blurred or oversized one. Anything wrong with a file - missing, over 512 KB, over
 * 4096 px on a side, cut short, not a PNG Android can read - means no logo, and the tile keeps its banner or icon; nothing
 * here throws.
 */
final class ThemeIcons {
    static final int MAX_BYTES = 512 * 1024;
    static final int MAX_SIDE = 4096;
    /** Enough for every dock app and a few more; a theme with more logos decodes the rest again when they scroll in. */
    static final int CACHE_SIZE = 24;
    private static final String TAG = "ThemeIcons";
    /** The last 12 bytes of every whole PNG: the empty IEND chunk and its CRC. */
    private static final byte[] IEND = {0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xAE, 0x42, 0x60, (byte) 0x82};

    private static final Map<String, Bitmap> cache = new LinkedHashMap<String, Bitmap>(CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    private ThemeIcons() {}

    /** The logo file for this package in the theme's icons/ folder, or null when there is none. */
    static File file(File themeDir, String pkg) {
        String name = pkg == null ? null : ThemeFiles.iconName(pkg);
        if (themeDir == null || name == null) return null;
        File f = new File(new File(themeDir, ThemeFiles.ICONS), name);
        return f.isFile() ? f : null;
    }

    /** The logo fitted inside maxW x maxH px, or null to keep the tile's own artwork. */
    static synchronized Bitmap get(File f, int maxW, int maxH) {
        if (f == null || maxW <= 0 || maxH <= 0) return null;
        String key = key(f.getPath(), f.length(), f.lastModified(), maxW, maxH);
        if (cache.containsKey(key)) return cache.get(key);   // a file that failed stays null until it changes
        Bitmap logo = null;
        try {
            logo = decode(f, maxW, maxH);
        } catch (Exception | OutOfMemoryError e) {
            Log.w(TAG, "logo " + f.getName() + " not loaded", e);
        }
        if (logo == null) Log.w(TAG, "logo " + f.getName() + " missing or unreadable, keeping the app's own artwork");
        cache.put(key, logo);
        return logo;
    }

    /** Lets go of every decoded logo (Home is in the background and memory is short). */
    static synchronized void clear() {
        cache.clear();
    }

    private static Bitmap decode(File f, int maxW, int maxH) {
        if (!f.isFile() || f.length() <= 0 || f.length() > MAX_BYTES || !whole(f)) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), bounds);
        if (!accepted(bounds.outWidth, bounds.outHeight, bounds.outMimeType)) return null;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxW, maxH);
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;   // keep the transparency around the letters
        Bitmap bmp = BitmapFactory.decodeFile(f.getPath(), opts);
        if (bmp == null) return null;
        int[] size = fit(bmp.getWidth(), bmp.getHeight(), maxW, maxH);
        if (size[0] != bmp.getWidth() || size[1] != bmp.getHeight()) {
            Bitmap scaled = Bitmap.createScaledBitmap(bmp, size[0], size[1], true);
            if (scaled != bmp) bmp.recycle();
            bmp = scaled;
        }
        // As WallpaperPlayer does: a hardware copy is uploaded once and leaves the Java heap.
        Bitmap hardware = bmp.copy(Bitmap.Config.HARDWARE, false);
        if (hardware == null) return bmp;
        bmp.recycle();
        return hardware;
    }

    /**
     * A PNG cut short (a push that stopped halfway) still decodes: Android fills the missing rows with nothing and
     * the tile shows an empty card. Only a file that ends in IEND is drawn.
     */
    private static boolean whole(File f) {
        try (RandomAccessFile in = new RandomAccessFile(f, "r")) {
            if (in.length() < IEND.length) return false;
            byte[] tail = new byte[IEND.length];
            in.seek(in.length() - IEND.length);
            in.readFully(tail);
            return endsWithIend(tail);
        } catch (IOException e) {
            return false;
        }
    }

    static boolean endsWithIend(byte[] tail) {
        return tail != null && tail.length >= IEND.length
                && Arrays.equals(IEND, Arrays.copyOfRange(tail, tail.length - IEND.length, tail.length));
    }

    static String key(String path, long length, long modified, int maxW, int maxH) {
        return path + ':' + length + ':' + modified + ':' + maxW + 'x' + maxH;
    }

    /** A PNG with a size Android reported, at most MAX_SIDE on each side. */
    static boolean accepted(int width, int height, String mime) {
        return "image/png".equals(mime) && width > 0 && height > 0 && width <= MAX_SIDE && height <= MAX_SIDE;
    }

    /** The largest power of two that still leaves the picture at least as big as its fitted size. */
    static int sampleSize(int width, int height, int maxW, int maxH) {
        int[] target = fit(width, height, maxW, maxH);
        int sample = 1;
        while (width / (sample * 2) >= target[0] && height / (sample * 2) >= target[1]) sample *= 2;
        return sample;
    }

    /** The picture's size scaled down, never up, to fit inside maxW x maxH with its shape kept; at least 1 x 1. */
    static int[] fit(int width, int height, int maxW, int maxH) {
        if (width <= 0 || height <= 0) return new int[]{1, 1};
        float scale = Math.min(1f, Math.min(maxW / (float) width, maxH / (float) height));
        return new int[]{Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale))};
    }
}
