package com.quadrant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Demonstrates the full pipeline:
 * <ol>
 *   <li>Generate compressed .rle file  ({@link TestDataGenerator})</li>
 *   <li>Load file into {@code byte[]}</li>
 *   <li>Decompress into MSB-first bitfield ({@link TestDataDecompressor})</li>
 *   <li>Analyze per-quadrant failures   ({@link QuadrantAnalyzer})</li>
 * </ol>
 *
 * Run: {@code java com.quadrant.Main}
 * (For standard-size tests, add {@code -Xmx4g} to the JVM flags.)
 */
public class Main {

    public static void main(String[] args) throws IOException {
        runSmallExamples();
        runStandardSizeDemo();
    }

    // -------------------------------------------------------------------------
    // Small, manually verifiable examples
    // -------------------------------------------------------------------------

    private static void runSmallExamples() {
        System.out.println("════════════════════════════════════════");
        System.out.println(" Small examples (manually verifiable)");
        System.out.println("════════════════════════════════════════");

        // 4 × 4, all pass  — 1 rep of 0x0000 → 16 zero bits
        analyzeInline("4×4  all pass",
                new byte[]{0x00, 0x01, 0x00, 0x00}, 4, 4);

        // 4 × 4, top-left quadrant all fail
        // Grid layout (row-major, MSB=bit 15 first in each 16-bit rep):
        //   (0,0)→b15 (0,1)→b14 (0,2)→b13 (0,3)→b12
        //   (1,0)→b11 (1,1)→b10  …
        // TL = col<2, row<2 → bits 15,14,11,10 → 1100_1100_0000_0000 = 0xCC00
        analyzeInline("4×4  top-left fail",
                new byte[]{0x00, 0x01, (byte) 0xCC, 0x00}, 4, 4);

        // 4 × 4, bottom-right quadrant all fail
        // BR = col≥2, row≥2 → bits 5,4,1,0 → 0000_0000_0011_0011 = 0x0033
        analyzeInline("4×4  bottom-right fail",
                new byte[]{0x00, 0x01, 0x00, 0x33}, 4, 4);

        // 6 × 4  (even width, NOT a multiple of 16), 2 reps of 0x8620
        // 0x8620 = 1000_0110_0010_0000  (MSB = first output bit)
        // Decompressed stream (32 bits, first 24 used for 6×4 grid):
        //   bits 00–05 → row 0: 1,0,0,0,0,1  → fail (0,0) and (0,5)
        //   bits 06–11 → row 1: 1,0,0,0,1,0  → fail (1,0) and (1,4)
        //   bits 12–17 → row 2: 0,0,0,0,1,0  → fail (2,4)
        //   bits 18–23 → row 3: 0,0,0,1,1,0  → fail (3,3) and (3,4)
        // midCol=3, midRow=2 → TL:2  TR:2  BL:0  BR:3  total:7
        analyzeInline("6×4  mixed (even width, not ×16)",
                new byte[]{0x00, 0x02, (byte) 0x86, 0x20}, 6, 4);

        System.out.println();
    }

    private static void analyzeInline(String label, byte[] compressed, int width, int height) {
        byte[]        grid   = TestDataDecompressor.decompress(compressed, width, height);
        QuadrantResult result = QuadrantAnalyzer.analyze(grid, width, height);
        System.out.printf("%n── %s ──%n%s%n", label, result);
    }

    // -------------------------------------------------------------------------
    // Standard-size demo: generate → load → decompress → analyze
    // -------------------------------------------------------------------------

    private static void runStandardSizeDemo() throws IOException {
        System.out.println("════════════════════════════════════════");
        System.out.printf(" Standard size  %,d × %,d%n",
                TestDataGenerator.STANDARD_WIDTH, TestDataGenerator.STANDARD_HEIGHT);
        System.out.println("════════════════════════════════════════");

        Path dir = Path.of("test-data");
        Files.createDirectories(dir);

        runStandardScenario("All-pass",
                dir.resolve("standard_all_pass.rle"),
                () -> TestDataGenerator.generateAllPass(
                        TestDataGenerator.STANDARD_WIDTH,
                        TestDataGenerator.STANDARD_HEIGHT,
                        dir.resolve("standard_all_pass.rle")));

        runStandardScenario("Top-left quadrant fail",
                dir.resolve("standard_tl_fail.rle"),
                () -> TestDataGenerator.generateQuadrantFail(
                        TestDataGenerator.STANDARD_WIDTH,
                        TestDataGenerator.STANDARD_HEIGHT,
                        true, true,
                        dir.resolve("standard_tl_fail.rle")));

        runStandardScenario("Random 15% failure (seed=42)",
                dir.resolve("standard_random_15pct.rle"),
                () -> TestDataGenerator.generateRandom(
                        TestDataGenerator.STANDARD_WIDTH,
                        TestDataGenerator.STANDARD_HEIGHT,
                        0.15, 42L,
                        dir.resolve("standard_random_15pct.rle")));
    }

    private static void runStandardScenario(String label, Path file, ThrowingRunnable gen)
            throws IOException {

        // Generate file if it does not yet exist
        if (!Files.exists(file)) {
            long t0 = System.nanoTime();
            gen.run();
            System.out.printf("[gen]  %-36s  %,d B  (%d ms)%n",
                    label, Files.size(file), (System.nanoTime() - t0) / 1_000_000L);
        }

        // Load compressed bytes
        byte[] compressed = Files.readAllBytes(file);

        // Decompress → byte[] bitfield (1.5 GB for standard size)
        long t0 = System.nanoTime();
        byte[] grid = TestDataDecompressor.decompress(
                compressed, TestDataGenerator.STANDARD_WIDTH, TestDataGenerator.STANDARD_HEIGHT);
        long decompMs = (System.nanoTime() - t0) / 1_000_000L;

        // Analyze using bit operations on the byte array
        t0 = System.nanoTime();
        QuadrantResult result = QuadrantAnalyzer.analyze(
                grid, TestDataGenerator.STANDARD_WIDTH, TestDataGenerator.STANDARD_HEIGHT);
        long analyzeMs = (System.nanoTime() - t0) / 1_000_000L;

        System.out.printf("%n── %s ──%n", label);
        System.out.printf("   Compressed : %,d bytes | Decompressed : %,d bytes (%.2f GB)%n",
                compressed.length, grid.length, grid.length / 1073741824.0);
        System.out.printf("   Decompress : %d ms  |  Analyze : %d ms%n", decompMs, analyzeMs);
        System.out.println(result);
        System.out.println();
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws IOException; }
}
