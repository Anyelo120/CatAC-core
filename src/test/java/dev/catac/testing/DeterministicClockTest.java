package dev.catac.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DeterministicClockTest {
    @Test
    void advancesMonotonically() {
        DeterministicClock clock = new DeterministicClock(10);
        assertEquals(30, clock.advanceNanos(20));
        assertThrows(IllegalArgumentException.class, () -> clock.setNanos(29));
    }
}
