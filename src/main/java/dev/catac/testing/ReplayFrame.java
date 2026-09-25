package dev.catac.testing;

import java.util.Objects;

/** One timestamped external input supplied by a server-specific replay adapter. */
public record ReplayFrame<T>(long sequence, long timestampNanos, T payload) {
    public ReplayFrame {
        if (sequence < 0L || timestampNanos < 0L) {
            throw new IllegalArgumentException("sequence and timestampNanos must be non-negative");
        }
        Objects.requireNonNull(payload, "payload");
    }
}
