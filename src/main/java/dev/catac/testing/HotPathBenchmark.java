package dev.catac.testing;

import java.util.Arrays;
import java.util.Objects;

/** Small dependency-free benchmark helper; use JMH for publication-quality numbers. */
public final class HotPathBenchmark {
    private HotPathBenchmark() {
    }

    public static BenchmarkReport measure(String name, int warmupIterations, int measuredIterations, Runnable operation) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(operation, "operation");
        if (warmupIterations < 0 || measuredIterations < 1) throw new IllegalArgumentException("invalid iteration counts");
        for (int index = 0; index < warmupIterations; index++) operation.run();
        long[] samples = new long[measuredIterations];
        long total = 0L;
        for (int index = 0; index < measuredIterations; index++) {
            long start = System.nanoTime();
            operation.run();
            long elapsed = System.nanoTime() - start;
            samples[index] = elapsed;
            total += elapsed;
        }
        Arrays.sort(samples);
        return new BenchmarkReport(name, measuredIterations, (double) total / measuredIterations,
                percentile(samples, 0.50), percentile(samples, 0.95));
    }

    private static long percentile(long[] samples, double percentile) {
        int index = Math.min(samples.length - 1, (int) Math.ceil(samples.length * percentile) - 1);
        return samples[index];
    }
}
