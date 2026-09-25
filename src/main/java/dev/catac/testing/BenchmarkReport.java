package dev.catac.testing;

/** Basic latency distribution for an offline microbenchmark. */
public record BenchmarkReport(String name, int iterations, double averageNanos, long p50Nanos, long p95Nanos) {
}
