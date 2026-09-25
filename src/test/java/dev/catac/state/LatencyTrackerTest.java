package dev.catac.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LatencyTrackerTest {
    @Test
    void measuresAcknowledgedRoundTripsAndRejectsUnknownPongs() {
        LatencyTracker tracker = new LatencyTracker(1_000L, 10_000L);
        int first = tracker.createProbe(100L, false);
        assertFalse(tracker.acknowledge(first + 10, 200L));
        assertTrue(tracker.acknowledge(first, 1_100L));
        assertTrue(tracker.available());
        assertEquals(0.001, tracker.roundTripMillis(), 0.000_001);

        int second = tracker.createProbe(1_200L, true);
        assertTrue(tracker.acknowledge(second, 3_200L));
        assertTrue(tracker.jitterMillis() > 0.0);
    }

    @Test
    void expiresAProbeOutsideItsAcknowledgementWindow() {
        LatencyTracker tracker = new LatencyTracker(1_000L, 100L);
        int id = tracker.createProbe(10L, false);
        assertFalse(tracker.acknowledge(id, 111L));
        assertFalse(tracker.available());
    }
}
