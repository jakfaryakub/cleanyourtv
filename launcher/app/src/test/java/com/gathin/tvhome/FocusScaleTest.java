package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FocusScaleTest {
    @Test public void aThemeScaleInsideTheRangeIsTaken() {
        assertEquals(1.0f, Config.focusScale(1, 0f), 0f);
        assertEquals(1.2f, Config.focusScale(1.2, 0f), 0.0001f);
        assertEquals(1.3f, Config.focusScale(1.3, 0f), 0.0001f);
    }

    @Test public void anythingElseKeepsTheBuiltInZoom() {
        assertEquals(0f, Config.focusScale(0.9, 0f), 0f);
        assertEquals(0f, Config.focusScale(1.31, 0f), 0f);
        assertEquals(0f, Config.focusScale("1.2", 0f), 0f);
        assertEquals(0f, Config.focusScale(null, 0f), 0f);
    }

    @Test public void theZoomUsesTheThemeScaleWhenThereIsOne() {
        assertEquals(1.2f, AppAdapter.focusScale(true, false, 1.2f), 0f);
        assertEquals(1.2f, AppAdapter.focusScale(true, true, 1.2f), 0f);
        assertEquals(1.10f, AppAdapter.focusScale(true, false, 0f), 0f);
        assertEquals(1.12f, AppAdapter.focusScale(true, true, 0f), 0f);
    }

    @Test public void noAnimationsMeansNoZoom() {
        assertEquals(1f, AppAdapter.focusScale(false, true, 1.2f), 0f);
        assertEquals(1f, AppAdapter.focusScale(false, false, 0f), 0f);
    }
}
