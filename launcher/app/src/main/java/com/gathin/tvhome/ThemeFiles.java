package com.gathin.tvhome;

import java.util.List;
import java.util.regex.Pattern;

/**
 * The files ThemeProvider may write or delete under Config.root, by the URI's path segments: the root (legacy)
 * theme, the active_theme pointer, and the same theme files inside a slot, themes/<slug>/.
 */
final class ThemeFiles {
    static final String WALLPAPERS = "wallpapers";
    /** The theme's font files (config.json "font"); never bundled in the APK. */
    static final String FONTS = "fonts";
    /** The theme's app logos (config.json "app_logos"), one per app: icons/<package>.png; never bundled in the APK. */
    static final String ICONS = "icons";
    /** The folder that holds one whole theme per slot: themes/<slug>/. */
    static final String THEMES = "themes";
    /** The root file naming the theme in use: a slot's slug, LEGACY or BUILTIN. */
    static final String ACTIVE = "active_theme";
    /** active_theme value for the root theme, the one tools/theme.sh <folder> pushes. */
    static final String LEGACY = "legacy";
    /** active_theme value for the built-in config.xml, even when the root has a config.json. */
    static final String BUILTIN = "builtin";
    /** The names tools/theme.sh accepts for a collection image, and the types WallpaperRotation reads. */
    private static final Pattern IMAGE = Pattern.compile("[A-Za-z0-9_.-]+\\.(jpg|jpeg|png|webp)",
            Pattern.CASE_INSENSITIVE);
    /** The names tools/theme.sh accepts for a theme font, and the types ThemeFont loads. */
    private static final Pattern FONT = Pattern.compile("[A-Za-z0-9_.-]+\\.(ttf|otf)", Pattern.CASE_INSENSITIVE);

    /** An app logo is named after the package it stands for: two or more Java identifiers joined by dots. */
    private static final Pattern ICON = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+\\.(png)",
            Pattern.CASE_INSENSITIVE);

    /** A slot name; declared after IMAGE, whose Pattern the tests read as the first one in this file. */
    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9-]{0,31}");

    private ThemeFiles() {}

    /** A slot name: lower-case letters, digits and '-', at most 32, never LEGACY or BUILTIN. */
    static boolean slug(String name) {
        return name != null && SLUG.matcher(name).matches() && !LEGACY.equals(name) && !BUILTIN.equals(name);
    }

    /** True when the segments name a file inside a slot: themes/<slug>/... */
    private static boolean inSlot(List<String> segments) {
        return segments != null && segments.size() >= 3 && THEMES.equals(segments.get(0)) && slug(segments.get(1));
    }

    /** "themes/<slug>/" for a path inside a slot, "" for the root theme. */
    static String prefix(List<String> segments) {
        return inSlot(segments) ? THEMES + "/" + segments.get(1) + "/" : "";
    }

    /** The segments under the slot (or all of them for the root theme), for isCollection, isFonts and isIcons. */
    static List<String> inner(List<String> segments) {
        return inSlot(segments) ? segments.subList(2, segments.size()) : segments;
    }

    /** The slug for themes/<slug> alone (a delete of it removes the slot), or null. */
    static String slot(List<String> segments) {
        return segments != null && segments.size() == 2 && THEMES.equals(segments.get(0)) && slug(segments.get(1))
                ? segments.get(1) : null;
    }

    /** True for "themes" alone: a query of it lists the slots. */
    static boolean isThemes(List<String> segments) {
        return segments != null && segments.size() == 1 && THEMES.equals(segments.get(0));
    }

    /**
     * The path relative to Config.root, e.g. "config.json", "wallpapers/dusk.jpg" or "themes/red-eyes/config.json",
     * or null if refused. theme_revision and active_theme belong to the root only.
     */
    static String path(List<String> segments) {
        if (segments == null) return null;
        if (inSlot(segments)) {
            String inner = themePath(inner(segments), false);
            return inner == null ? null : prefix(segments) + inner;
        }
        if (segments.size() == 1 && ACTIVE.equals(segments.get(0))) return ACTIVE;
        return themePath(segments, true);
    }

    private static String themePath(List<String> segments, boolean root) {
        if (segments.size() == 1) {
            String name = segments.get(0);
            return Config.FILE_NAME.equals(name) || "wallpaper.jpg".equals(name)
                    || (root && "theme_revision".equals(name))
                    || AnimatedWallpaper.FILE_NAME.equals(name) ? name : null;
        }
        if (segments.size() == 2 && WALLPAPERS.equals(segments.get(0))
                && (collectionImage(segments.get(1)) || WallpaperCaptions.FILE_NAME.equals(segments.get(1)))) {
            return WALLPAPERS + "/" + segments.get(1);
        }
        if (segments.size() == 2 && FONTS.equals(segments.get(0)) && fontFile(segments.get(1))) {
            return FONTS + "/" + segments.get(1);
        }
        if (segments.size() == 2 && ICONS.equals(segments.get(0)) && iconFile(segments.get(1))) {
            return ICONS + "/" + segments.get(1);
        }
        return null;
    }

    /** True for "wallpapers" alone: a delete of it clears the collection's images. */
    static boolean isCollection(List<String> segments) {
        return segments != null && segments.size() == 1 && WALLPAPERS.equals(segments.get(0));
    }

    /** True for "fonts" alone: a delete of it clears the theme's fonts. */
    static boolean isFonts(List<String> segments) {
        return segments != null && segments.size() == 1 && FONTS.equals(segments.get(0));
    }

    /** True for "icons" alone: a delete of it clears the theme's app logos. */
    static boolean isIcons(List<String> segments) {
        return segments != null && segments.size() == 1 && ICONS.equals(segments.get(0));
    }

    /** icons/<package>.png: the file name of an app logo, e.g. com.netflix.ninja.png. */
    static boolean iconFile(String name) {
        return name != null && ICON.matcher(name).matches();
    }

    /** The logo file name for a package, or null for a package name that cannot be one. */
    static String iconName(String pkg) {
        String name = pkg + ".png";
        return iconFile(name) ? name : null;
    }

    static boolean fontFile(String name) {
        return name != null && !name.startsWith(".") && !name.contains("..") && FONT.matcher(name).matches();
    }

    static boolean collectionImage(String name) {
        return name != null && !name.startsWith(".") && IMAGE.matcher(name).matches();
    }
}
