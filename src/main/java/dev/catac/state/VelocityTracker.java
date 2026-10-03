package dev.catac.state;

import net.minestom.server.coordinate.Vec;

/** Bounded, acknowledged velocity vectors in protocol units: blocks/tick. */
final class VelocityTracker {
    private static final int CAPACITY = 8;
    private final int[] ids = new int[CAPACITY];
    private final long[] time = new long[CAPACITY];
    private final Vec[] vectors = new Vec[CAPACITY];
    private final long timeout;
    private int next;
    private long timeouts, overwritten;
    private Vec acknowledged = Vec.ZERO;
    private boolean fresh;

    VelocityTracker(long timeout) {
        this.timeout = timeout;
        java.util.Arrays.fill(ids, LatencyTracker.NO_PROBE);
    }

    void markVelocity(long now) {
        markVelocity(Vec.ZERO, now);
    }

    void markVelocity(Vec vector, long now) {
        expire(now);
        if (ids[next] != LatencyTracker.NO_PROBE) overwritten++;
        ids[next] = -1;
        time[next] = now;
        vectors[next] = vector;
        next = (next + 1) % CAPACITY;
    }

    boolean hasUnarmed(long now) {
        expire(now);
        for (int id : ids) if (id == -1) return true;
        return false;
    }

    void arm(int probe, long now) {
        expire(now);
        for (int i = 0; i < CAPACITY; i++) if (ids[i] == -1) ids[i] = probe;
    }

    void acknowledge(int probe, long now) {
        expire(now);
        for (int n = 0; n < CAPACITY; n++) {
            int i = (next + n) % CAPACITY;
            if (ids[i] == probe) {
                acknowledged = vectors[i];
                fresh = true;
                ids[i] = LatencyTracker.NO_PROBE;
                vectors[i] = null;
            }
        }
    }

    Vec consumeAcknowledged() {
        if (!fresh) return null;
        fresh = false;
        return acknowledged;
    }

    int pendingCount(long now) {
        expire(now);
        int count = 0;
        for (int id : ids) if (id != LatencyTracker.NO_PROBE) count++;
        return count;
    }

    long timeouts() {
        return timeouts;
    }

    long overwritten() {
        return overwritten;
    }

    private void expire(long now) {
        for (int i = 0; i < CAPACITY; i++)
            if (ids[i] != LatencyTracker.NO_PROBE && now - time[i] >= timeout) {
                ids[i] = LatencyTracker.NO_PROBE;
                vectors[i] = null;
                timeouts++;
            }
    }
}
