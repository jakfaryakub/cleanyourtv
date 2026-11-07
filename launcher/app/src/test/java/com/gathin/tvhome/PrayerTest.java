package com.gathin.tvhome;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Prayer against Kemenag RI's published table for Jakarta (resources/prayer/, with source and date read). The test
 * set was held out while the parameters were chosen on the tuning set; each time may be off by 1 minute at most.
 */
public class PrayerTest {
    /** The place Kemenag's table states for Kota Jakarta: 6 10 12.32 S, 106 49 51.78 E. */
    static final double LAT = -(6 + 10 / 60.0 + 12.32 / 3600), LON = 106 + 49 / 60.0 + 51.78 / 3600;
    static final ZoneId WIB = ZoneId.of("Asia/Jakarta");
    static final String[] NAMES = {"subuh", "dzuhur", "ashar", "maghrib", "isya"};

    private static List<String[]> table(String name) throws Exception {
        InputStream in = PrayerTest.class.getClassLoader().getResourceAsStream("prayer/" + name);
        assertNotNull("missing test resource prayer/" + name, in);
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#") || line.startsWith("date") || line.trim().isEmpty()) continue;
                rows.add(line.split(","));
            }
        }
        return rows;
    }

    private static int minutes(String hhmm) {
        return Integer.parseInt(hhmm.substring(0, 2)) * 60 + Integer.parseInt(hhmm.substring(3, 5));
    }

    /**
     * The safety rule: a computed time is never earlier than Kemenag's and at most one minute later. Prayer must not
     * tell anyone a prayer has begun before the official time.
     */
    private static void safe(String file, int days) throws Exception {
        List<String[]> rows = table(file);
        assertEquals(file, days, rows.size());
        StringBuilder bad = new StringBuilder();
        for (String[] row : rows) {
            int[] got = Prayer.times(LocalDate.parse(row[0]), LAT, LON, WIB, Prayer.IHTIYAT_DEFAULT);
            for (int i = 0; i < Prayer.COUNT; i++) {
                int late = got[i] - minutes(row[i + 1]);
                if (late < 0 || late > 1) {
                    bad.append(String.format("%n%s %s: Kemenag %s, computed %02d:%02d (%+d)", row[0], NAMES[i],
                            row[i + 1], got[i] / 60, got[i] % 60, late));
                }
            }
        }
        assertTrue("never earlier, at most 1 minute later:" + bad, bad.length() == 0);
    }

    @Test public void theHeldOutDatesAreNeverEarlyAndAtMostAMinuteLate() throws Exception {
        safe("kemenag-jakarta-2026-test.csv", 6);
    }

    @Test public void everyDayOfNineMonthsIsNeverEarlyAndAtMostAMinuteLate() throws Exception {
        safe("kemenag-jakarta-2026-validation.csv", 272);
    }

    @Test public void theHoldoutMonthsAreNeverEarlyAndAtMostAMinuteLate() throws Exception {
        // May, July and October: loaded after the parameters and the margin were fixed, checked once, never tuned on.
        List<String[]> rows = table("kemenag-jakarta-2026-holdout.csv");
        int[][] count = new int[Prayer.COUNT][3];   // earlier, same, one minute later
        for (String[] row : rows) {
            int[] got = Prayer.times(LocalDate.parse(row[0]), LAT, LON, WIB, Prayer.IHTIYAT_DEFAULT);
            for (int i = 0; i < Prayer.COUNT; i++) {
                int late = got[i] - minutes(row[i + 1]);
                count[i][late < 0 ? 0 : late == 0 ? 1 : 2]++;
            }
        }
        for (int i = 0; i < Prayer.COUNT; i++) {
            System.out.printf("holdout %-8s earlier %3d  same %3d  later %3d%n", NAMES[i], count[i][0], count[i][1], count[i][2]);
        }
        safe("kemenag-jakarta-2026-holdout.csv", 93);
    }

    @Test public void theTuningDatesKeepTheSameRule() throws Exception {
        safe("kemenag-jakarta-2026-tuning.csv", 6);
    }

    @Test public void theNextPrayerIsTheFirstStillToCome() {
        int[] t = {4 * 60 + 19, 12 * 60, 15 * 60 + 26, 18 * 60 + 14, 19 * 60 + 29};
        assertEquals(Prayer.SUBUH, Prayer.next(t, 0));
        assertEquals(Prayer.DZUHUR, Prayer.next(t, 4 * 60 + 19));   // at Subuh's minute it is Dzuhur's turn
        assertEquals(Prayer.ISYA, Prayer.next(t, 18 * 60 + 57));
        assertEquals(Prayer.SUBUH, Prayer.next(t, 19 * 60 + 29));   // after Isya: tomorrow's Subuh
    }

    @Test public void afterIsyaTheDayIsDone() {
        int[] t = {4 * 60 + 7, 11 * 60 + 40, 14 * 60 + 58, 17 * 60 + 50, 19 * 60 + 2};
        assertTrue(Prayer.allPassed(t, 19 * 60 + 22));
        assertTrue(Prayer.allPassed(t, 19 * 60 + 2));
        assertEquals(false, Prayer.allPassed(t, 19 * 60 + 1));
        assertEquals(false, Prayer.allPassed(t, 0));
    }

    @Test public void theLineIsTodayUntilIshaThenTomorrow() {
        LocalDate nov7 = LocalDate.of(2026, 11, 7);
        int[] today = Prayer.times(nov7, LAT, LON, WIB, 2);
        Prayer.Line evening = Prayer.line(nov7, today[Prayer.ISYA] - 1, LAT, LON, WIB, 2);
        assertEquals(nov7, evening.day);
        assertEquals(false, evening.tomorrow);
        assertEquals(Prayer.ISYA, evening.next);
        assertEquals(today[Prayer.ISYA], evening.changesAt());

        Prayer.Line night = Prayer.line(nov7, today[Prayer.ISYA], LAT, LON, WIB, 2);
        assertEquals(nov7.plusDays(1), night.day);
        assertTrue(night.tomorrow);
        assertEquals(Prayer.SUBUH, night.next);
        assertEquals(24 * 60, night.changesAt());   // redrawn at midnight, as today's line again
        assertEquals(Prayer.times(nov7.plusDays(1), LAT, LON, WIB, 2)[Prayer.SUBUH], night.times[Prayer.SUBUH]);

        Prayer.Line afterMidnight = Prayer.line(nov7.plusDays(1), 0, LAT, LON, WIB, 2);
        assertEquals(nov7.plusDays(1), afterMidnight.day);
        assertEquals(false, afterMidnight.tomorrow);
        assertEquals(Prayer.SUBUH, afterMidnight.next);
    }

    @Test public void theLastNightOfTheYearShowsNewYearsDay() {
        Prayer.Line line = Prayer.line(LocalDate.of(2026, 12, 31), 23 * 60 + 59, LAT, LON, WIB, 2);
        assertEquals(LocalDate.of(2027, 1, 1), line.day);
        assertTrue(line.tomorrow);
    }

    @Test public void aTimeTheSunNeverReachesIsNoneAndSkipped() {
        // Oslo in June: the sun never sinks 18 degrees below the horizon, so there is no Isya (nor Subuh).
        int[] oslo = Prayer.times(LocalDate.of(2026, 6, 21), 59.91, 10.75, ZoneId.of("Europe/Oslo"), 2);
        assertEquals(Prayer.NONE, oslo[Prayer.ISYA]);
        assertEquals(Prayer.NONE, oslo[Prayer.SUBUH]);
        assertTrue(oslo[Prayer.MAGHRIB] > 22 * 60);
        assertEquals(Prayer.DZUHUR, Prayer.next(oslo, 3 * 60));
        assertEquals(Prayer.SUBUH, Prayer.next(oslo, 23 * 60 + 30));
    }

    @Test public void theIhtiyatMovesAllButDzuhur() {
        LocalDate d = LocalDate.of(2026, 3, 20);
        int[] two = Prayer.times(d, LAT, LON, WIB, 2), zero = Prayer.times(d, LAT, LON, WIB, 0);
        assertEquals(two[Prayer.DZUHUR], zero[Prayer.DZUHUR]);
        for (int i : new int[]{Prayer.SUBUH, Prayer.ASHAR, Prayer.MAGHRIB, Prayer.ISYA}) {
            int shift = two[i] - zero[i];
            assertTrue(NAMES[i] + " moved " + shift, shift >= 1 && shift <= 2);   // rounding up can eat a minute
        }
    }
}
