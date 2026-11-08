package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Small pictures of each theme for the theme picker (docs/PLAN.md, Phase 3): the theme's wallpaper.jpg (or the first
 * frame of its wallpaper.webp) scaled down once to WIDTH x HEIGHT and kept as a JPEG in the cache, so opening the
 * picker again decodes only a few KB per theme. A new wallpaper has another size or time, so another file name.
 * Never called on the UI thread.
 */
final class ThemeThumbs {
    private static final String TAG = "TvHome.ThemeThumbs";
    static final int WIDTH = 320;
    static final int HEIGHT = 180;
    private static final String DIR = "theme-thumbs";

    private ThemeThumbs() {}

    /** The cache file name for a picture: id, length and time, so a changed wallpaper never shows a stale one. */
    static String name(String id, long length, long modified) {
        return id + "-" + length + "-" + modified + ".jpg";
    }

    /** The centred part of a width x height picture with the 16:9 shape of a card: left, top, right, bottom. */
    static int[] crop(int width, int height) {
        if (width <= 0 || height <= 0) return new int[] {0, 0, Math.max(width, 0), Math.max(height, 0)};
        long wide = (long) width * HEIGHT, tall = (long) height * WIDTH;
        if (wide > tall) {   // wider than 16:9: cut the sides
            int w = (int) (tall / HEIGHT);
            int left = (width - w) / 2;
            return new int[] {left, 0, left + w, height};
        }
        int h = (int) (wide / WIDTH);
        int top = (height - h) / 2;
        return new int[] {0, top, width, top + h};
    }

    /** The picture a theme's card shows: wallpaper.jpg, else wallpaper.webp, else null (a plain card). */
    static File source(File folder) {
        if (folder == null) return null;
        File jpg = new File(folder, Config.WALLPAPER_NAME);
        if (jpg.isFile() && jpg.length() > 0) return jpg;
        File webp = new File(folder, AnimatedWallpaper.FILE_NAME);
        return webp.isFile() && webp.length() > 0 ? webp : null;
    }

    /** The theme's card picture, from the cache or made now; null when the theme has none or it cannot be read. */
    static Bitmap load(Context ctx, String id, File folder) {
        File src = source(folder);
        if (src == null) return null;
        File dir = new File(ctx.getCacheDir(), DIR);
        String name = name(id, src.length(), src.lastModified());
        File cached = new File(dir, name);
        if (cached.isFile()) {
            Bitmap b = BitmapFactory.decodeFile(cached.getPath());
            if (b != null) return b;
        }
        Bitmap thumb = make(src);
        if (thumb == null) return null;
        if (dir.isDirectory() || dir.mkdirs()) {
            File[] old = dir.listFiles((d, n) -> n.startsWith(id + "-") && !n.equals(name));
            if (old != null) for (File f : old) //noinspection ResultOfMethodCallIgnored
                f.delete();
            try (FileOutputStream out = new FileOutputStream(cached)) {
                thumb.compress(Bitmap.CompressFormat.JPEG, 80, out);
            } catch (IOException e) {
                Log.w(TAG, "could not cache " + name + ": " + e.getMessage());
            }
        }
        return thumb;
    }

    private static Bitmap make(File src) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(src.getPath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = ThemeIcons.sampleSize(bounds.outWidth, bounds.outHeight, WIDTH, HEIGHT);
        Bitmap full = BitmapFactory.decodeFile(src.getPath(), opts);   // a WebP gives its first frame
        if (full == null) return null;
        int[] c = crop(full.getWidth(), full.getHeight());
        Bitmap thumb = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        new Canvas(thumb).drawBitmap(full, new Rect(c[0], c[1], c[2], c[3]), new Rect(0, 0, WIDTH, HEIGHT),
                new Paint(Paint.FILTER_BITMAP_FLAG));
        full.recycle();
        return thumb;
    }
}
