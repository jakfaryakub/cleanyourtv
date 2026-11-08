package com.gathin.tvhome;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.json.JSONObject;

/**
 * Which theme Home uses: a slot (themes/<slug>/), the root (legacy) theme, or the built-in config.xml, from the
 * root's active_theme file (docs/PLAN.md, Phase 3). Only the pointer changes on a switch; nothing is copied.
 */
final class ThemeSlots {
    /** Whether a slot holds a theme Home can read: its config.json is there, at most 64 KB, and parses. */
    interface Usable {
        boolean test(String slug);
    }

    private ThemeSlots() {}

    /**
     * ThemeFiles.LEGACY for the root theme, a slot's slug, or null for config.xml.
     * <ul>
     *   <li>no pointer (null): the root, as before slots existed, whatever it holds;</li>
     *   <li>BUILTIN: config.xml, even when the root has a config.json;</li>
     *   <li>LEGACY: the root when it has a config.json, else config.xml;</li>
     *   <li>a usable slot: that slot;</li>
     *   <li>anything else (a bad slug, a slot that is gone or broken): the root when it has a config.json, else
     *       config.xml.</li>
     * </ul>
     */
    static String resolve(String pointer, boolean rootHasConfig, Usable usable) {
        if (pointer == null) return ThemeFiles.LEGACY;
        if (ThemeFiles.BUILTIN.equals(pointer)) return null;
        if (ThemeFiles.slug(pointer) && usable != null && usable.test(pointer)) return pointer;
        return rootHasConfig ? ThemeFiles.LEGACY : null;
    }

    /** The pointer's value: the first line, trimmed; null when the file is absent, empty or longer than 64 bytes. */
    static String pointer(File file) {
        if (file == null || !file.isFile() || file.length() == 0 || file.length() > 64) return null;
        try (InputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int n = in.read(bytes);
            return n > 0 ? parse(new String(bytes, 0, n, StandardCharsets.US_ASCII)) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    static String parse(String text) {
        if (text == null) return null;
        int nl = text.indexOf('\n');
        String v = (nl >= 0 ? text.substring(0, nl) : text).trim();
        return v.isEmpty() ? null : v;
    }

    /** The folder for a resolved value under root: themes/<slug>, the root itself, or null for config.xml. */
    static File folder(File root, String resolved) {
        if (root == null || resolved == null) return null;
        if (ThemeFiles.LEGACY.equals(resolved)) return root;
        return new File(new File(root, ThemeFiles.THEMES), resolved);
    }

    /** One theme on the TV, for the picker and tools/theme.sh --list. */
    static final class Info {
        /** A slot's slug, or ThemeFiles.LEGACY for the root theme. */
        final String id;
        /** config.json theme_name (at most 40 characters), or the id. */
        final String name;
        final long bytes;
        /** wallpaper_animated is on and the theme has a wallpaper.webp. */
        final boolean animated;
        /** config.json is there and parses: Home can use it. */
        final boolean usable;
        final File folder;

        Info(String id, String name, long bytes, boolean animated, boolean usable, File folder) {
            this.id = id;
            this.name = name;
            this.bytes = bytes;
            this.animated = animated;
            this.usable = usable;
            this.folder = folder;
        }
    }

    static final int MAX_NAME = 40;

    /** The display name: theme_name trimmed and cut to MAX_NAME, or the id when there is none. */
    static String name(String themeName, String id) {
        String v = themeName == null ? "" : themeName.trim();
        if (v.isEmpty()) return id;
        return v.length() > MAX_NAME ? v.substring(0, MAX_NAME) : v;
    }

    /** The slots under root, sorted by slug, then the root theme when it has a config.json. */
    static List<Info> list(File root) {
        List<Info> out = new ArrayList<>();
        if (root == null) return out;
        String[] slugs = new File(root, ThemeFiles.THEMES).list();
        if (slugs != null) {
            Arrays.sort(slugs);
            for (String slug : slugs) {
                File folder = new File(new File(root, ThemeFiles.THEMES), slug);
                if (ThemeFiles.slug(slug) && folder.isDirectory()) out.add(info(slug, folder));
            }
        }
        if (new File(root, Config.FILE_NAME).isFile()) out.add(info(ThemeFiles.LEGACY, root));
        return out;
    }

    private static Info info(String id, File folder) {
        JSONObject json = Config.readTheme(new File(folder, Config.FILE_NAME));
        boolean animated = json != null && json.optBoolean("wallpaper_animated", false)
                && new File(folder, AnimatedWallpaper.FILE_NAME).isFile();
        return new Info(id, name(json == null ? null : json.optString("theme_name", ""), id),
                ThemeFiles.LEGACY.equals(id) ? rootBytes(folder) : bytes(folder, 0), animated, json != null, folder);
    }

    /** The size of a slot: its files and those one folder down (wallpapers/, fonts/, icons/). */
    static long bytes(File folder, int depth) {
        File[] files = folder.listFiles();
        long n = 0;
        if (files == null) return 0;
        for (File f : files) {
            if (f.isFile()) n += f.length();
            else if (depth < 1 && f.isDirectory()) n += bytes(f, depth + 1);
        }
        return n;
    }

    /** The root theme's size: its theme files and folders only, not themes/ or the pointer. */
    private static long rootBytes(File root) {
        long n = 0;
        for (String name : new String[] {Config.FILE_NAME, Config.WALLPAPER_NAME, AnimatedWallpaper.FILE_NAME}) {
            n += new File(root, name).length();
        }
        for (String dir : new String[] {ThemeFiles.WALLPAPERS, ThemeFiles.FONTS, ThemeFiles.ICONS}) {
            File d = new File(root, dir);
            if (d.isDirectory()) n += bytes(d, 1);
        }
        return n;
    }

    /** The id Home uses now: a slug, ThemeFiles.LEGACY, or ThemeFiles.BUILTIN. */
    static String active(File root, File dir) {
        if (dir == null) return ThemeFiles.BUILTIN;
        return dir.equals(root) ? ThemeFiles.LEGACY : dir.getName();
    }
}
