package com.gathin.tvhome;

/**
 * wallpaper_pause_seconds: after that long with no key on Home the animated wallpaper (wallpaper_animated) pauses on
 * the frame it shows, so an unattended Home stops costing CPU; the first key, or Home coming back, plays it again.
 * It takes no key: the key still does what it would do, so with rows_autohide_seconds the first key also only
 * brings the row back.
 *
 * Pure state, no Android types: MainActivity feeds it keys and time and asks it when to pause.
 */
final class WallpaperPause {
    static final int MIN_SECONDS = 30, MAX_SECONDS = 3600;

    private final long timeoutMs;
    private long lastInputMs;
    private boolean paused;

    /** seconds from wallpaper_pause_seconds, already through {@link #seconds}; above 0. */
    WallpaperPause(int seconds, long nowMs) {
        this.timeoutMs = seconds * 1000L;
        this.lastInputMs = nowMs;
    }

    /** A theme's value as the launcher uses it: 0 (never) stays 0, 1-29 count as 30, more than 3600 as 3600. */
    static int seconds(int value) {
        if (value <= 0) return 0;
        return Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, value));
    }

    boolean paused() {
        return paused;
    }

    /** A key was pressed. True when it should play the animation again (it was paused). */
    boolean onKey(long nowMs) {
        lastInputMs = nowMs;
        if (!paused) return false;
        paused = false;
        return true;
    }

    /** Home came back to the screen (or Home was pressed): playing, and the wait starts over. */
    void show(long nowMs) {
        paused = false;
        lastInputMs = nowMs;
    }

    /** True when the animation should pause now; it then counts as paused. */
    boolean pauseNow(long nowMs) {
        if (paused || nowMs - lastInputMs < timeoutMs) return false;
        paused = true;
        return true;
    }

    /** Milliseconds until it would pause, for the next check; at least one. */
    long delayMs(long nowMs) {
        return Math.max(1, lastInputMs + timeoutMs - nowMs);
    }
}
