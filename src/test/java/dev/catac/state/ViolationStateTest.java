package dev.catac.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViolationStateTest {
    @Test
    void accumulatesAndDecaysWithoutGoingNegative() {
        ViolationState state = new ViolationState();

        assertEquals(2.5, state.add(2.5));
        assertEquals(3.0, state.add(0.5));
        assertEquals(2, state.totalDetections());
        assertEquals(1.0, state.decay(2.0));
        assertEquals(0.0, state.decay(20.0));
    }

    @Test
    void enforcesAlertCooldown() {
        ViolationState state = new ViolationState();

        assertTrue(state.canAlert(1_000, 500));
        assertFalse(state.canAlert(1_200, 500));
        assertTrue(state.canAlert(1_500, 500));
    }

    @Test
    void rateLimitsPlayerNoticesIndependently() {
        ViolationState state = new ViolationState();

        assertTrue(state.canNotifyPlayer(1_000, 500));
        assertFalse(state.canNotifyPlayer(1_200, 500));
        assertTrue(state.canNotifyPlayer(1_500, 500));
        assertEquals(2, state.playerWarnings());
    }
}
