package com.quadrant;

/**
 * Holds the failure count for each of the four quadrants of a tested object.
 *
 * Quadrant layout:
 *   cols [0, midCol)        cols [midCol, width)
 *   +-------------------+-------------------+
 *   |    TOP-LEFT       |    TOP-RIGHT      |  rows [0, midRow)
 *   +-------------------+-------------------+
 *   |   BOTTOM-LEFT     |   BOTTOM-RIGHT    |  rows [midRow, height)
 *   +-------------------+-------------------+
 *
 * For odd dimensions the right/bottom half receives the extra column/row.
 */
public class QuadrantResult {

    private final int width;
    private final int height;
    private final int topLeft;
    private final int topRight;
    private final int bottomLeft;
    private final int bottomRight;

    public QuadrantResult(int width, int height,
                          int topLeft, int topRight,
                          int bottomLeft, int bottomRight) {
        this.width       = width;
        this.height      = height;
        this.topLeft     = topLeft;
        this.topRight    = topRight;
        this.bottomLeft  = bottomLeft;
        this.bottomRight = bottomRight;
    }

    public int getTopLeft()     { return topLeft; }
    public int getTopRight()    { return topRight; }
    public int getBottomLeft()  { return bottomLeft; }
    public int getBottomRight() { return bottomRight; }
    public int getTotal()       { return topLeft + topRight + bottomLeft + bottomRight; }

    @Override
    public String toString() {
        int midCol = width / 2;
        int midRow = height / 2;
        String tlRange = String.format("cols[0,%d) rows[0,%d)",   midCol, midRow);
        String trRange = String.format("cols[%d,%d) rows[0,%d)",  midCol, width, midRow);
        String blRange = String.format("cols[0,%d) rows[%d,%d)",  midCol, midRow, height);
        String brRange = String.format("cols[%d,%d) rows[%d,%d)", midCol, width, midRow, height);
        return String.format(
            "Object size : %d x %d  (midpoint col=%d, row=%d)%n" +
            "+------------------------------+------------------------------+%n" +
            "| TOP-LEFT     : %6d fails  | TOP-RIGHT    : %6d fails  |%n" +
            "| %-28s | %-28s |%n" +
            "+------------------------------+------------------------------+%n" +
            "| BOTTOM-LEFT  : %6d fails  | BOTTOM-RIGHT : %6d fails  |%n" +
            "| %-28s | %-28s |%n" +
            "+------------------------------+------------------------------+%n" +
            "  Total failures: %d / %d",
            width, height, midCol, midRow,
            topLeft,  topRight,  tlRange, trRange,
            bottomLeft, bottomRight, blRange, brRange,
            getTotal(), width * height
        );
    }
}
