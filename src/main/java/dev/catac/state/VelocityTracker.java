package dev.catac.state;

/** Bounded queue of velocity updates awaiting a later client Pong. */
final class VelocityTracker {
    private static final int MAX_PENDING = 8;
    private final int[] acknowledgementIds = new int[MAX_PENDING];
    private final long[] deadlines = new long[MAX_PENDING];
    private int writeIndex;
    private final long timeoutNanos;

    VelocityTracker(long timeoutNanos) {
        this.timeoutNanos = timeoutNanos;
        java.util.Arrays.fill(acknowledgementIds, LatencyTracker.NO_PROBE);
    }

    void markVelocity(long nowNanos) {
        acknowledgementIds[writeIndex] = -1;
        deadlines[writeIndex] = nowNanos + timeoutNanos;
        writeIndex = (writeIndex + 1) % MAX_PENDING;
    }

    boolean hasUnarmed(long nowNanos) {
        expire(nowNanos);
        for (int id : acknowledgementIds) {
            if (id == -1) {
                return true;
            }
        }
        return false;
    }

    void arm(int probeId, long nowNanos) {
        expire(nowNanos);
        for (int index = 0; index < MAX_PENDING; index++) {
            if (acknowledgementIds[index] == -1) {
                acknowledgementIds[index] = probeId;
            }
        }
    }

    void acknowledge(int probeId, long nowNanos) {
        for (int index = 0; index < MAX_PENDING; index++) {
            if (acknowledgementIds[index] == probeId) {
                acknowledgementIds[index] = LatencyTracker.NO_PROBE;
            }
        }
        expire(nowNanos);
    }

    int pendingCount(long nowNanos) {
        expire(nowNanos);
        int count = 0;
        for (int id : acknowledgementIds) {
            if (id != LatencyTracker.NO_PROBE) {
                count++;
            }
        }
        return count;
    }

    private void expire(long nowNanos) {
        for (int index = 0; index < MAX_PENDING; index++) {
            if (acknowledgementIds[index] != LatencyTracker.NO_PROBE && nowNanos > deadlines[index]) {
                acknowledgementIds[index] = LatencyTracker.NO_PROBE;
            }
        }
    }
}
