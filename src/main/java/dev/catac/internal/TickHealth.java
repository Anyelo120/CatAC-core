package dev.catac.internal;

import dev.catac.state.TimeWindow;

public final class TickHealth {
    private final double thresholdMillis;
    private final TimeWindow lag = new TimeWindow();

    public TickHealth(double thresholdMillis) {
        this.thresholdMillis = thresholdMillis;
    }

    public synchronized void recordTick(double millis, long now) {
        if (!Double.isFinite(millis) || millis > thresholdMillis) lag.open(now, 750_000_000L);
    }

    public synchronized boolean isLagCompensating(long now) {
        return lag.active(now);
    }
}
