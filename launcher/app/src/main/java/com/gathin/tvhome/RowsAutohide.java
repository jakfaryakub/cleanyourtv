package com.gathin.tvhome;

/**
 * rows_autohide_seconds: after that long with no key on Home the app row fades out and only the wallpaper and the
 * header stay. The first key afterwards only brings the row back: it is taken whole (its DOWN, repeats and UP), so
 * the focus does not move and no app opens. Volume and mute keys pass through untouched and do not count as input.
 *
 * Pure state, no Android types: MainActivity feeds it keys and time and asks it when to hide. While something is
 * blocking (tiles being moved, a menu, a dialog, Home in the background) it never hides, and the wait starts over.
 */
final class RowsAutohide {
    static final int MIN_SECONDS = 5, MAX_SECONDS = 3600;
    /** What a key should do. */
    static final int PASS = 0, REVEAL = 1, SWALLOW = 2;

    private final long timeoutMs;
    private long lastInputMs;
    private boolean hidden;
    /** The key whose DOWN brought the row back, taken until its UP; -1 for none. */
    private int swallowing = -1;

    /** seconds from rows_autohide_seconds (1-3600); under {@link #MIN_SECONDS} counts as that. */
    RowsAutohide(int seconds, long nowMs) {
        this.timeoutMs = Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, seconds)) * 1000L;
        this.lastInputMs = nowMs;
    }

    boolean hidden() {
        return hidden;
    }

    /**
     * One key event. PASS lets it through; REVEAL means it was the first key after hiding: show the row and take the
     * event; SWALLOW takes the rest of that key (repeats and its UP).
     */
    int onKey(int keyCode, boolean down, long nowMs) {
        if (passesThrough(keyCode)) return PASS;
        if (keyCode == swallowing) {
            if (!down) swallowing = -1;
            lastInputMs = nowMs;
            return SWALLOW;
        }
        lastInputMs = nowMs;
        if (hidden && down) {
            hidden = false;
            swallowing = keyCode;
            return REVEAL;
        }
        return PASS;
    }

    /** Something other than a key brought the row back (Home pressed, Home resumed, a menu opened). */
    void show(long nowMs) {
        hidden = false;
        swallowing = -1;
        lastInputMs = nowMs;
    }

    /** True when the row should go now; it then counts as hidden. A blocker restarts the wait instead. */
    boolean hideNow(long nowMs, boolean blocked) {
        if (hidden) return false;
        if (blocked) {
            lastInputMs = nowMs;
            return false;
        }
        if (nowMs - lastInputMs < timeoutMs) return false;
        hidden = true;
        return true;
    }

    /** Milliseconds until the row would go, for the next check; at least one. */
    long delayMs(long nowMs) {
        return Math.max(1, lastInputMs + timeoutMs - nowMs);
    }

    /** Volume, mute and similar keys work over a hidden row and do not bring it back. */
    static boolean passesThrough(int keyCode) {
        switch (keyCode) {
            case 24:    // KEYCODE_VOLUME_UP
            case 25:    // KEYCODE_VOLUME_DOWN
            case 164:   // KEYCODE_VOLUME_MUTE
            case 91:    // KEYCODE_MUTE
                return true;
            default:
                return false;
        }
    }
}
