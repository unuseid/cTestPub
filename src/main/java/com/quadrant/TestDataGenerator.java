package com.quadrant;

import java.io.*;
import java.nio.file.*;
import java.util.Random;

/**
 * Generates RLE-compressed test data files for {@link TestDataAnalyzer}.
 *
 * <h3>Output format</h3>
 * Binary file of 4-byte RLE records: {@code [uint16 repeatCount][uint16 pattern]}.
 * The analyzer consumes this format directly.
 *
 * <h3>Constraints</h3>
 * Width must be a positive multiple of 16 so that each 16-bit pattern aligns
 * within rows without spanning row boundaries.  The standard size (98304) satisfies
 * this requirement.  Height must be a positive even integer.
 *
 * <h3>Usage</h3>
 * {@code java com.quadrant.TestDataGenerator [output-dir]}
 * <br>Defaults to {@code ./test-data/} if no argument is given.
 */
public class TestDataGenerator {

    public static final int STANDARD_WIDTH  = 98304;
    public static final int STANDARD_HEIGHT = 131072;

    public static void main(String[] args) throws IOException {
        var outDir = args.length > 0 ? Path.of(args[0]) : Path.of("test-data");
        Files.createDirectories(outDir);

        System.out.printf("Generating standard test data (%,d × %,d)%n",
                STANDARD_WIDTH, STANDARD_HEIGHT);
        System.out.println("Output : " + outDir.toAbsolutePath());
        System.out.println();

        var scenarios = new Scenario[] {
            new Scenario("All-pass",                   outDir.resolve("standard_all_pass.rle")),
            new Scenario("All-fail",                   outDir.resolve("standard_all_fail.rle")),
            new Scenario("Top-left quadrant fail",     outDir.resolve("standard_tl_fail.rle")),
            new Scenario("Top-right quadrant fail",    outDir.resolve("standard_tr_fail.rle")),
            new Scenario("Bottom-left quadrant fail",  outDir.resolve("standard_bl_fail.rle")),
            new Scenario("Bottom-right quadrant fail", outDir.resolve("standard_br_fail.rle")),
            new Scenario("Random 15% failure (seed=42)", outDir.resolve("standard_random_15pct.rle")),
        };

        System.out.printf("%-38s  %12s  %6s%n", "Scenario", "File size", "Time");
        System.out.println("-".repeat(62));

        generate(scenarios[0], () -> generateAllPass(STANDARD_WIDTH, STANDARD_HEIGHT, scenarios[0].file()));
        generate(scenarios[1], () -> generateAllFail(STANDARD_WIDTH, STANDARD_HEIGHT, scenarios[1].file()));
        generate(scenarios[2], () -> generateQuadrantFail(STANDARD_WIDTH, STANDARD_HEIGHT, true,  true,  scenarios[2].file()));
        generate(scenarios[3], () -> generateQuadrantFail(STANDARD_WIDTH, STANDARD_HEIGHT, true,  false, scenarios[3].file()));
        generate(scenarios[4], () -> generateQuadrantFail(STANDARD_WIDTH, STANDARD_HEIGHT, false, true,  scenarios[4].file()));
        generate(scenarios[5], () -> generateQuadrantFail(STANDARD_WIDTH, STANDARD_HEIGHT, false, false, scenarios[5].file()));
        generate(scenarios[6], () -> generateRandom(STANDARD_WIDTH, STANDARD_HEIGHT, 0.15, 42L, scenarios[6].file()));
    }

    private record Scenario(String label, Path file) {}

