package dev.catac.state;

/** Fixed-size tracker for server Ping / client Pong round trips. */
final class LatencyTracker {
    static final int NO_PROBE = Integer.MIN_VALUE;
    private static final int MAX_PENDING = 8;

    private final int[] probeIds = new int[MAX_PENDING];
    private final long[] sentNanos = new long[MAX_PENDING];
    private final long intervalNanos;
    private final long timeoutNanos;
    private int nextProbeId;
    private int writeIndex;
    private long nextProbeNanos;
    private long lastRttNanos = -1L;
    private double roundTripMillis = -1.0;
    private double jitterMillis = -1.0;
    private int samples;

    LatencyTracker(long intervalNanos, long timeoutNanos) {
        this.intervalNanos = intervalNanos;
        this.timeoutNanos = timeoutNanos;
        java.util.Arrays.fill(probeIds, NO_PROBE);
    }

    int createProbe(long nowNanos, boolean force) {
        expire(nowNanos);
        if (!force && nowNanos < nextProbeNanos) {
            return NO_PROBE;
        }
        int id = ++nextProbeId;
        if (id == NO_PROBE) {
            id = ++nextProbeId;
        }
        probeIds[writeIndex] = id;
        sentNanos[writeIndex] = nowNanos;
        writeIndex = (writeIndex + 1) % MAX_PENDING;
        nextProbeNanos = nowNanos + intervalNanos;
        return id;
    }

    boolean acknowledge(int id, long nowNanos) {
        for (int index = 0; index < MAX_PENDING; index++) {
            if (probeIds[index] != id) {
                continue;
            }
            long elapsed = nowNanos - sentNanos[index];
            probeIds[index] = NO_PROBE;
            if (elapsed < 0L || elapsed > timeoutNanos) {
                return false;
            }
            if (lastRttNanos >= 0L) {
                double deltaMillis = Math.abs(elapsed - lastRttNanos) / 1_000_000.0;
                jitterMillis = jitterMillis < 0.0 ? deltaMillis : jitterMillis * 0.8 + deltaMillis * 0.2;
            }
            double elapsedMillis = elapsed / 1_000_000.0;
            roundTripMillis = roundTripMillis < 0.0 ? elapsedMillis : roundTripMillis * 0.8 + elapsedMillis * 0.2;
            lastRttNanos = elapsed;
            samples++;
            return true;
        }
        return false;
    }

    void discard(int id) {
        for (int index = 0; index < MAX_PENDING; index++) {
            if (probeIds[index] == id) {
                probeIds[index] = NO_PROBE;
                return;
            }
        }
    }

    private void expire(long nowNanos) {
        for (int index = 0; index < MAX_PENDING; index++) {
            if (probeIds[index] != NO_PROBE && nowNanos - sentNanos[index] > timeoutNanos) {
                probeIds[index] = NO_PROBE;
            }
        }
    }

    boolean available() { return samples > 0; }
    double roundTripMillis() { return roundTripMillis; }
    double jitterMillis() { return jitterMillis; }
}
