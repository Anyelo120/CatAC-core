package dev.catac.state;

public final class ExemptionState {
    private long joinUntilNanos;
    private long teleportUntilNanos;
    private long velocityUntilNanos;
    private long manualUntilNanos;

    public ExemptionState(long nowNanos, long joinGraceNanos) {
        this.joinUntilNanos = saturatingAdd(nowNanos, joinGraceNanos);
    }

    public void markTeleport(long nowNanos, long graceNanos) {
        teleportUntilNanos = Math.max(teleportUntilNanos, saturatingAdd(nowNanos, graceNanos));
    }

    public void markVelocity(long nowNanos, long graceNanos) {
        velocityUntilNanos = Math.max(velocityUntilNanos, saturatingAdd(nowNanos, graceNanos));
    }

    public void markManual(long nowNanos, long durationNanos) {
        manualUntilNanos = Math.max(manualUntilNanos, saturatingAdd(nowNanos, durationNanos));
    }

    public boolean movementExempt(long nowNanos) {
        return nowNanos < joinUntilNanos || nowNanos < teleportUntilNanos ||
                nowNanos < velocityUntilNanos || nowNanos < manualUntilNanos;
    }

    public boolean manualExempt(long nowNanos) {
        return nowNanos < manualUntilNanos;
    }

    private static long saturatingAdd(long value, long amount) {
        if (amount <= 0) {
            return value;
        }
        if (Long.MAX_VALUE - value < amount) {
            return Long.MAX_VALUE;
        }
        return value + amount;
    }
}
