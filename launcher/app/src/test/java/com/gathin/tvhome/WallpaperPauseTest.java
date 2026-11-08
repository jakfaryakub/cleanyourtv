package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WallpaperPauseTest {
    @Test public void itPausesAfterTheWaitAndNotBefore() {
        WallpaperPause p = new WallpaperPause(300, 0);
        assertFalse(p.pauseNow(299_999));
        assertEquals(1, p.delayMs(299_999));
        assertTrue(p.pauseNow(300_000));
        assertTrue(p.paused());
        assertFalse(p.pauseNow(900_000));   // already paused: nothing to do again
    }

    @Test public void aKeyStartsTheWaitOver() {
        WallpaperPause p = new WallpaperPause(300, 0);
        assertFalse(p.onKey(200_000));
        assertFalse(p.pauseNow(499_999));
        assertEquals(1, p.delayMs(499_999));
        assertTrue(p.pauseNow(500_000));
    }

    @Test public void theFirstKeyAfterAPausePlaysItAgain() {
        WallpaperPause p = new WallpaperPause(300, 0);
        assertTrue(p.pauseNow(300_000));
        assertTrue(p.onKey(400_000));
        assertFalse(p.paused());
        assertFalse(p.onKey(400_500));   // the next keys find it playing
        assertTrue(p.pauseNow(700_500));
    }

    @Test public void homeComingBackPlaysItAndRestartsTheWait() {
        WallpaperPause p = new WallpaperPause(300, 0);
        assertTrue(p.pauseNow(300_000));
        p.show(1_000_000);
        assertFalse(p.paused());
        assertFalse(p.pauseNow(1_299_999));
        assertTrue(p.pauseNow(1_300_000));
    }

    @Test public void zeroMeansNeverAndTheRangeIsThirtySecondsToAnHour() {
        assertEquals(0, WallpaperPause.seconds(0));
        assertEquals(0, WallpaperPause.seconds(-5));
        assertEquals(30, WallpaperPause.seconds(1));
        assertEquals(30, WallpaperPause.seconds(29));
        assertEquals(300, WallpaperPause.seconds(300));
        assertEquals(3600, WallpaperPause.seconds(3600));
        assertEquals(3600, WallpaperPause.seconds(86_400));
    }
}
