package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ThemeThumbsTest {
    @Test public void sixteenByNineIsKeptWhole() {
        assertArrayEquals(new int[] {0, 0, 1920, 1080}, ThemeThumbs.crop(1920, 1080));
        assertArrayEquals(new int[] {0, 0, 640, 360}, ThemeThumbs.crop(640, 360));
    }

    @Test public void widerPicturesLoseTheirSides() {
        assertArrayEquals(new int[] {240, 0, 1680, 810}, ThemeThumbs.crop(1920, 810));
    }

    @Test public void tallerPicturesLoseTopAndBottom() {
        assertArrayEquals(new int[] {0, 210, 1080, 817}, ThemeThumbs.crop(1080, 1027));
        assertArrayEquals(new int[] {0, 0, 0, 0}, ThemeThumbs.crop(0, 0));
    }

    @Test public void theCacheNameChangesWithTheWallpaper() {
        assertEquals("red-eyes-194370-1700000000000.jpg", ThemeThumbs.name("red-eyes", 194370, 1700000000000L));
        assertNotEquals(ThemeThumbs.name("a", 1, 2), ThemeThumbs.name("a", 1, 3));
    }

    @Test public void aFullHdWallpaperIsDecodedAtAQuarter() {
        // 1920x1080 into 320x180: sample 4 gives 480x270, the smallest power of two not under the card.
        assertEquals(4, ThemeIcons.sampleSize(1920, 1080, ThemeThumbs.WIDTH, ThemeThumbs.HEIGHT));
    }
}
