package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AnimatedWallpaperTest {
    @Test public void playsOnAndroid9AndLaterWithAFileUnderTheCap() {
        assertEquals(AnimatedWallpaper.PLAY, AnimatedWallpaper.decide(28, false, 800_000));
        assertEquals(AnimatedWallpaper.PLAY, AnimatedWallpaper.decide(31, false, AnimatedWallpaper.MAX_BYTES));
    }

    @Test public void olderAndroidKeepsTheStillPicture() {
        assertEquals(AnimatedWallpaper.OLD_ANDROID, AnimatedWallpaper.decide(26, false, 800_000));
        assertEquals(AnimatedWallpaper.OLD_ANDROID, AnimatedWallpaper.decide(27, true, 800_000));
    }

    @Test public void reducedMotionDecodesNothing() {
        assertEquals(AnimatedWallpaper.REDUCED_MOTION, AnimatedWallpaper.decide(31, true, 800_000));
    }

    @Test public void anEmptyOrMissingFileKeepsTheStillPicture() {
        assertEquals(AnimatedWallpaper.NO_FILE, AnimatedWallpaper.decide(31, false, 0));
    }

    @Test public void aFileOverThreeMegabytesIsRefused() {
        assertEquals(AnimatedWallpaper.TOO_BIG, AnimatedWallpaper.decide(31, false, AnimatedWallpaper.MAX_BYTES + 1));
    }

    @Test public void fullHdIsTheLargestAcceptedSize() {
        assertTrue(AnimatedWallpaper.acceptedSize(1920, 1080));
        assertTrue(AnimatedWallpaper.acceptedSize(960, 540));
        assertFalse(AnimatedWallpaper.acceptedSize(1921, 1080));
        assertFalse(AnimatedWallpaper.acceptedSize(1920, 1081));
        assertFalse(AnimatedWallpaper.acceptedSize(3840, 2160));
        assertFalse(AnimatedWallpaper.acceptedSize(0, 540));
    }

    @Test public void aSmallAnimationIsDecodedAsIs() {
        assertArrayEquals(new int[] {960, 540}, AnimatedWallpaper.decodeSize(960, 540));
        assertArrayEquals(new int[] {640, 360}, AnimatedWallpaper.decodeSize(640, 360));
    }

    @Test public void aLargerOneIsScaledDownToFitKeepingItsShape() {
        assertArrayEquals(new int[] {960, 540}, AnimatedWallpaper.decodeSize(1920, 1080));
        assertArrayEquals(new int[] {960, 411}, AnimatedWallpaper.decodeSize(1680, 720));   // 21:9
        assertArrayEquals(new int[] {720, 540}, AnimatedWallpaper.decodeSize(1440, 1080));  // 4:3
    }
}
