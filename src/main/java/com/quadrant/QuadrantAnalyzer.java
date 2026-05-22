package com.quadrant;

/**
 * Counts failures per quadrant by performing bit operations on a decompressed
 * {@code byte[]} bitfield produced by {@link TestDataDecompressor}.
 *
 * <h3>Bit layout expected</h3>
 * Test-point (row, col) → {@code grid[p/8]}, bit {@code 7-(p%8)} where {@code p = row*width+col}.
 *
 * <h3>Two execution paths</h3>
 * <ul>
 *   <li><b>Fast path</b> ({@code width % 16 == 0}, e.g. standard 98 304):
 *       rows and the half-row boundary both align to byte boundaries.
 *       Uses {@link Long#bitCount} over 8-byte windows for maximum throughput.</li>
 *   <li><b>General path</b> (any even width):
 *       Counts bits with byte-level {@link Integer#bitCount} where possible,
 *       falling back to individual bit access at unaligned row boundaries.</li>
 * </ul>
 */
public class QuadrantAnalyzer {

    /**
     * @param grid   MSB-first bitfield from {@link TestDataDecompressor#decompress}
     * @param width  number of test columns (positive, even)
     * @param height number of test rows    (positive, even)
     */
    public static QuadrantResult analyze(byte[] grid, int width, int height) {
        validate(grid, width, height);

        int midCol = width  / 2;
        int midRow = height / 2;

        long topLeft     = 0;
        long topRight    = 0;
        long bottomLeft  = 0;
        long bottomRight = 0;

        if (width % 16 == 0) {
            // Fast path: every row starts on a byte boundary; the half-row
            // boundary (midCol = width/2, and width%16==0 → midCol%8==0) also
            // aligns to a byte.  Both halves are exactly halfBytes wide.
            int rowBytes  = width  / 8;
            int halfBytes = midCol / 8;

            for (int row = 0; row < height; row++) {
                // row * rowBytes ≤ 131071 * 12288 = 1 610 600 448 < Integer.MAX_VALUE
                int rowStart = (int)((long) row * rowBytes);

                long leftFails  = countBitsInByteRange(grid, rowStart,             halfBytes);
                long rightFails = countBitsInByteRange(grid, rowStart + halfBytes, halfBytes);

                if (row < midRow) {
                    topLeft    += leftFails;
                    topRight   += rightFails;
                } else {
                    bottomLeft  += leftFails;
                    bottomRight += rightFails;
                }
            }
        } else {
            // General path: rows may not start on byte boundaries.
            for (int row = 0; row < height; row++) {
                long rowBitStart = (long) row * width;

                long leftFails  = countBitsInRange(grid, rowBitStart,          rowBitStart + midCol);
                long rightFails = countBitsInRange(grid, rowBitStart + midCol, rowBitStart + width);

                if (row < midRow) {
                    topLeft    += leftFails;
                    topRight   += rightFails;
                } else {
                    bottomLeft  += leftFails;
                    bottomRight += rightFails;
                }
            }
        }

        return new QuadrantResult(width, height, topLeft, topRight, bottomLeft, bottomRight);
    }

    // -------------------------------------------------------------------------
    // Bit-counting helpers
    // -------------------------------------------------------------------------

    /**
     * Counts set bits in {@code grid[startByte .. startByte+byteCount)}.
     * Aligns to 8-byte boundaries, then processes 8-byte longs for throughput.
     */
    private static long countBitsInByteRange(byte[] grid, int startByte, int byteCount) {
        long count = 0;
        int  pos   = startByte;
        int  end   = startByte + byteCount;

        // Align to 8-byte boundary
        while (pos < end && (pos & 7) != 0) {
            count += Integer.bitCount(grid[pos] & 0xFF);
            pos++;
        }

        // Process 8 bytes at a time using Long.bitCount
        while (pos + 8 <= end) {
            long word = ((long)(grid[pos    ] & 0xFF) << 56)
                      | ((long)(grid[pos + 1] & 0xFF) << 48)
                      | ((long)(grid[pos + 2] & 0xFF) << 40)
                      | ((long)(grid[pos + 3] & 0xFF) << 32)
                      | ((long)(grid[pos + 4] & 0xFF) << 24)
                      | ((long)(grid[pos + 5] & 0xFF) << 16)
                      | ((long)(grid[pos + 6] & 0xFF) <<  8)
                      |  (long)(grid[pos + 7] & 0xFF);
            count += Long.bitCount(word);
            pos   += 8;
        }

        // Remaining bytes
        while (pos < end) {
            count += Integer.bitCount(grid[pos] & 0xFF);
            pos++;
        }

        return count;
    }

    /**
     * Counts set bits in the bit range [{@code startBit}, {@code endBit}).
     * Handles arbitrary alignment; falls back to byte-level counting in the middle.
     */
    private static long countBitsInRange(byte[] grid, long startBit, long endBit) {
        long count = 0;
        long bit   = startBit;

        // Align to byte boundary
        while (bit < endBit && (bit % 8L) != 0L) {
            count += (grid[(int)(bit / 8L)] >> (7 - (int)(bit % 8L))) & 1;
            bit++;
        }

        // Full bytes — advance `bit` in lockstep with `b` to avoid resetting backwards.
        long endByte = endBit / 8L;
        for (long b = bit / 8L; b < endByte; b++, bit += 8L) {
            count += Integer.bitCount(grid[(int) b] & 0xFF);
        }

        // Trailing bits
        while (bit < endBit) {
            count += (grid[(int)(bit / 8L)] >> (7 - (int)(bit % 8L))) & 1;
            bit++;
        }

        return count;
    }

    // -------------------------------------------------------------------------

    private static void validate(byte[] grid, int width, int height) {
        if (grid == null)
            throw new IllegalArgumentException("Grid must not be null");
        if (width <= 0 || width % 2 != 0)
            throw new IllegalArgumentException(
                "Width must be a positive even integer, got: " + width);
        if (height <= 0 || height % 2 != 0)
            throw new IllegalArgumentException(
                "Height must be a positive even integer, got: " + height);
        long needed = ((long) width * height + 7L) / 8L;
        if (grid.length < needed)
            throw new IllegalArgumentException(
                "Grid too small: need " + needed + " bytes for " + width + "×" + height
                + ", got " + grid.length);
    }
}
