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

    private static void within(String file, int tolerance) throws Exception {
        List<String[]> rows = table(file);
        assertEquals(file, 6, rows.size());
        for (String[] row : rows) {
            int[] got = Prayer.times(LocalDate.parse(row[0]), LAT, LON, WIB, Prayer.IHTIYAT_DEFAULT);
            for (int i = 0; i < Prayer.COUNT; i++) {
                int want = minutes(row[i + 1]);
                assertTrue(row[0] + " " + NAMES[i] + ": Kemenag " + row[i + 1] + ", computed "
                                + String.format("%02d:%02d", got[i] / 60, got[i] % 60),
                        Math.abs(got[i] - want) <= tolerance);
            }
        }
    }

    @Test public void theHeldOutDatesMatchKemenagWithinAMinute() throws Exception {
        within("kemenag-jakarta-2026-test.csv", 1);
    }

    @Test public void theTuningDatesStillMatchKemenagWithinAMinute() throws Exception {
        within("kemenag-jakarta-2026-tuning.csv", 1);
    }

    @Test public void theNextPrayerIsTheFirstStillToCome() {
        int[] t = {4 * 60 + 19, 12 * 60, 15 * 60 + 26, 18 * 60 + 14, 19 * 60 + 29};
        assertEquals(Prayer.SUBUH, Prayer.next(t, 0));
        assertEquals(Prayer.DZUHUR, Prayer.next(t, 4 * 60 + 19));   // at Subuh's minute it is Dzuhur's turn
        assertEquals(Prayer.ISYA, Prayer.next(t, 18 * 60 + 57));
        assertEquals(Prayer.SUBUH, Prayer.next(t, 19 * 60 + 29));   // after Isya: tomorrow's Subuh
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
