package dev.catac.state;

public final class ViolationState {
    private double buffer;
    private long lastAlertNanos = Long.MIN_VALUE;
    private long lastPlayerNoticeNanos = Long.MIN_VALUE;
    private int totalDetections;
    private int playerWarnings;

    public double add(double amount) {
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("amount must be finite and > 0");
        }
        buffer = Math.min(1_000_000.0, buffer + amount);
        totalDetections++;
        return buffer;
    }

    public double decay(double amount) {
        if (amount > 0) {
            buffer = Math.max(0.0, buffer - amount);
        }
        return buffer;
    }

    public boolean canAlert(long nowNanos, long cooldownNanos) {
        if (lastAlertNanos == Long.MIN_VALUE || nowNanos - lastAlertNanos >= cooldownNanos) {
            lastAlertNanos = nowNanos;
            return true;
        }
        return false;
    }

    /**
     * Rate-limits feedback separately from staff alerts. The counter is kept
     * per check so unrelated, harmless anomalies can never unlock a kick.
     */
    public boolean canNotifyPlayer(long nowNanos, long cooldownNanos) {
        if (lastPlayerNoticeNanos == Long.MIN_VALUE || nowNanos - lastPlayerNoticeNanos >= cooldownNanos) {
            lastPlayerNoticeNanos = nowNanos;
            playerWarnings++;
            return true;
        }
        return false;
    }

    public double buffer() {
        return buffer;
    }

    public int totalDetections() {
        return totalDetections;
    }

    public int playerWarnings() { return playerWarnings; }

    public void reset() {
        buffer = 0.0;
        lastAlertNanos = Long.MIN_VALUE;
        lastPlayerNoticeNanos = Long.MIN_VALUE;
        totalDetections = 0;
        playerWarnings = 0;
    }
}
