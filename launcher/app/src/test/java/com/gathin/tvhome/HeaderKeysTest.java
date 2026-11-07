package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HeaderKeysTest {
    @Test public void endIsTheOnlyOtherPosition() {
        assertEquals(Config.HEADER_END, Config.headerPosition("end"));
        assertEquals(Config.HEADER_END, Config.headerPosition(" End "));
        assertEquals(Config.HEADER_START, Config.headerPosition("right"));
        assertEquals(Config.HEADER_START, Config.headerPosition(null));
    }

    @Test public void aClockSizeIsAWholeNumberOfSpInRange() {
        assertEquals(24, Config.clockSize(24, 0));
        assertEquals(36, Config.clockSize(36.0, 0));
        assertEquals(72, Config.clockSize(72, 0));
        assertEquals(0, Config.clockSize(23, 0));
        assertEquals(0, Config.clockSize(73, 0));
        assertEquals(0, Config.clockSize(36.5, 0));
        assertEquals(0, Config.clockSize("36", 0));
    }

    @Test public void aCoordinateMustBeANumberOnTheGlobe() {
        assertEquals(-6.17, Config.coordinate(-6.17, 90, Double.NaN), 0);
        assertEquals(106.83, Config.coordinate(106.83, 180, Double.NaN), 0);
        assertTrue(Double.isNaN(Config.coordinate(91, 90, Double.NaN)));
        assertTrue(Double.isNaN(Config.coordinate("-6.17", 90, Double.NaN)));
        assertEquals(-6.17, Config.coordinate("-6.17", 90), 0);
        assertTrue(Double.isNaN(Config.coordinate("", 90)));
        assertTrue(Double.isNaN(Config.coordinate("north", 90)));
        assertTrue(Double.isNaN(Config.coordinate("200", 180)));
    }

    @Test public void aTopButtonSizeIsAWholeNumberOfDpInRange() {
        assertEquals(28, Config.topButtonSize(28, 0));
        assertEquals(34, Config.topButtonSize(34.0, 0));
        assertEquals(42, Config.topButtonSize(42, 0));
        assertEquals(0, Config.topButtonSize(27, 0));
        assertEquals(0, Config.topButtonSize(43, 0));
        assertEquals(0, Config.topButtonSize(33.5, 0));
        assertEquals(0, Config.topButtonSize("34", 0));
    }
}
