package com.quadrant;

import java.io.IOException;
import java.nio.file.*;

/**
 * Demonstrates the full pipeline: generate → save → load → analyze.
 *
 * Small examples are verified inline; the standard-size demo generates files
 * to disk and measures analysis throughput.
 *
 * Run: java com.quadrant.Main
 */
public class Main {

    public static void main(String[] args) throws IOException {
        smallExamples();
        standardSizeDemo();
    }

    // -------------------------------------------------------------------------
    // Small verifiable examples
    // -------------------------------------------------------------------------

    private static void smallExamples() {
        System.out.println("════════════════════════════════════════");
        System.out.println(" Small examples (manually verifiable)");
        System.out.println("════════════════════════════════════════");

        // 4 × 4, all pass  — 1 rep of 0x0000 → 16 zero bits
        run("4×4  all pass",
                new byte[]{0x00, 0x01, 0x00, 0x00}, 4, 4);

        // 4 × 4, top-left quadrant all fail
        // Grid (row-major, MSB first per 16-bit rep):
        //   (0,0)=b15 (0,1)=b14 (0,2)=b13 (0,3)=b12
        //   (1,0)=b11 (1,1)=b10 …
        // TL = col<2, row<2 → bits 15,14,11,10 → pattern = 1100_1100_0000_0000 = 0xCC00
        run("4×4  top-left fail",
                new byte[]{0x00, 0x01, (byte) 0xCC, 0x00}, 4, 4);

        // 4 × 4, bottom-right quadrant all fail
        // BR = col≥2, row≥2 → bits 5,4,1,0 → pattern = 0000_0000_0011_0011 = 0x0033
        run("4×4  bottom-right fail",
                new byte[]{0x00, 0x01, 0x00, 0x33}, 4, 4);

        // 6 × 4  (even width, NOT a multiple of 16), 2 repetitions of 0x8620
        // Pattern 0x8620 = 1000_0110_0010_0000  (MSB = first output bit)
        // Decompressed stream (32 bits, first 24 used for 6×4):
        //   bits 00–05 → row 0: 1,0,0,0,0,1  → fail (0,0) and (0,5)
        //   bits 06–11 → row 1: 1,0,0,0,1,0  → fail (1,0) and (1,4)
        //   bits 12–17 → row 2: 0,0,0,0,1,0  → fail (2,4)
        //   bits 18–23 → row 3: 0,0,0,1,1,0  → fail (3,3) and (3,4)
        // midCol=3, midRow=2 → TL:2  TR:2  BL:0  BR:3  total:7
        run("6×4  mixed (odd col-width, even dims)",
                new byte[]{0x00, 0x02, (byte) 0x86, 0x20}, 6, 4);

        System.out.println();
    }

    private static void run(String label, byte[] data, int w, int h) {
        var result = TestDataAnalyzer.analyze(data, w, h);
        System.out.printf("%n── %s ──%n%s%n", label, result);
    }

    // -------------------------------------------------------------------------
    // Standard-size demo: generate files, load, analyze, time
    // -------------------------------------------------------------------------

    private static void standardSizeDemo() throws IOException {
        System.out.println("════════════════════════════════════════");
        System.out.printf(" Standard size  %,d × %,d%n",
                TestDataGenerator.STANDARD_WIDTH, TestDataGenerator.STANDARD_HEIGHT);
        System.out.println("════════════════════════════════════════");

        var dir = Path.of("test-data");
        Files.createDirectories(dir);

        record Demo(String label, Path file, Runnable gen) {}

        var demos = new Demo[]{
            new Demo("All-pass",
                dir.resolve("standard_all_pass.rle"),
                sneaky(() -> TestDataGenerator.generateAllPass(
                        TestDataGenerator.STANDARD_WIDTH,
                        TestDataGenerator.STANDARD_HEIGHT,
                        dir.resolve("standard_all_pass.rle")))),

            new Demo("Top-left quadrant fail",
                dir.resolve("standard_tl_fail.rle"),
                sneaky(() -> TestDataGenerator.generateQuadrantFail(
                        TestDataGenerator.STANDARD_WIDTH,
                        TestDataGenerator.STANDARD_HEIGHT,
                        true, true,
                        dir.resolve("standard_tl_fail.rle")))),

            new Demo("Random 15% (seed=42)",
                dir.resolve("standard_random_15pct.rle"),
                sneaky(() -> TestDataGenerator.generateRandom(
                        TestDataGenerator.STANDARD_WIDTH,
                        TestDataGenerator.STANDARD_HEIGHT,
                        0.15, 42L,
                        dir.resolve("standard_random_15pct.rle")))),
        };

        System.out.printf("%n%-30s  %10s  %8s  %8s%n",
                "Scenario", "File", "Gen", "Analyze");
        System.out.println("-".repeat(62));

        for (var d : demos) {
            // Generate (skip if already exists)
            long genMs = 0;
            if (!Files.exists(d.file())) {
                long t0 = System.nanoTime();
                d.gen().run();
                genMs = (System.nanoTime() - t0) / 1_000_000;
            } else {
                genMs = -1; // cached
            }

            // Load
            byte[] compressed = Files.readAllBytes(d.file());

            // Analyze
            long t0 = System.nanoTime();
            var result = TestDataAnalyzer.analyze(
                    compressed,
                    TestDataGenerator.STANDARD_WIDTH,
                    TestDataGenerator.STANDARD_HEIGHT);
            long anaMs = (System.nanoTime() - t0) / 1_000_000;

            String genStr = genMs < 0 ? "(cached)" : genMs + " ms";
            System.out.printf("%-30s  %,8d B  %8s  %5d ms%n",
                    d.label(), Files.size(d.file()), genStr, anaMs);
            System.out.println(result);
            System.out.println();
        }
    }

    // Wraps a checked lambda into an unchecked Runnable (only for demo use)
    private static Runnable sneaky(ThrowingRunnable r) {
        return () -> { try { r.run(); } catch (IOException e) { throw new RuntimeException(e); } };
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws IOException; }
}
