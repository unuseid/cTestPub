package com.quadrant;

/**
 * Analyzes RLE-compressed test data for a rectangular object and counts
 * failures in each of the four quadrants.
 *
 * Usage:
 *   QuadrantResult result = QuadrantAnalyzer.analyze(compressedData, width, height);
 */
public class QuadrantAnalyzer {

    /**
     * Decompresses the input data and counts failures per quadrant.
     *
     * @param compressedData RLE-compressed test data (multiple of 4 bytes)
     * @param width          number of test columns (horizontal size of the object)
     * @param height         number of test rows    (vertical size of the object)
     * @return               failure counts for each quadrant
     */
    public static QuadrantResult analyze(byte[] compressedData, int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                "Width and height must be positive, got: " + width + "x" + height
            );
        }

        boolean[] bits = TestDataDecompressor.decompress(compressedData);

        int totalPoints = width * height;
        if (bits.length < totalPoints) {
            throw new IllegalArgumentException(String.format(
                "Decompressed data has %d bits, but %dx%d grid requires %d",
                bits.length, width, height, totalPoints
            ));
        }

        int midCol = width  / 2;
        int midRow = height / 2;

        int topLeft = 0, topRight = 0, bottomLeft = 0, bottomRight = 0;

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                if (!bits[row * width + col]) {
                    continue; // pass (0) — skip
                }
                // fail (1)
                if (row < midRow && col < midCol)       topLeft++;
                else if (row < midRow)                  topRight++;
                else if (col < midCol)                  bottomLeft++;
                else                                    bottomRight++;
            }
        }

        return new QuadrantResult(width, height, topLeft, topRight, bottomLeft, bottomRight);
    }
}
