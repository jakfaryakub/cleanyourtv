package com.gathin.tvhome;

/** Physical left/right boundaries for a grid whose first column follows its reading direction. */
final class GridNavigation {
    /** What Down from a tile does: move down a row, focus the App library pill, open the App library, or stay. */
    static final int DOWN_ROW = 0, DOWN_PILL = 1, DOWN_LIBRARY = 2, DOWN_STAY = 3;

    private GridNavigation() { }

    static boolean atHorizontalEdge(int position, int count, int columns, boolean rtl, boolean left) {
        boolean towardStart = left != rtl;
        if (towardStart) return position % columns == 0;
        return position % columns == columns - 1 || position == count - 1;
    }

    /**
     * row_wrap: the tile at the other end of the row when Left/Right leaves it at an edge, or -1 to leave focus where
     * it is (not at an edge, or a row of one tile). rowStart and rowEnd are the row's first and last focusable tiles.
     */
    static int wrapTarget(int position, int rowStart, int rowEnd, boolean rtl, boolean left) {
        if (rowEnd <= rowStart) return -1;
        boolean towardStart = left != rtl;
        if (towardStart) return position == rowStart ? rowEnd : -1;
        return position == rowEnd ? rowStart : -1;
    }

    /**
     * Down from the tile at position: a single row, or the grid's last row, leaves for the pill when it is shown,
     * otherwise opens the library with down_opens_library, otherwise stays. Other rows move down.
     */
    static int down(int position, int count, int columns, boolean singleRow, boolean pill, boolean opensLibrary) {
        boolean lastRow = singleRow || position >= ((count - 1) / columns) * columns;
        if (!lastRow) return DOWN_ROW;
        if (pill) return DOWN_PILL;
        return opensLibrary ? DOWN_LIBRARY : DOWN_STAY;
    }
}
