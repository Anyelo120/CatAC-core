package dev.catac.state;

import net.minestom.server.coordinate.Vec;

/** Tracks one latest authoritative impulse. Observations never replace its expected vector. */
public final class ImpulseState {
    private Vec vector = Vec.ZERO;
    private long sent, ack;
    private boolean active, acknowledged;

    public void sent(Vec perTick, long now) {
        vector = perTick;
        sent = now;
        active = true;
        acknowledged = false;
    }

    public void acknowledge(Vec perTick, long now) {
        if (!active) sent(perTick, now);
        ack = now;
        acknowledged = true;
    }

    public boolean active(long now) {
        if (active && (now - sent < 0 || now - sent > 1_000_000_000L)) active = false;
        return active;
    }

    public Vec allowance(long now) {
        if (!active(now)) return Vec.ZERO;
        double ticks = Math.max(0, (now - sent) / 50_000_000.0);
        return new Vec(
                vector.x() * Math.pow(.91, ticks),
                vector.y() * Math.pow(.98, ticks),
                vector.z() * Math.pow(.91, ticks));
    }

    public boolean canEvaluate(long now) {
        return active(now)
                && acknowledged
                && now - ack >= 50_000_000L
                && now - ack <= 200_000_000L
                && now - sent <= 350_000_000L;
    }

    public void reset() {
        active = false;
        acknowledged = false;
        vector = Vec.ZERO;
    }
}
