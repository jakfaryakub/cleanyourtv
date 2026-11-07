package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RowsAutohideTest {
    static final int DPAD_RIGHT = 22, OK = 23, BACK = 4, VOLUME_UP = 24, MUTE = 164;

    @Test public void theRowGoesAfterTheWaitAndNotBefore() {
        RowsAutohide a = new RowsAutohide(30, 0);
        assertFalse(a.hideNow(29_999, false));
        assertEquals(1, a.delayMs(29_999));
        assertTrue(a.hideNow(30_000, false));
        assertTrue(a.hidden());
        assertFalse(a.hideNow(60_000, false));   // already hidden: no second fade
    }

    @Test public void aKeyStartsTheWaitOver() {
        RowsAutohide a = new RowsAutohide(30, 0);
        assertEquals(RowsAutohide.PASS, a.onKey(DPAD_RIGHT, true, 20_000));
        assertEquals(RowsAutohide.PASS, a.onKey(DPAD_RIGHT, false, 20_100));
        assertFalse(a.hideNow(49_000, false));
        assertEquals(1_100, a.delayMs(49_000));
        assertTrue(a.hideNow(50_100, false));
    }

    @Test public void theFirstKeyOnlyBringsTheRowBack() {
        RowsAutohide a = new RowsAutohide(30, 0);
        assertTrue(a.hideNow(30_000, false));
        assertEquals(RowsAutohide.REVEAL, a.onKey(DPAD_RIGHT, true, 40_000));
        assertFalse(a.hidden());
        // Its repeats and its UP are taken too, so the focus does not move.
        assertEquals(RowsAutohide.SWALLOW, a.onKey(DPAD_RIGHT, true, 40_400));
        assertEquals(RowsAutohide.SWALLOW, a.onKey(DPAD_RIGHT, false, 40_500));
        // The next press moves the focus as usual.
        assertEquals(RowsAutohide.PASS, a.onKey(DPAD_RIGHT, true, 41_000));
        assertEquals(RowsAutohide.PASS, a.onKey(DPAD_RIGHT, false, 41_100));
    }

    @Test public void okOnAHiddenRowOpensNothing() {
        RowsAutohide a = new RowsAutohide(30, 0);
        a.hideNow(30_000, false);
        assertEquals(RowsAutohide.REVEAL, a.onKey(OK, true, 31_000));
        assertEquals(RowsAutohide.SWALLOW, a.onKey(OK, false, 31_200));
        assertEquals(RowsAutohide.PASS, a.onKey(OK, true, 32_000));   // the second OK opens the app
    }

    @Test public void anUpWithNoDownOnAHiddenRowPassesAndDoesNotReveal() {
        RowsAutohide a = new RowsAutohide(30, 0);
        a.hideNow(30_000, false);
        assertEquals(RowsAutohide.PASS, a.onKey(BACK, false, 31_000));
        assertTrue(a.hidden());
    }

    @Test public void volumeAndMuteWorkOverAHiddenRow() {
        RowsAutohide a = new RowsAutohide(30, 0);
        a.hideNow(30_000, false);
        assertEquals(RowsAutohide.PASS, a.onKey(VOLUME_UP, true, 31_000));
        assertEquals(RowsAutohide.PASS, a.onKey(MUTE, true, 31_500));
        assertTrue(a.hidden());
        // and they do not hold the row up either
        RowsAutohide b = new RowsAutohide(30, 0);
        b.onKey(VOLUME_UP, true, 20_000);
        assertTrue(b.hideNow(30_000, false));
    }

    @Test public void aBlockerRestartsTheWait() {
        RowsAutohide a = new RowsAutohide(30, 0);
        assertFalse(a.hideNow(30_000, true));   // a menu was open at the moment it would have gone
        assertFalse(a.hideNow(59_999, false));
        assertTrue(a.hideNow(60_000, false));
    }

    @Test public void showingByOtherMeansStartsTheWaitOver() {
        RowsAutohide a = new RowsAutohide(30, 0);
        a.hideNow(30_000, false);
        a.show(35_000);
        assertFalse(a.hidden());
        assertFalse(a.hideNow(64_999, false));
        assertTrue(a.hideNow(65_000, false));
    }

    @Test public void tooShortAWaitIsFiveSeconds() {
        RowsAutohide a = new RowsAutohide(1, 0);
        assertFalse(a.hideNow(4_999, false));
        assertTrue(a.hideNow(5_000, false));
    }
}
