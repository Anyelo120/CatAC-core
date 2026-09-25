package dev.catac.testing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeterministicClockTest {
    @Test
    void advancesMonotonically() {
        DeterministicClock clock = new DeterministicClock(10);
        assertEquals(30, clock.advanceNanos(20));
        assertThrows(IllegalArgumentException.class, () -> clock.setNanos(29));
    }
}
