package dev.catac.state;

import java.time.Duration;

/** Elapsed comparisons remain valid across signed nanoTime rollover for bounded durations. */
public final class TimeWindow {
    public static final long MAX_DURATION_NANOS = Duration.ofDays(1).toNanos();
    private boolean active;
    private long started;
    private long duration;

    public void open(long now, long nanos) {
        if (nanos < 0 || nanos > MAX_DURATION_NANOS)
            throw new IllegalArgumentException("Window must be in [0,24h]");
        if (nanos == 0) return;
        long remaining = remaining(now);
        if (remaining >= nanos) return;
        active = true;
        started = now;
        duration = nanos;
    }

    public boolean active(long now) {
        return remaining(now) > 0;
    }

    public long remaining(long now) {
        if (!active) return 0;
        long elapsed = now - started;
        if (elapsed < 0) return duration;
        if (elapsed >= duration) {
            active = false;
            return 0;
        }
        return duration - elapsed;
    }

    public void clear() {
        active = false;
    }

    public static long checkedNanos(Duration duration) {
        if (duration == null || duration.isNegative() || duration.compareTo(Duration.ofDays(1)) > 0)
            throw new IllegalArgumentException("Duration must be in [0,24h]");
        return duration.toNanos();
    }
}
