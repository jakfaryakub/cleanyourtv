package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;
import android.util.SparseArray;

import java.io.File;
import java.io.RandomAccessFile;

/**
 * The theme's own font (config.json font): a .ttf or .otf in the theme's fonts/ folder, loaded once per file and
 * weight, never bundled in the APK. Anything wrong with it - no key, no file, a file over 2 MB, a file that is not
 * a font, a Typeface Android refuses - means no ThemeFont, and the launcher keeps Outfit; nothing here throws.
 *
 * The layouts ask for four weights through the font_* strings (res/values/fonts.xml); a variable font is built
 * at each of them inside its own wght range, a static font draws all four at its one weight.
 */
final class ThemeFont {
    static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final String TAG = "ThemeFont";

    private static ThemeFont current;
    private static String currentKey;

    private final File file;
    private final int[] range;
    private final SparseArray<Typeface> byWeight = new SparseArray<>();

    private ThemeFont(File file, int[] range) {
        this.file = file;
        this.range = range;
    }

    /**
     * The font the theme names, or null for none. Loaded again only when the file changes (a theme push writes a
     * new one), so every activity and the screensaver share one copy per process.
     */
    static synchronized ThemeFont load(Context ctx, Config cfg) {
        File dir = Config.dir(ctx);
        if (cfg == null || cfg.font.isEmpty() || dir == null) return set(null, null);
        File f = new File(new File(dir, ThemeFiles.FONTS), cfg.font);
        String key = f.getPath() + ':' + f.length() + ':' + f.lastModified();
        if (key.equals(currentKey)) return current;
        ThemeFont font = null;
        try {
            if (f.isFile() && f.length() <= MAX_BYTES) {
                int[] range;
                try (RandomAccessFile in = new RandomAccessFile(f, "r")) {
                    range = FontHeader.weightRange(in);
                }
                if (range != null) {
                    font = new ThemeFont(f, range);
                    if (font.get(400) == null) font = null;   // Android could not draw it: keep Outfit
                }
            }
        } catch (Exception | LinkageError e) {
            Log.w(TAG, "theme font " + cfg.font + " not loaded", e);
            font = null;
        }
        if (font == null) Log.w(TAG, "theme font " + cfg.font + " missing or unreadable, using Outfit");
        return set(font, key);
    }

    private static ThemeFont set(ThemeFont font, String key) {
        current = font;
        currentKey = key;
        return font;
    }

    /** The font loaded last, for views built in code (UiFont.get), or null for Outfit. */
    static synchronized ThemeFont current() {
        return current;
    }

    /** The typeface at this weight (200-500 from the font_* strings), or null if Android refuses the file. */
    synchronized Typeface get(int weight) {
        int w = FontHeader.clamp(weight, range);
        Typeface t = byWeight.get(w);
        if (t != null) return t;
        try {
            Typeface.Builder b = new Typeface.Builder(file).setWeight(w);
            if (range.length == 2) b.setFontVariationSettings("'wght' " + w);
            t = b.build();
        } catch (Exception e) {
            Log.w(TAG, "theme font at weight " + w + " not built", e);
            t = null;
        }
        if (t != null) byWeight.put(w, t);
        return t;
    }
}
