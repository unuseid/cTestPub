package com.quadrant;

/**
 * Analyzes RLE-compressed test data and counts failures per quadrant.
 *
 * <h3>Compression format</h3>
 * Each 4-byte record: {@code [uint16 repeatCount][uint16 pattern]}
 * <ul>
 *   <li>Repeat count: big-endian unsigned 16-bit integer (0x0001 – 0xFFFF)</li>
 *   <li>Pattern: 16-bit value, MSB is the first output bit per repetition</li>
 * </ul>
 * Example: {@code FF FF 00 00} → pattern {@code 0x0000} repeated 65535 times
 *          → 65535 × 16 = 1,048,560 zero bits (all pass)
 *
 * <h3>Streaming design</h3>
 * The standard grid size (98304 × 131072 = ~12.8 billion bits) makes full
 * decompression into a boolean array infeasible (~12 GB RAM). This analyzer
 * instead processes each compressed record in-place, mapping bit ranges
 * directly to quadrant counters without materialising the decompressed data.
 *
 * <h3>Constraints</h3>
 * Width and height must be positive even integers (guaranteed by the caller).
 */
public class TestDataAnalyzer {

    /**
     * Analyzes the compressed data and returns per-quadrant failure counts.
     *
     * @param compressedData RLE bytes; length must be a multiple of 4
     * @param width          horizontal test-point count (positive, even)
     * @param height         vertical test-point count  (positive, even)
     * @return {@link QuadrantResult} with {@code long} failure counts per quadrant
     */
    public static QuadrantResult analyze(byte[] compressedData, int width, int height) {
        validate(compressedData, width, height);

        final long midCol  = width  / 2L;
        final long midRow  = height / 2L;
        final long maxBits = (long) width * height;

        long topLeft = 0, topRight = 0, bottomLeft = 0, bottomRight = 0;
        long cursor = 0;

        for (int i = 0; i < compressedData.length && cursor < maxBits; i += 4) {
            int repeatCount = readUInt16(compressedData, i);
            int pattern     = readUInt16(compressedData, i + 2);

            if (repeatCount == 0) continue;

            long recordStart = cursor;
            long recordEnd   = Math.min(cursor + (long) repeatCount * 16, maxBits);

            if (pattern == 0) {
                cursor = recordEnd;  // all-pass record: skip without per-bit work
                continue;
            }

            // Process one row-segment at a time so we never touch memory proportional
            // to the full decompressed size.
            while (cursor < recordEnd) {
                long row      = cursor / width;
                long colStart = cursor % width;
                long segEnd   = Math.min((row + 1L) * width, recordEnd);
                long segLen   = segEnd - cursor;
                long offset   = cursor - recordStart;
                boolean isTop = row < midRow;

                // Split at the horizontal midpoint
                long leftLen  = Math.max(0L, Math.min(midCol - colStart, segLen));
                long rightLen = segLen - leftLen;

                if (leftLen > 0) {
                    long f = countFailures(pattern, offset, leftLen);
                    if (isTop) topLeft    += f;
                    else       bottomLeft += f;
                }
                if (rightLen > 0) {
                    long f = countFailures(pattern, offset + leftLen, rightLen);
                    if (isTop) topRight    += f;
                    else       bottomRight += f;
                }

                cursor = segEnd;
            }
        }

        return new QuadrantResult(width, height, topLeft, topRight, bottomLeft, bottomRight);
    }

    // -------------------------------------------------------------------------
    // Bit-counting helpers
    // -------------------------------------------------------------------------

    /**
     * Counts set bits (failures) in a slice of the repeating-pattern stream.
     *
     * The stream is formed by repeating {@code pattern} indefinitely.
     * Within each 16-bit repetition, bit 15 of {@code pattern} is output first.
     *
     * @param pattern     16-bit pattern
     * @param startOffset first bit index within the record's output stream
     * @param length      number of bits to examine
     * @return number of set bits in the slice
     */
    private static long countFailures(int pattern, long startOffset, long length) {
        if (length <= 0 || pattern == 0) return 0;

        // Position within the current 16-bit repetition (0 = MSB side)
        int  patBit    = (int)(startOffset % 16);
        long count     = 0;
        long remaining = length;

        // Partial leading repetition
        if (patBit != 0) {
            int take = (int) Math.min(16 - patBit, remaining);
            count     += countPatternBits(pattern, patBit, patBit + take - 1);
            remaining -= take;
        }

        // Full repetitions
        long fullReps = remaining / 16;
        count     += fullReps * Integer.bitCount(pattern);
        remaining %= 16;

        // Partial trailing repetition
        if (remaining > 0) {
            count += countPatternBits(pattern, 0, (int) remaining - 1);
        }

        return count;
    }

    /**
     * Counts set bits in output positions [{@code s}, {@code e}] (inclusive)
     * of a single 16-bit pattern repetition.
     *
     * Output position 0 corresponds to bit 15 (MSB) of {@code pattern};
     * output position 15 corresponds to bit 0 (LSB).
     */
    private static int countPatternBits(int pattern, int s, int e) {
        int shift = 15 - e;
        int mask  = (1 << (e - s + 1)) - 1;
        return Integer.bitCount((pattern >> shift) & mask);
    }

    private static int readUInt16(byte[] data, int off) {
        return ((data[off] & 0xFF) << 8) | (data[off + 1] & 0xFF);
    }

    private static void validate(byte[] data, int width, int height) {
        if (data == null || data.length == 0)
            throw new IllegalArgumentException("Compressed data must not be null or empty");
        if (data.length % 4 != 0)
            throw new IllegalArgumentException(
                "Compressed data length must be a multiple of 4, got: " + data.length);
        if (width <= 0 || width % 2 != 0)
            throw new IllegalArgumentException(
                "Width must be a positive even integer, got: " + width);
        if (height <= 0 || height % 2 != 0)
            throw new IllegalArgumentException(
                "Height must be a positive even integer, got: " + height);
    }
}
