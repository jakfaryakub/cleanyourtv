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
 *
 * A logo from tools/logo.py --shadow carries a soft shadow in a transparent margin around it and names that margin
 * in a tEXt chunk ({@link #SHADOW_KEY}). The box is then the logo's own: the picture is decoded a margin larger, and
 * {@link Logo#margin} says how much of it to let spill past the box.
 */
final class ThemeIcons {
    /** The tEXt keyword tools/logo.py writes: the shadow's margin on each side, in px of the PNG. */
    static final String SHADOW_KEY = "cleanyourtv-shadow-margin";
    /** How much of the file's start is read for the margin chunk; logo.py writes it right after IHDR. */
    private static final int HEAD_BYTES = 1024;

    /** A decoded logo and the transparent shadow margin around it, in px of this bitmap (0 without a shadow). */
    static final class Logo {
        final Bitmap bitmap;
        final int margin;

        Logo(Bitmap bitmap, int margin) {
            this.bitmap = bitmap;
            this.margin = margin;
        }

        int contentWidth() {
            return Math.max(1, bitmap.getWidth() - 2 * margin);
        }

        int contentHeight() {
            return Math.max(1, bitmap.getHeight() - 2 * margin);
        }
    }

    static final int MAX_BYTES = 512 * 1024;
    static final int MAX_SIDE = 4096;
    /** Enough for every dock app and a few more; a theme with more logos decodes the rest again when they scroll in. */
    static final int CACHE_SIZE = 24;
    private static final String TAG = "ThemeIcons";
    /** The last 12 bytes of every whole PNG: the empty IEND chunk and its CRC. */
    private static final byte[] IEND = {0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xAE, 0x42, 0x60, (byte) 0x82};

    private static final Map<String, Logo> cache = new LinkedHashMap<String, Logo>(CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Logo> eldest) {
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

    /** The logo, its own area fitted inside maxW x maxH px, or null to keep the tile's own artwork. */
    static synchronized Logo get(File f, int maxW, int maxH) {
        if (f == null || maxW <= 0 || maxH <= 0) return null;
        String key = key(f.getPath(), f.length(), f.lastModified(), maxW, maxH);
        if (cache.containsKey(key)) return cache.get(key);   // a file that failed stays null until it changes
        Logo logo = null;
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

    private static Logo decode(File f, int maxW, int maxH) {
        if (!f.isFile() || f.length() <= 0 || f.length() > MAX_BYTES || !whole(f)) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), bounds);
        if (!accepted(bounds.outWidth, bounds.outHeight, bounds.outMimeType)) return null;
        int margin = usableMargin(shadowMargin(head(f)), bounds.outWidth, bounds.outHeight);
        // The box is for the logo; the whole picture, margin included, is decoded at the logo's scale.
        int[] full = fullSize(bounds.outWidth, bounds.outHeight, margin, maxW, maxH);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, full[0], full[1]);
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;   // keep the transparency around the letters
        Bitmap bmp = BitmapFactory.decodeFile(f.getPath(), opts);
        if (bmp == null) return null;
        int[] size = fit(bmp.getWidth(), bmp.getHeight(), full[0], full[1]);
        if (size[0] != bmp.getWidth() || size[1] != bmp.getHeight()) {
            Bitmap scaled = Bitmap.createScaledBitmap(bmp, size[0], size[1], true);
            if (scaled != bmp) bmp.recycle();
            bmp = scaled;
        }
        // As WallpaperPlayer does: a hardware copy is uploaded once and leaves the Java heap.
        int scaledMargin = Math.round(margin * size[0] / (float) bounds.outWidth);
        Bitmap hardware = bmp.copy(Bitmap.Config.HARDWARE, false);
        if (hardware == null) return new Logo(bmp, scaledMargin);
        bmp.recycle();
        return new Logo(hardware, scaledMargin);
    }

    /** The whole picture's size when its logo, inside the margin, is fitted to maxW x maxH. */
    static int[] fullSize(int width, int height, int margin, int maxW, int maxH) {
        int[] logo = fit(width - 2 * margin, height - 2 * margin, maxW, maxH);
        int m = Math.round(margin * logo[0] / (float) Math.max(1, width - 2 * margin));
        return new int[]{logo[0] + 2 * m, logo[1] + 2 * m};
    }

    /** A margin that leaves some logo inside it, or 0 for one that would not. */
    static int usableMargin(int margin, int width, int height) {
        return margin > 0 && 2 * margin < width && 2 * margin < height ? margin : 0;
    }

    private static byte[] head(File f) {
        try (RandomAccessFile in = new RandomAccessFile(f, "r")) {
            byte[] head = new byte[(int) Math.min(HEAD_BYTES, in.length())];
            in.readFully(head);
            return head;
        } catch (IOException e) {
            return new byte[0];
        }
    }

    /**
     * The shadow margin a PNG names in its {@link #SHADOW_KEY} tEXt chunk, or 0. Reads the chunks before the image
     * data; anything malformed is 0.
     */
    static int shadowMargin(byte[] png) {
        if (png == null || png.length < 8) return 0;
        int at = 8;
        while (at + 8 <= png.length) {
            int length = ((png[at] & 0xFF) << 24) | ((png[at + 1] & 0xFF) << 16) | ((png[at + 2] & 0xFF) << 8)
                    | (png[at + 3] & 0xFF);
            String type = new String(png, at + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if (length < 0 || "IDAT".equals(type) || at + 8 + length > png.length) return 0;
            if ("tEXt".equals(type)) {
                String text = new String(png, at + 8, length, java.nio.charset.StandardCharsets.ISO_8859_1);
                int nul = text.indexOf('\0');
                if (nul > 0 && SHADOW_KEY.equals(text.substring(0, nul))) {
                    try {
                        int margin = Integer.parseInt(text.substring(nul + 1).trim());
                        return margin >= 0 && margin <= 256 ? margin : 0;
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                }
            }
            at += 12 + length;
        }
        return 0;
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
