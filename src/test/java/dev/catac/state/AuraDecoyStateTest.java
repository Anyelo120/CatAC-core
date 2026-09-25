package dev.catac.state;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuraDecoyStateTest {
    @Test
    void onlyConfirmsTheArmedEntityWhileItIsBehindThePlayer() {
        AuraDecoyState state = new AuraDecoyState();
        state.spawn(42, UUID.randomUUID(), 0.0, 0.0, 1_000, 100, 500, 2_000, -0.35);

        assertFalse(state.confirmsAttack(42, 1_050, -1.0));
        assertFalse(state.confirmsAttack(41, 1_150, -1.0));
        assertFalse(state.confirmsAttack(42, 1_150, 0.25));
        assertTrue(state.confirmsAttack(42, 1_150, -0.8));
        assertFalse(state.confirmsAttack(42, 1_500, -0.8));
        state.clear();
        assertFalse(state.canSpawn(2_999));
        assertTrue(state.canSpawn(3_000));
    }
}
