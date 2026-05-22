package com.quadrant;

/**
 * Decompresses RLE-encoded test data.
 *
 * Compression format: each 4-byte record = [2-byte repeat count][2-byte pattern]
 * Example: FF FF 00 00 → pattern 0x0000 repeated 0xFFFF times → 0xFFFF * 16 zero bits
 */
public class TestDataDecompressor {

    /**
     * Decompresses the given byte array into a flat boolean array.
     * Each boolean represents one test point: false = pass (0), true = fail (1).
     *
     * @param compressedData byte array whose length must be a multiple of 4
     * @return decompressed bit array (MSB first within each 16-bit pattern)
     */
    public static boolean[] decompress(byte[] compressedData) {
        if (compressedData == null || compressedData.length == 0) {
            return new boolean[0];
        }
        if (compressedData.length % 4 != 0) {
            throw new IllegalArgumentException(
                "Compressed data length must be a multiple of 4, got: " + compressedData.length
            );
        }

        long totalBits = calculateTotalBits(compressedData);
        if (totalBits > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Decompressed data too large: " + totalBits + " bits");
        }

        boolean[] bits = new boolean[(int) totalBits];
        int idx = 0;

        for (int i = 0; i < compressedData.length; i += 4) {
            int repeatCount = readUInt16(compressedData, i);
            int pattern     = readUInt16(compressedData, i + 2);

            for (int r = 0; r < repeatCount; r++) {
                for (int bit = 15; bit >= 0; bit--) {
                    bits[idx++] = ((pattern >> bit) & 1) == 1;
                }
            }
        }

        return bits;
    }

    private static long calculateTotalBits(byte[] data) {
        long total = 0;
        for (int i = 0; i < data.length; i += 4) {
            total += (long) readUInt16(data, i) * 16;
        }
        return total;
    }

    private static int readUInt16(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }
}
