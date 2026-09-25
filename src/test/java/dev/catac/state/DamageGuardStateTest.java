package dev.catac.state;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageGuardStateTest {
    @Test
    void appliesOnlyOnceToTheExactProtectedTarget() {
        DamageGuardState state = new DamageGuardState();
        UUID target = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        state.arm(target, "combat.reach", 1_000, 500);
        assertTrue(state.appliesTo(target, 1_100));
        assertFalse(state.appliesTo(other, 1_100));
        state.clear();
        assertFalse(state.appliesTo(target, 1_100));
    }

    @Test
    void rejectsInvalidReasonsBeforeArming() {
        DamageGuardState state = new DamageGuardState();
        UUID target = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> state.arm(target, "not valid", 1, 1));
    }
}