    private static void generate(Scenario s, ThrowingRunnable r) {
        try {
            long t0 = System.nanoTime();
            r.run();
            long ms = (System.nanoTime() - t0) / 1_000_000;
            System.out.printf("%-38s  %,12d B  %4d ms%n", s.label(), Files.size(s.file()), ms);
        } catch (IOException e) {
            System.err.println("Failed: " + s.label() + " — " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws IOException; }

    // -------------------------------------------------------------------------
    // Public generation API
    // -------------------------------------------------------------------------

    /** Generates an all-pass (all bits = 0) dataset. */
    public static void generateAllPass(int width, int height, Path output) throws IOException {
        validateDims(width, height);
        try (var w = new RleWriter(output)) {
            w.addRepeat(0x0000, patternCount(width, height));
        }
    }

    /** Generates an all-fail (all bits = 1) dataset. */
    public static void generateAllFail(int width, int height, Path output) throws IOException {
        validateDims(width, height);
        try (var w = new RleWriter(output)) {
            w.addRepeat(0xFFFF, patternCount(width, height));
        }
    }

    /**
     * Generates a dataset where exactly one quadrant has all failures; the other
     * three quadrants pass.
     *
     * @param failTop  {@code true} → top half fails; {@code false} → bottom half
     * @param failLeft {@code true} → left half fails; {@code false} → right half
     */
    public static void generateQuadrantFail(
            int width, int height,
            boolean failTop, boolean failLeft,
            Path output) throws IOException {
        validateDims(width, height);

        int  ppr         = width / 16;   // 16-bit patterns per full row
        int  halfPpr     = ppr    / 2;   // patterns per half-row (width % 16 == 0, so exact)
        int  midRow      = height / 2;
        int  failStart   = failTop ? 0      : midRow;
        int  failEnd     = failTop ? midRow : height;
        long passPatterns = (long)(height / 2) * ppr;

        int leftPat  = failLeft ? 0xFFFF : 0x0000;  // pattern for left  half of fail rows
        int rightPat = failLeft ? 0x0000 : 0xFFFF;  // pattern for right half of fail rows

        try (var w = new RleWriter(output)) {
            if (failStart > 0) {
                // Pass rows precede fail rows (failTop == false)
                w.addRepeat(0x0000, passPatterns);
            }

            // Fail rows: each row = [halfPpr × leftPat][halfPpr × rightPat]
            // Row boundaries break consecutive runs unless the trailing half-row pattern
            // matches the leading pattern of the next row — the RleWriter merges these
            // automatically (e.g., trailing 0x0000 in top-left fail merges with pass rows).
            for (int row = failStart; row < failEnd; row++) {
                w.addRepeat(leftPat,  halfPpr);
                w.addRepeat(rightPat, halfPpr);
            }

            if (failStart == 0) {
                // Pass rows follow fail rows (failTop == true)
                w.addRepeat(0x0000, passPatterns);
            }
        }
    }

    /**
     * Generates a random dataset using a per-row uniform pattern.
     * Each row is assigned one random 16-bit pattern (same across all column blocks
     * in that row) with each bit set independently at probability {@code failRate}.
     * Consecutive rows sharing the same pattern are merged into one RLE record.
     *
     * <p>The maximum output size is {@code height × 4} bytes (one record per unique
     * row pattern), far smaller than a fully random cell-level dataset would be.
     *
     * @param failRate probability [0.0, 1.0] of failure for each test point
     * @param seed     RNG seed for reproducibility
     */
    public static void generateRandom(
            int width, int height,
            double failRate, long seed,
            Path output) throws IOException {
        validateDims(width, height);
        if (failRate < 0.0 || failRate > 1.0)
            throw new IllegalArgumentException("failRate must be in [0.0, 1.0]: " + failRate);

        int  ppr = width / 16;
        var  rng = new Random(seed);

        try (var w = new RleWriter(output)) {
            for (int row = 0; row < height; row++) {
                w.addRepeat(randomPattern(rng, failRate), ppr);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static int randomPattern(Random rng, double failRate) {
        int p = 0;
        for (int b = 0; b < 16; b++) {
            if (rng.nextDouble() < failRate) p |= (1 << b);
        }
        return p;
    }

    private static long patternCount(int width, int height) {
        return (long) width * height / 16;
    }

    private static void validateDims(int width, int height) {
        if (width <= 0 || width % 16 != 0)
            throw new IllegalArgumentException(
                "Width must be a positive multiple of 16 (standard: 98304), got: " + width);
        if (height <= 0 || height % 2 != 0)
            throw new IllegalArgumentException(
                "Height must be a positive even integer (standard: 131072), got: " + height);
    }

    // -------------------------------------------------------------------------
    // RLE output writer
    // -------------------------------------------------------------------------

    /**
     * Buffered RLE encoder.  Consecutive calls with the same pattern accumulate
     * into a single in-memory run; a new record is emitted when the pattern changes
     * or on {@link #close()}.  Large accumulated counts are split into multiple
     * ≤65535-repeat records automatically.
     */
    static final class RleWriter implements Closeable {
        private final BufferedOutputStream out;
        private int  currentPattern = -1;   // -1 = nothing buffered yet
        private long accumulated    =  0;

        RleWriter(Path file) throws IOException {
            out = new BufferedOutputStream(Files.newOutputStream(file), 1 << 17);
        }

        void addRepeat(int pattern, long count) throws IOException {
            pattern &= 0xFFFF;
            if (count <= 0) return;
            if (pattern == currentPattern) {
                accumulated += count;
            } else {
                flush();
                currentPattern = pattern;
                accumulated    = count;
            }
        }

        @Override
        public void close() throws IOException {
            flush();
            out.close();
        }

        private void flush() throws IOException {
            if (currentPattern < 0 || accumulated == 0) return;
            long rem = accumulated;
            while (rem > 0) {
                int chunk = (int) Math.min(rem, 0xFFFF);
                out.write(chunk >> 8);
                out.write(chunk & 0xFF);
                out.write(currentPattern >> 8);
                out.write(currentPattern & 0xFF);
                rem -= chunk;
            }
            currentPattern = -1;
            accumulated    = 0;
        }
    }
}
