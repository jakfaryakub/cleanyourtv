package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UnfocusedOpacityTest {
    @Test public void anOpacityInsideTheRangeIsTaken() {
        assertEquals(0.3f, Config.unfocusedOpacity(0.3, 1f), 0.0001f);
        assertEquals(0.5f, Config.unfocusedOpacity(0.5, 1f), 0.0001f);
        assertEquals(1f, Config.unfocusedOpacity(1, 1f), 0f);
    }

    @Test public void anythingElseKeepsFullOpacity() {
        assertEquals(1f, Config.unfocusedOpacity(0.2, 1f), 0f);
        assertEquals(1f, Config.unfocusedOpacity(1.5, 1f), 0f);
        assertEquals(1f, Config.unfocusedOpacity("0.5", 1f), 0f);
        assertEquals(1f, Config.unfocusedOpacity(null, 1f), 0f);
    }

    @Test public void onlyATileInALogoRowThatIsNotFocusedIsDimmed() {
        assertEquals(0.5f, AppAdapter.contentAlpha(false, true, 0.5f), 0f);
        assertEquals(1f, AppAdapter.contentAlpha(true, true, 0.5f), 0f);
        assertEquals(1f, AppAdapter.contentAlpha(false, false, 0.5f), 0f);   // tile_style card never dims
        assertEquals(1f, AppAdapter.contentAlpha(false, true, 1f), 0f);
    }
}
