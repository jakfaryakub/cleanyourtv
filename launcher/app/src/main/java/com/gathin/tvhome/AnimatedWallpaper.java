package com.gathin.tvhome;

/**
 * wallpaper_animated: the rules for a theme's wallpaper.webp, kept apart from Android so they can be unit tested.
 * WallpaperPlayer decodes the file with ImageDecoder into an AnimatedImageDrawable; the still wallpaper.jpg stays
 * the first frame Home draws and the fallback whenever the animation cannot play.
 *
 * Limits, from the Phase 4a survey: at most 3 MB on disk, at most 1920x1080 (bigger is refused), decoded at no more
 * than 960x540. Frame rate and loop length cannot be checked by ImageDecoder; tools/webp_wallpaper.py checks them
 * offline before the file reaches the TV.
 */
final class AnimatedWallpaper {
    static final String FILE_NAME = "wallpaper.webp";
    static final long MAX_BYTES = 3L * 1024 * 1024;
    static final int MAX_WIDTH = 1920, MAX_HEIGHT = 1080;
    static final int DECODE_WIDTH = 960, DECODE_HEIGHT = 540;

    /** Why the animation is not decoded; PLAY when it is. */
    static final int PLAY = 0, OLD_ANDROID = 1, REDUCED_MOTION = 2, NO_FILE = 3, TOO_BIG = 4;

    private AnimatedWallpaper() { }

    /** Decided before decoding: Android 9 (API 28) has AnimatedImageDrawable; reduced_motion keeps the still JPG. */
    static int decide(int sdk, boolean reducedMotion, long bytes) {
        if (sdk < 28) return OLD_ANDROID;
        if (reducedMotion) return REDUCED_MOTION;
        if (bytes <= 0) return NO_FILE;
        return bytes > MAX_BYTES ? TOO_BIG : PLAY;
    }

    /** The header's size: refused over 1920x1080 in either direction, or when empty. */
    static boolean acceptedSize(int width, int height) {
        return width > 0 && height > 0 && width <= MAX_WIDTH && height <= MAX_HEIGHT;
    }

    /** The size to decode at: as is when it fits in 960x540, else scaled down to fit, keeping its shape. */
    static int[] decodeSize(int width, int height) {
        if (width <= DECODE_WIDTH && height <= DECODE_HEIGHT) return new int[] {width, height};
        double scale = Math.min((double) DECODE_WIDTH / width, (double) DECODE_HEIGHT / height);
        return new int[] {Math.max(1, (int) Math.round(width * scale)), Math.max(1, (int) Math.round(height * scale))};
    }

    /**
     * The file's first 12 bytes are a RIFF/WEBP header whose length matches the file. A file cut short (a push that
     * stopped) still decodes, as the frames before the cut, so it is refused here instead of playing a broken loop.
     */
    static boolean complete(byte[] header, long fileLength) {
        if (header == null || header.length < 12) return false;
        if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F'
                || header[8] != 'W' || header[9] != 'E' || header[10] != 'B' || header[11] != 'P') return false;
        long size = (header[4] & 0xFFL) | (header[5] & 0xFFL) << 8 | (header[6] & 0xFFL) << 16
                | (header[7] & 0xFFL) << 24;
        return size + 8 == fileLength;
    }

    /** For the log line when the still JPG stays. */
    static String reason(int decision) {
        switch (decision) {
            case OLD_ANDROID: return "Android older than 9";
            case REDUCED_MOTION: return "reduced_motion";
            case NO_FILE: return "no wallpaper.webp";
            case TOO_BIG: return "wallpaper.webp over 3 MB";
            default: return "plays";
        }
    }
}
