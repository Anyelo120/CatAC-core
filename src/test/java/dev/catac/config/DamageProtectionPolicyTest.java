package dev.catac.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.time.Duration;

class DamageProtectionPolicyTest {
    @Test
    void defaultsToDenyingOnlyMatchedInvalidDamage() {
        assertTrue(DamageProtectionPolicy.defaults().enabled());
    }

    @Test
    void rejectsAnEmptyDenialWindow() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DamageProtectionPolicy(true, Duration.ZERO, context -> null));
    }
}
