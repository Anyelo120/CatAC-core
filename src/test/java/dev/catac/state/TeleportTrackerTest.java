package dev.catac.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeleportTrackerTest {
    @Test
    void acceptsOnlyTheCapturedTeleportConfirmation() {
        TeleportTracker tracker = new TeleportTracker(1_000L);
        tracker.markPending(100L);
        assertTrue(tracker.pending(101L));
        assertFalse(tracker.acknowledge(3, 102L));
        tracker.captureExpectedId(4);
        assertFalse(tracker.acknowledge(3, 103L));
        assertTrue(tracker.acknowledge(4, 104L));
        assertFalse(tracker.pending(105L));
    }

    @Test
    void failsOpenAfterTheConfirmationTimeout() {
        TeleportTracker tracker = new TeleportTracker(10L);
        tracker.markPending(100L);
        tracker.captureExpectedId(1);
        assertFalse(tracker.pending(111L));
    }
}
