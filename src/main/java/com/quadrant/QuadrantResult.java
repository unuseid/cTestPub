package com.quadrant;

/**
 * Failure counts per quadrant for a tested rectangular object.
 *
 * Quadrant layout (width and height are guaranteed even):
 *
 *   cols [0, midCol)          cols [midCol, width)
 *   +-----------------------+------------------------+
 *   |      TOP-LEFT         |      TOP-RIGHT         |  rows [0, midRow)
 *   +-----------------------+------------------------+
 *   |     BOTTOM-LEFT       |     BOTTOM-RIGHT       |  rows [midRow, height)
 *   +-----------------------+------------------------+
 *
 * Failure counts use {@code long} because the standard size (98304 x 131072)
 * can produce up to ~12.8 billion failures, exceeding {@link Integer#MAX_VALUE}.
 */
public record QuadrantResult(
        int  width,
        int  height,
        long topLeft,
        long topRight,
        long bottomLeft,
        long bottomRight
) {
    public long total() {
        return topLeft + topRight + bottomLeft + bottomRight;
    }

    @Override
    public String toString() {
        int midCol = width  / 2;
        int midRow = height / 2;
        var tlRange = "cols[0,%d) rows[0,%d)".formatted(midCol, midRow);
        var trRange = "cols[%d,%d) rows[0,%d)".formatted(midCol, width, midRow);
        var blRange = "cols[0,%d) rows[%d,%d)".formatted(midCol, midRow, height);
        var brRange = "cols[%d,%d) rows[%d,%d)".formatted(midCol, width, midRow, height);

        return """
                Object size : %,d x %,d  (midpoint col=%,d, row=%,d)
                +--------------------------------+--------------------------------+
                | TOP-LEFT     : %,14d  | TOP-RIGHT    : %,14d  |
                | %-30s | %-30s |
                +--------------------------------+--------------------------------+
                | BOTTOM-LEFT  : %,14d  | BOTTOM-RIGHT : %,14d  |
                | %-30s | %-30s |
                +--------------------------------+--------------------------------+
                  Total failures: %,d / %,d""".formatted(
                width, height, midCol, midRow,
                topLeft,    topRight,    tlRange, trRange,
                bottomLeft, bottomRight, blRange, brRange,
                total(), (long) width * height
        );
    }
}
