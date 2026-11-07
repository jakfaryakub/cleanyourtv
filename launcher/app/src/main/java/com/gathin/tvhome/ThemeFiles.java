package com.gathin.tvhome;

import java.util.List;
import java.util.regex.Pattern;

/** The files ThemeProvider may write or delete under Config.dir, by the URI's path segments. */
final class ThemeFiles {
    static final String WALLPAPERS = "wallpapers";
    /** The theme's font files (config.json "font"); never bundled in the APK. */
    static final String FONTS = "fonts";
    /** The theme's app logos (config.json "app_logos"), one per app: icons/<package>.png; never bundled in the APK. */
    static final String ICONS = "icons";
    /** The names tools/theme.sh accepts for a collection image, and the types WallpaperRotation reads. */
    private static final Pattern IMAGE = Pattern.compile("[A-Za-z0-9_.-]+\\.(jpg|jpeg|png|webp)",
            Pattern.CASE_INSENSITIVE);
    /** The names tools/theme.sh accepts for a theme font, and the types ThemeFont loads. */
    private static final Pattern FONT = Pattern.compile("[A-Za-z0-9_.-]+\\.(ttf|otf)", Pattern.CASE_INSENSITIVE);

    /** An app logo is named after the package it stands for: two or more Java identifiers joined by dots. */
    private static final Pattern ICON = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+\\.(png)",
            Pattern.CASE_INSENSITIVE);

    private ThemeFiles() {}

    /** The path relative to the theme directory, e.g. "config.json" or "wallpapers/dusk.jpg", or null if refused. */
    static String path(List<String> segments) {
        if (segments == null) return null;
        if (segments.size() == 1) {
            String name = segments.get(0);
            return Config.FILE_NAME.equals(name) || "wallpaper.jpg".equals(name) || "theme_revision".equals(name)
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
