package com.quadrant;

/**
 * Demonstrates the QuadrantAnalyzer with several test scenarios.
 *
 * Compression format reminder:
 *   [2-byte repeat count][2-byte 16-bit pattern]
 *   FF FF 00 00  →  pattern 0x0000 repeated 65535 times
 */
public class Main {

    public static void main(String[] args) {

        // ----------------------------------------------------------------
        // Example 1: 4x4 grid, all pass
        //   1 repetition of pattern 0x0000 → 16 zero bits → all pass
        // ----------------------------------------------------------------
        System.out.println("=== Example 1: 4x4, all pass ===");
        byte[] ex1 = {0x00, 0x01, 0x00, 0x00};
        System.out.println(QuadrantAnalyzer.analyze(ex1, 4, 4));
        System.out.println();

        // ----------------------------------------------------------------
        // Example 2: 4x4 grid, top-left quadrant all fail
        //
        //   Grid layout (row-major, MSB first in each 16-bit pattern):
        //     bit 15 14 13 12 | 11 10  9  8 |  7  6  5  4 |  3  2  1  0
        //     row  0  0  0  0 |  1  1  1  1 |  2  2  2  2 |  3  3  3  3
        //     col  0  1  2  3 |  0  1  2  3 |  0  1  2  3 |  0  1  2  3
        //
        //   Top-left quadrant: col < 2, row < 2 → bits at (r,c): (0,0)(0,1)(1,0)(1,1)
        //   Bit indices: 15,14,11,10  → pattern = 1100 1100 0000 0000 = 0xCC00
        //   1 repetition → 0x00 0x01 0xCC 0x00
        // ----------------------------------------------------------------
        System.out.println("=== Example 2: 4x4, top-left quadrant all fail ===");
        byte[] ex2 = {0x00, 0x01, (byte) 0xCC, 0x00};
        System.out.println(QuadrantAnalyzer.analyze(ex2, 4, 4));
        System.out.println();

        // ----------------------------------------------------------------
        // Example 3: 4x4 grid, bottom-right quadrant all fail
        //
        //   Bottom-right: col >= 2, row >= 2 → bits at (r,c): (2,2)(2,3)(3,2)(3,3)
        //   Bit indices: 5,4,1,0  → pattern = 0000 0000 0011 0011 = 0x0033
        // ----------------------------------------------------------------
        System.out.println("=== Example 3: 4x4, bottom-right quadrant all fail ===");
        byte[] ex3 = {0x00, 0x01, 0x00, 0x33};
        System.out.println(QuadrantAnalyzer.analyze(ex3, 4, 4));
        System.out.println();

        // ----------------------------------------------------------------
        // Example 4: 3x4 grid (odd width), some scattered failures
        //
        //   Grid is 3 cols × 4 rows = 12 bits.
        //   We use 1 repetition of 16-bit pattern; only first 12 bits are used.
        //
        //   Pattern layout (MSB first):
        //     bit 15 14 13 | 12 11 10 |  9  8  7 |  6  5  4  | 3 2 1 0 (padding)
        //     row  0  0  0 |  1  1  1 |  2  2  2 |  3  3  3  |
        //     col  0  1  2 |  0  1  2 |  0  1  2 |  0  1  2  |
        //
        //   midCol=1, midRow=2
        //   Let's fail: (0,0), (1,2), (2,0), (3,1)
        //     bit positions: 15, 10, 9, 5
        //     pattern = 1000 0010 1100 0100 ... wait let me redo:
        //
        //   bit 15→(0,0)=1, 14→(0,1)=0, 13→(0,2)=0
        //   bit 12→(1,0)=0, 11→(1,1)=0, 10→(1,2)=1
        //   bit  9→(2,0)=1,  8→(2,1)=0,  7→(2,2)=0
        //   bit  6→(3,0)=0,  5→(3,1)=1,  4→(3,2)=0  (bits 3-0 = padding)
        //   pattern = 1000 0010 1100 0100 = 0x82C4  (wait, let me recalculate)
        //   bit15=1,14=0,13=0,12=0,11=0,10=1,9=1,8=0,7=0,6=0,5=1,4=0,3=0,2=0,1=0,0=0
        //   = 1000 0011 0001 0000 ... hmm, let me just use hex carefully:
        //   bits: 1 0 0 0 | 0 1 1 0 | 0 0 1 0 | 0 0 0 0
        //   = 0x8620
        //   Failures: (0,0)→TL, (1,2)→TR, (2,0)→BL, (3,1)→BR → one each
        // ----------------------------------------------------------------
        System.out.println("=== Example 4: 3x4 (odd width), one failure per quadrant ===");
        byte[] ex4 = {0x00, 0x01, (byte) 0x86, 0x20};
        System.out.println(QuadrantAnalyzer.analyze(ex4, 3, 4));
        System.out.println();

        // ----------------------------------------------------------------
        // Example 5: large object — FF FF 00 00 (all pass, 65535 repetitions)
        //   Decompressed size: 65535 * 16 = 1,048,560 bits
        //   Object size: 1024 x 1024 = 1,048,576  → slightly more bits available, ok
        // ----------------------------------------------------------------
        System.out.println("=== Example 5: large object 1024x1020 with all pass ===");
        byte[] ex5 = {(byte) 0xFF, (byte) 0xFF, 0x00, 0x00};
        System.out.println(QuadrantAnalyzer.analyze(ex5, 1024, 1020));
    }
}
