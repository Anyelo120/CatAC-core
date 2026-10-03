package dev.catac.state;

import java.util.HashMap;
import java.util.Map;

public final class ExemptionState {
    private final TimeWindow join = new TimeWindow(),
            teleport = new TimeWindow(),
            velocity = new TimeWindow(),
            manual = new TimeWindow();
    private final Map<String, TimeWindow> scoped = new HashMap<>();

    public ExemptionState(long now, long grace) {
        join.open(now, grace);
    }

    public void markTeleport(long now, long duration) {
        teleport.open(now, duration);
    }

    public void markVelocity(long now, long duration) {
        velocity.open(now, duration);
    }

    public void markManual(long now, long duration) {
        manual.open(now, duration);
    }

    public void markScoped(String checkId, long now, long duration) {
        if (!scoped.containsKey(checkId) && scoped.size() >= 64) {
            scoped.entrySet().removeIf(e -> !e.getValue().active(now));
            if (scoped.size() >= 64) throw new IllegalStateException("Too many scoped exemptions");
        }
        scoped.computeIfAbsent(checkId, k -> new TimeWindow()).open(now, duration);
    }

    public boolean movementExempt(long now) {
        return join.active(now)
                || teleport.active(now)
                || velocity.active(now)
                || manual.active(now);
    }

    public boolean manualExempt(long now) {
        return manual.active(now);
    }

    public boolean scopedExempt(String id, long now) {
        TimeWindow w = scoped.get(id);
        return w != null && w.active(now);
    }
}
