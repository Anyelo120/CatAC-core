package dev.catac.testing;

import java.util.List;

/** Immutable result of a deterministic timeline execution. */
public record ReplayReport<R>(int frames, long durationNanos, List<R> results) {
    public ReplayReport {
        results = List.copyOf(results);
    }
}
