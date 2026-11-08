package com.gathin.tvhome;

import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PrayerTimelineModelTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 8);
    // Jakarta, 8 November 2026: Fajr 04:07, Dhuhr 11:40, Asr 14:59, Maghrib 17:51, Isha 19:03.
    private static final int[] TODAY = Prayer.times(DAY, PrayerTest.LAT, PrayerTest.LON, PrayerTest.WIB, 2);

    private static PrayerTimelineModel at(LocalDate day, int[] today, int minute) {
        return PrayerTimelineModel.of(today,
                Prayer.line(day, minute, PrayerTest.LAT, PrayerTest.LON, PrayerTest.WIB, 2), minute);
    }

    @Test public void minutesSitOnTheLineFromMidnightToMidnight() {
        assertEquals(0f, PrayerTimelineModel.fraction(0), 0f);
        assertEquals(0.5f, PrayerTimelineModel.fraction(12 * 60), 0f);
        assertEquals(0.25f, PrayerTimelineModel.fraction(6 * 60), 0f);
        assertEquals(1f, PrayerTimelineModel.fraction(24 * 60), 0f);
        assertEquals((4 * 60 + 7) / 1440f, PrayerTimelineModel.fraction(4 * 60 + 7), 1e-6f);
    }

    @Test public void minutesOutsideTheDayStayOnTheLine() {
        assertEquals(0f, PrayerTimelineModel.fraction(-30), 0f);
        assertEquals(1f, PrayerTimelineModel.fraction(25 * 60), 0f);
    }

    @Test public void theNowTickFollowsTheMinute() {
        assertEquals(13 * 60 + 10, at(DAY, TODAY, 13 * 60 + 10).now);
        assertEquals(0, at(DAY, TODAY, 0).now);
        assertEquals(1439, at(DAY, TODAY, 1439).now);
    }

    @Test public void atMiddayFajrAndDhuhrArePassedAndAsrGlowsWithItsTime() {
        PrayerTimelineModel m = at(DAY, TODAY, 13 * 60 + 10);
        assertEquals(Prayer.ASHAR, m.next);
        assertEquals(14 * 60 + 59, m.label);
        assertEquals(m.label, m.nextAt);
        assertTrue(m.passed[Prayer.SUBUH]);
        assertTrue(m.passed[Prayer.DZUHUR]);
        assertFalse(m.passed[Prayer.ASHAR]);
        assertFalse(m.passed[Prayer.MAGHRIB]);
        assertFalse(m.passed[Prayer.ISYA]);
    }

    @Test public void aPrayerIsPassedFromItsOwnMinute() {
        int asr = TODAY[Prayer.ASHAR];
        assertEquals(Prayer.ASHAR, at(DAY, TODAY, asr - 1).next);
        PrayerTimelineModel m = at(DAY, TODAY, asr);
        assertTrue(m.passed[Prayer.ASHAR]);
        assertEquals(Prayer.MAGHRIB, m.next);
    }

    @Test public void afterIshaEveryDotIsDimmedAndTomorrowsFajrGlowsOnTodaysDot() {
        PrayerTimelineModel m = at(DAY, TODAY, 21 * 60 + 30);
        int[] tomorrow = Prayer.times(DAY.plusDays(1), PrayerTest.LAT, PrayerTest.LON, PrayerTest.WIB, 2);
        for (boolean p : m.passed) assertTrue(p);
        assertEquals(Prayer.SUBUH, m.next);
        assertEquals(tomorrow[Prayer.SUBUH], m.label);
        assertEquals(TODAY[Prayer.SUBUH], m.nextAt);
        assertArrayEquals(TODAY, m.times);   // today's line stays
        assertTrue(Math.abs(m.label - m.nextAt) <= 1);
    }

    @Test public void atMidnightTheNewDayStartsWithNothingPassed() {
        LocalDate next = DAY.plusDays(1);
        int[] tomorrow = Prayer.times(next, PrayerTest.LAT, PrayerTest.LON, PrayerTest.WIB, 2);
        PrayerTimelineModel m = at(next, tomorrow, 0);
        for (boolean p : m.passed) assertFalse(p);
        assertEquals(Prayer.SUBUH, m.next);
        assertEquals(tomorrow[Prayer.SUBUH], m.label);
        assertEquals(0, m.now);
        assertFalse(at(next, tomorrow, 0).sameAs(at(DAY, TODAY, 23 * 60 + 59)));
    }

    @Test public void aTimeThatDoesNotHappenHasNoDotAndIsNeverNext() {
        int[] noIsha = TODAY.clone();
        noIsha[Prayer.ISYA] = Prayer.NONE;
        Prayer.Line line = new Prayer.Line(DAY, noIsha, Prayer.MAGHRIB, false);
        PrayerTimelineModel m = PrayerTimelineModel.of(noIsha, line, 17 * 60);
        assertFalse(m.passed[Prayer.ISYA]);
        assertEquals(Prayer.MAGHRIB, m.next);
    }

    @Test public void noTimeAtAllMeansNoGlow() {
        int[] none = {Prayer.NONE, Prayer.NONE, Prayer.NONE, Prayer.NONE, Prayer.NONE};
        PrayerTimelineModel m = PrayerTimelineModel.of(none, new Prayer.Line(DAY, none, Prayer.SUBUH, true), 600);
        assertEquals(-1, m.next);
    }

    @Test public void theSameMinuteIsNotRedrawn() {
        assertTrue(at(DAY, TODAY, 600).sameAs(at(DAY, TODAY, 600)));
        assertFalse(at(DAY, TODAY, 600).sameAs(at(DAY, TODAY, 601)));
    }
}
