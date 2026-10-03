package dev.catac.state;

/** Exact confirmation identity. A previous ID must never release a new teleport. */
final class TeleportTracker {
    private final long timeout;
    private final TimeWindow window = new TimeWindow();
    private boolean awaitingId, issued;
    private long timeouts;
    private int expectedId, previousId;

    TeleportTracker(long timeout) {
        this.timeout = timeout;
    }

    void markPending(long now) {
        markPending(now, Integer.MIN_VALUE);
    }

    void markPending(long now, int previous) {
        previousId = previous;
        awaitingId = true;
        issued = true;
        window.open(now, timeout);
    }

    void captureExpectedId(int id) {
        if (awaitingId && id != previousId) {
            expectedId = id;
            awaitingId = false;
        }
    }

    void sent(int id, long now) {
        if (id < 0) {
            window.clear();
            issued = false;
            awaitingId = false;
            return;
        }
        expectedId = id;
        awaitingId = false;
        issued = true;
        window.open(now, timeout);
    }

    boolean acknowledge(int id, long now) {
        if (!pending(now) || awaitingId || id != expectedId) return false;
        window.clear();
        issued = false;
        return true;
    }

    boolean pending(long now) {
        boolean active = window.active(now);
        if (issued && !active) {
            issued = false;
            timeouts++;
        }
        return active;
    }

    long timeouts() {
        return timeouts;
    }
}
