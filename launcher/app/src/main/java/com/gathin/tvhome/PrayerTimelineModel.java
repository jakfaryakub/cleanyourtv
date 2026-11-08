package com.gathin.tvhome;

import java.util.Arrays;

/**
 * prayer_style "timeline": what the 24-hour line shows at one minute, kept apart from Android so it can be unit
 * tested. Today's five times sit on the line from 00:00 to 24:00; the ones already passed are dimmed and the next one
 * glows, with its time as the only label. Once Isha has passed the line still shows today, every dot dimmed, and the
 * glow and label go to Fajr's dot with tomorrow's time (a minute apart at most, so the same place on the line).
 */
final class PrayerTimelineModel {
    static final int DAY = 24 * 60;

    /** Today's times in minutes of the day, Prayer.NONE for one that does not happen. */
    final int[] times;
    final boolean[] passed;
    /** The prayer that glows, or -1 when no time of either day happens. */
    final int next;
    /** The minute written under the glowing dot: today's time, or tomorrow's after Isha. */
    final int label;
    /** Where the glowing dot sits, in minutes of the day: today's time, or the label's when today has none. */
    final int nextAt;
    final int now;

    private PrayerTimelineModel(int[] times, boolean[] passed, int next, int label, int nextAt, int now) {
        this.times = times;
        this.passed = passed;
        this.next = next;
        this.label = label;
        this.nextAt = nextAt;
        this.now = now;
    }

    /** The place of a minute on the line, 0 at 00:00 and 1 at 24:00. */
    static float fraction(int minute) {
        return Math.max(0, Math.min(DAY, minute)) / (float) DAY;
    }

    /** @param line Prayer.line at the same minute: it decides the next prayer, as on the text line. */
    static PrayerTimelineModel of(int[] today, Prayer.Line line, int nowMinute) {
        int[] times = today.clone();
        boolean[] passed = new boolean[times.length];
        for (int i = 0; i < times.length; i++) {
            passed[i] = times[i] != Prayer.NONE && (line.tomorrow || times[i] <= nowMinute);
        }
        int next = line.next;
        int label = next >= 0 && next < line.times.length ? line.times[next] : Prayer.NONE;
        if (label == Prayer.NONE) next = -1;
        else if (!line.tomorrow) passed[next] = false;
        int nextAt = next < 0 ? Prayer.NONE : times[next] != Prayer.NONE ? times[next] : label;
        return new PrayerTimelineModel(times, passed, next, label, nextAt, Math.max(0, Math.min(DAY - 1, nowMinute)));
    }

    boolean sameAs(PrayerTimelineModel other) {
        return other != null && other.next == next && other.label == label && other.now == now
                && other.nextAt == nextAt && Arrays.equals(other.times, times) && Arrays.equals(other.passed, passed);
    }
}
