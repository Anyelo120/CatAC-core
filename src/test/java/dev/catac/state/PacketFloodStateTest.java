package dev.catac.state;

import dev.catac.api.PacketCost;
import dev.catac.config.PacketBudget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketFloodStateTest {
    private static final PacketBudget TOTAL = new PacketBudget(10, 10);
    private static final PacketBudget HEAVY = new PacketBudget(2, 2);

    @Test
    void limitsHeavyPacketsWithoutPenalizingAValidRefill() {
        PacketFloodState state = new PacketFloodState(TOTAL, HEAVY, 0);

        assertTrue(state.tryConsume(PacketCost.HEAVY, TOTAL, HEAVY, 0));
        assertTrue(state.tryConsume(PacketCost.HEAVY, TOTAL, HEAVY, 0));
        assertFalse(state.tryConsume(PacketCost.HEAVY, TOTAL, HEAVY, 0));
        assertTrue(state.tryConsume(PacketCost.NORMAL, TOTAL, HEAVY, 0));
        assertTrue(state.tryConsume(PacketCost.HEAVY, TOTAL, HEAVY, 1_000_000_000L));
    }

    @Test
    void resetsStrikesAfterTheConfiguredWindow() {
        PacketFloodState state = new PacketFloodState(TOTAL, HEAVY, 0);
        assertEquals(1, state.strike(100, 500));
        assertEquals(2, state.strike(200, 500));
        assertEquals(1, state.strike(600, 500));
    }
}
