package dev.catac.state;

import java.util.Objects;
import java.util.UUID;

/** Explicit one-shot host guard. CatAC never arms this for cancelled native attack packets. */
public final class DamageGuardState {
    private UUID targetId;
    private String reason;
    private long actionId;
    private final TimeWindow window = new TimeWindow();

    public void arm(UUID target, String reason, long now, long duration) {
        arm(target, 0, reason, now, duration);
    }

    public void arm(UUID target, long action, String reason, long now, long duration) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(reason);
        if (!reason.matches("[a-z0-9]+(?:[._-][a-z0-9]+)*"))
            throw new IllegalArgumentException("Invalid reason");
        if (duration <= 0 || duration > TimeWindow.MAX_DURATION_NANOS)
            throw new IllegalArgumentException("Invalid duration");
        this.targetId = target;
        this.reason = reason;
        this.actionId = action;
        window.open(now, duration);
    }

    public boolean appliesTo(UUID target, long now) {
        return appliesTo(target, 0, now);
    }

    public boolean appliesTo(UUID target, long action, long now) {
        return targetId != null
                && actionId == action
                && window.active(now)
                && targetId.equals(target);
    }

    public void clearLegacy() {
        if (actionId == 0) clear();
    }

    public String reason() {
        return reason;
    }

    public void clear() {
        targetId = null;
        reason = null;
        actionId = 0;
        window.clear();
    }
}
