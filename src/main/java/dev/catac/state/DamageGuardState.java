package dev.catac.state;

import java.util.Objects;
import java.util.UUID;

/** One bounded, short-lived damage denial associated with an attacker. */
public final class DamageGuardState {
    private UUID targetId;
    private String reason;
    private long expiresAtNanos;

    public void arm(UUID targetId, String reason, long nowNanos, long durationNanos) {
        this.targetId = Objects.requireNonNull(targetId, "targetId");
        this.reason = requireReason(reason);
        this.expiresAtNanos = saturatedAdd(nowNanos, durationNanos);
    }

    public boolean appliesTo(UUID targetId, long nowNanos) {
        return this.targetId != null && nowNanos < expiresAtNanos && this.targetId.equals(targetId);
    }

    public String reason() { return reason; }

    /** A guard is one-shot: it cannot suppress later legitimate damage. */
    public void clear() { targetId = null; reason = null; expiresAtNanos = 0L; }

    private static String requireReason(String reason) {
        Objects.requireNonNull(reason, "reason");
        if (!reason.matches("[a-z0-9]+(?:[._-][a-z0-9]+)*")) {
            throw new IllegalArgumentException("Invalid damage protection reason: " + reason);
        }
        return reason;
    }

    private static long saturatedAdd(long value, long amount) {
        if (amount <= 0) return value;
        return Long.MAX_VALUE - value < amount ? Long.MAX_VALUE : value + amount;
    }
}
