package com.gathin.tvhome;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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
}
