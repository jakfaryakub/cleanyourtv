package com.gathin.tvhome;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * Lets tools/theme.sh write the theme files into {@link Config#root} on firmware where the adb shell is
 * locked out of Android/data (TCL G07, Android 11). Only the files {@link ThemeFiles} allows, and only for
 * callers holding WRITE_SECURE_SETTINGS, which the adb shell has and ordinary apps cannot get.
 *
 *   content write --uri content://com.gathin.tvhome.theme/config.json < /data/local/tmp/config.json
 *   content write --uri content://com.gathin.tvhome.theme/wallpapers/dusk.jpg < /data/local/tmp/dusk.jpg
 *   content write --uri content://com.gathin.tvhome.theme/themes/red-eyes/config.json < ...   (a slot)
 *   content write --uri content://com.gathin.tvhome.theme/active_theme < ...    (a slug, legacy or builtin)
 *   content delete --uri content://com.gathin.tvhome.theme/config.json
 *   content delete --uri content://com.gathin.tvhome.theme/wallpapers      (every image in the collection)
 *   content delete --uri content://com.gathin.tvhome.theme/icons           (every app logo)
 *   content delete --uri content://com.gathin.tvhome.theme/themes/red-eyes (the whole slot, unless in use)
 *   content query --uri content://com.gathin.tvhome.theme/themes           (the themes on the TV)
 */
public class ThemeProvider extends ContentProvider {
    static final String[] COLUMNS = {"id", "name", "bytes", "animated", "usable", "active"};

    private File file(Uri uri) throws FileNotFoundException {
        String path = ThemeFiles.path(uri.getPathSegments());
        File root = Config.root(getContext());
        if (root == null || path == null) throw new FileNotFoundException(String.valueOf(uri));
        File f = new File(root, path);
        if (!ThemeFiles.inside(root, f)) throw new FileNotFoundException(String.valueOf(uri));
        File parent = f.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new FileNotFoundException(String.valueOf(uri));
        return f;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = file(uri);
        if (mode.contains("w")) {
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        File root = Config.root(getContext());
        if (root == null) return 0;
        List<String> segments = uri.getPathSegments();
        String slot = ThemeFiles.slot(segments);
        if (slot != null) return deleteSlot(root, slot);
        File base = new File(root, ThemeFiles.prefix(segments));
        List<String> inner = ThemeFiles.inner(segments);
        if (ThemeFiles.isCollection(inner)) return clear(base, ThemeFiles.WALLPAPERS, f -> ThemeFiles.collectionImage(f.getName()));
        if (ThemeFiles.isFonts(inner)) return clear(base, ThemeFiles.FONTS, f -> ThemeFiles.fontFile(f.getName()));
        if (ThemeFiles.isIcons(inner)) return clear(base, ThemeFiles.ICONS, f -> ThemeFiles.iconFile(f.getName()));
        try {
            return file(uri).delete() ? 1 : 0;
        } catch (FileNotFoundException e) {
            return 0;
        }
    }

    private static int clear(File base, String folder, FileFilter allowed) {
        File[] files = new File(base, folder).listFiles(allowed);
        int n = 0;
        if (files != null) for (File f : files) if (f.isFile() && f.delete()) n++;
        return n;
    }

    /** Removes a slot's theme files, then its empty folders; refused (0) for the slot active_theme names. */
    private int deleteSlot(File root, String slug) {
        if (slug.equals(ThemeSlots.pointer(new File(root, ThemeFiles.ACTIVE)))) return 0;
        File slot = new File(new File(root, ThemeFiles.THEMES), slug);
        if (!slot.isDirectory() || !ThemeFiles.inside(root, slot)) return 0;
        int n = 0;
        for (String name : new String[] {Config.FILE_NAME, Config.WALLPAPER_NAME, AnimatedWallpaper.FILE_NAME}) {
            if (new File(slot, name).delete()) n++;
        }
        n += clear(slot, ThemeFiles.WALLPAPERS, f -> ThemeFiles.collectionImage(f.getName())
                || WallpaperCaptions.FILE_NAME.equals(f.getName()));
        n += clear(slot, ThemeFiles.FONTS, f -> ThemeFiles.fontFile(f.getName()));
        n += clear(slot, ThemeFiles.ICONS, f -> ThemeFiles.iconFile(f.getName()));
        for (String dir : new String[] {ThemeFiles.WALLPAPERS, ThemeFiles.FONTS, ThemeFiles.ICONS}) {
            //noinspection ResultOfMethodCallIgnored
            new File(slot, dir).delete();   // only when empty
        }
        //noinspection ResultOfMethodCallIgnored
        slot.delete();
        return n;
    }

    @Override
    public Cursor query(Uri uri, String[] p, String s, String[] a, String o) {
        if (!ThemeFiles.isThemes(uri.getPathSegments())) return null;
        File root = Config.root(getContext());
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        if (root == null) return cursor;
        String active = ThemeSlots.active(root, Config.dir(getContext()));
        for (ThemeSlots.Info t : ThemeSlots.list(root)) {
            cursor.addRow(new Object[] {t.id, t.name, t.bytes, t.animated ? 1 : 0, t.usable ? 1 : 0,
                    t.id.equals(active) ? 1 : 0});
        }
        if (ThemeFiles.BUILTIN.equals(active)) cursor.addRow(new Object[] {ThemeFiles.BUILTIN, "Built-in", 0, 0, 1, 1});
        return cursor;
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
