package com.quadrant;

import java.util.Arrays;

/**
 * Decompresses RLE-encoded test data into a flat byte-array bitfield.
 *
 * <h3>Bit layout</h3>
 * The returned {@code byte[]} stores bits in MSB-first order:
 * test-point at (row, col) occupies bit {@code row * width + col}, which maps to
 * <pre>
 *   byte index : (row * width + col) / 8
 *   bit  index : 7 - ((row * width + col) % 8)   // bit 7 = MSB
 * </pre>
 * This matches the natural 16-bit pattern order: each compressed pattern byte-pair
 * writes directly to two consecutive bytes in the output ({@code hi} byte first).
 *
 * <h3>Memory</h3>
 * Standard size 98 304 × 131 072 → 1.5 GB.  Requires {@code -Xmx4g} or larger.
 *
 * <h3>Compression format</h3>
 * 4-byte records: {@code [uint16 repeatCount][uint16 16-bit pattern]}, MSB first per field.
 */
public class TestDataDecompressor {

    /**
     * Decompresses the RLE data into a bitfield byte array.
     *
     * @param compressed RLE bytes (length must be a multiple of 4)
     * @param width      number of test columns (positive, even)
     * @param height     number of test rows    (positive, even)
     * @return           MSB-first packed bitfield of size {@code ceil(width * height / 8)}
     */
    public static byte[] decompress(byte[] compressed, int width, int height) {
        validate(compressed, width, height);

        long totalBits = (long) width * height;
        // ceil(totalBits / 8): fits in int for standard size (1 610 612 736 < Integer.MAX_VALUE)
        int  byteCount = (int)((totalBits + 7L) / 8L);
        byte[] grid    = new byte[byteCount];   // Java initialises to 0 (= all-pass)

        long bitPos = 0;   // always a multiple of 16 at record boundaries

        for (int i = 0; i < compressed.length && bitPos < totalBits; i += 4) {
            int repeatCount = readUInt16(compressed, i);
            int pattern     = readUInt16(compressed, i + 2);

            if (repeatCount == 0) continue;

            long bitsAvailable = Math.min((long) repeatCount * 16L, totalBits - bitPos);
            long fullReps      = bitsAvailable / 16L;
            int  remainder     = (int)(bitsAvailable % 16L);

            // bitPos is a multiple of 16 → bytePos is a multiple of 2 (safe int cast)
            int bytePos = (int)(bitPos / 8L);

            if (pattern == 0x0000) {
                // Grid is already zero-initialised; no writes needed.
            } else if (pattern == 0xFFFF) {
                // Fill both bytes of every repetition with 0xFF.
                Arrays.fill(grid, bytePos, bytePos + (int)(fullReps * 2L), (byte) 0xFF);
            } else {
                // Write the two pattern bytes for each repetition.
                byte hi  = (byte)(pattern >> 8);
                byte lo  = (byte)(pattern & 0xFF);
                int  pos = bytePos;
                int  end = bytePos + (int)(fullReps * 2L);
                while (pos < end) {
                    grid[pos++] = hi;
                    grid[pos++] = lo;
                }
            }
            bitPos += fullReps * 16L;

            // Partial last repetition — only possible when totalBits % 16 != 0.
            // bitPos and the loop index are tracked separately to keep the int cast safe.
            if (remainder > 0 && pattern != 0x0000) {
                for (int bit = 15; bit >= 16 - remainder; bit--) {
                    if (((pattern >> bit) & 1) == 1) {
                        int byteIdx = (int)(bitPos / 8L);
                        int bitOff  = (int)(bitPos % 8L);
                        grid[byteIdx] |= (byte)(1 << (7 - bitOff));
                    }
                    bitPos++;
                }
            } else {
                bitPos += remainder;
            }
        }

        return grid;
    }

    // -------------------------------------------------------------------------

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
