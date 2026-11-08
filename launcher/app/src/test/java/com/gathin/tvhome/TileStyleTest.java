package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TileStyleTest {
    @Test public void logoIsTheOnlyOtherStyle() {
        assertEquals(Config.TILE_LOGO, Config.tileStyle("logo"));
        assertEquals(Config.TILE_LOGO, Config.tileStyle(" Logo "));
        assertEquals(Config.TILE_CARD, Config.tileStyle("card"));
    }

    @Test public void anythingElseKeepsTheCards() {
        assertEquals(Config.TILE_CARD, Config.tileStyle(null));
        assertEquals(Config.TILE_CARD, Config.tileStyle(""));
        assertEquals(Config.TILE_CARD, Config.tileStyle("logos"));
        assertEquals(Config.TILE_CARD, Config.tileStyle("banner"));
    }

    @Test public void aWordmarkFollowsTheTileHeight() {
        assertEquals(34.54f, AppAdapter.wordmarkTextPx(157), 0.01f);
        assertEquals(22.22f, AppAdapter.wordmarkTextPx(101), 0.01f);
    }
}
