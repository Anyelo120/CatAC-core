package dev.catac.config;

import dev.catac.api.EnforcementMode;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CatACConfigTest {
    @Test
    void appliesPerCheckOverrides() {
        CheckPolicy fallback = CheckPolicy.standard(4, 8, 16);
        CheckPolicy override = new CheckPolicy(true, 2, 4, 12, 0.1, 500);
        CatACConfig config = CatACConfig.builder()
                .enforcementMode(EnforcementMode.MONITOR)
                .policy("movement.speed", override)
                .disableCheck("movement.vertical")
                .build();

        assertEquals(EnforcementMode.MONITOR, config.enforcementMode());
        assertEquals(override, config.policyFor("movement.speed", fallback));
        assertFalse(config.policyFor("movement.vertical", fallback).enabled());
        assertEquals(fallback, config.policyFor("unknown.check", fallback));
    }

    @Test
    void rejectsNonPositiveNetworkSynchronizationDurations() {
        assertThrows(IllegalArgumentException.class,
                () -> CatACConfig.builder().networkProbeInterval(Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> CatACConfig.builder().networkAcknowledgementTimeout(Duration.ZERO));
    }

    @Test
    void rejectsCombatPaddingAboveTheMaximumRewind() {
        assertThrows(IllegalArgumentException.class, () -> CatACConfig.builder()
                .combatRewindPadding(Duration.ofMillis(400))
                .combatMaxRewind(Duration.ofMillis(300))
                .build());
    }

    @Test
    void allowsTelemetryToBeDisabledExplicitly() {
        assertFalse(CatACConfig.builder().telemetryEnabled(false).build().telemetryEnabled());
    }

    @Test
    void exposesQuietPlayerFeedbackControls() {
        CatACConfig config = CatACConfig.builder()
                .warningsBeforeKick(3)
                .playerNoticeCooldown(Duration.ofSeconds(7))
                .playerMessageProvider(notice -> null)
                .build();

        assertEquals(3, config.warningsBeforeKick());
        assertEquals(Duration.ofSeconds(7).toNanos(), config.playerNoticeCooldownNanos());
    }

    @Test
    void rejectsInvalidWarningCounts() {
        assertThrows(IllegalArgumentException.class, () -> CatACConfig.builder().warningsBeforeKick(-1));
        assertThrows(IllegalArgumentException.class, () -> CatACConfig.builder().warningsBeforeKick(101));
    }

    @Test
    void exposesAuraDecoyTuning() {
        AuraDecoyPolicy policy = AuraDecoyPolicy.disabled();
        assertFalse(CatACConfig.builder().auraDecoyPolicy(policy).build().auraDecoyPolicy().enabled());
    }

    @Test
    void exposesDamageProtectionPolicy() {
        assertFalse(CatACConfig.builder()
                .damageProtectionPolicy(DamageProtectionPolicy.disabled())
                .build().damageProtectionPolicy().enabled());
    }
}
