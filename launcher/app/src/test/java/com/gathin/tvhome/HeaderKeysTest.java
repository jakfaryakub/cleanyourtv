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
}
