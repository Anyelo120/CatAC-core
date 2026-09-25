package dev.catac.testing;

/** Mutable monotonic clock for deterministic replay tests; never use it in production handlers. */
public final class DeterministicClock {
    private long nowNanos;

    public DeterministicClock(long initialNanos) {
        this.nowNanos = initialNanos;
    }

    public long nowNanos() { return nowNanos; }

    public long advanceNanos(long nanos) {
        if (nanos < 0L) throw new IllegalArgumentException("nanos cannot be negative");
        return nowNanos += nanos;
    }

    public void setNanos(long nanos) {
        if (nanos < nowNanos) throw new IllegalArgumentException("clock cannot move backwards");
        this.nowNanos = nanos;
    }
}
