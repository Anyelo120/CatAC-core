package dev.catac.internal;

public final class TickHealth {
    private static final long COMPENSATION_NANOS = 750_000_000L;

    private final double thresholdMillis;
    private volatile long laggingUntilNanos;

    public TickHealth(double thresholdMillis) {
        this.thresholdMillis = thresholdMillis;
    }

    public void recordTick(double tickTimeMillis, long nowNanos) {
        if (tickTimeMillis > thresholdMillis) {
            laggingUntilNanos = nowNanos + COMPENSATION_NANOS;
        }
    }

    public boolean isLagCompensating(long nowNanos) {
        return nowNanos < laggingUntilNanos;
    }
}
