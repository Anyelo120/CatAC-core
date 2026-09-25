package dev.catac.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityTrackerTest {
    @Test
    void keepsVelocityPendingUntilTheProbeIsAcknowledged() {
        VelocityTracker tracker = new VelocityTracker(1_000L);
        tracker.markVelocity(100L);
        assertTrue(tracker.hasUnarmed(101L));
        tracker.arm(9, 102L);
        assertEquals(1, tracker.pendingCount(103L));
        tracker.acknowledge(9, 104L);
        assertEquals(0, tracker.pendingCount(105L));
    }

    @Test
    void boundsTheQueueAndExpiresUnacknowledgedVelocity() {
        VelocityTracker tracker = new VelocityTracker(10L);
        for (int index = 0; index < 10; index++) {
            tracker.markVelocity(100L + index);
        }
        assertEquals(8, tracker.pendingCount(109L));
        assertEquals(0, tracker.pendingCount(120L));
    }
}
