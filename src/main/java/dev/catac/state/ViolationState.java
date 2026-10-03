package dev.catac.state;

public final class ViolationState {
    private double buffer;
    private long lastUpdate, lastFailure, lastAlert, lastNotice;
    private boolean updated, failed, alerted, noticed;
    private int totalDetections, playerWarnings;

    public void advance(long now, double decayPerSecond, long incidentNanos) {
        if (updated) {
            long elapsed = now - lastUpdate;
            if (elapsed > 0)
                buffer = Math.max(0, buffer - decayPerSecond * (elapsed / 1_000_000_000.0));
        }
        lastUpdate = now;
        updated = true;
        if (failed && now - lastFailure >= incidentNanos) {
            buffer = 0;
            playerWarnings = 0;
            totalDetections = 0;
            failed = false;
        }
    }

    public double add(double amount) {
        if (!Double.isFinite(amount) || amount <= 0)
            throw new IllegalArgumentException("amount must be finite and > 0");
        buffer = Math.min(1_000_000, buffer + amount);
        if (totalDetections < Integer.MAX_VALUE) totalDetections++;
        return buffer;
    }

    public double add(double amount, long now) {
        lastFailure = now;
        failed = true;
        return add(amount);
    }

    /** Explicit decay helper; engine decay is elapsed-time based, never packet-count based. */
    public double decay(double amount) {
        if (amount > 0) buffer = Math.max(0, buffer - amount);
        return buffer;
    }

    public boolean canAlert(long now, long cooldown) {
        if (!alerted || now - lastAlert >= cooldown) {
            alerted = true;
            lastAlert = now;
            return true;
        }
        return false;
    }

    public boolean canNotifyPlayer(long now, long cooldown) {
        if (!noticed || now - lastNotice >= cooldown) {
            noticed = true;
            lastNotice = now;
            return true;
        }
        return false;
    }

    public void warningSent() {
        playerWarnings = Math.min(1_000, playerWarnings + 1);
    }

    public double buffer() {
        return buffer;
    }

    public int totalDetections() {
        return totalDetections;
    }

    public int playerWarnings() {
        return playerWarnings;
    }

    public void reset() {
        buffer = 0;
        updated = false;
        failed = false;
        alerted = false;
        noticed = false;
        totalDetections = 0;
        playerWarnings = 0;
    }
}
