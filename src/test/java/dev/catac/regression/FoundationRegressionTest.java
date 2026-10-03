package dev.catac.regression;

import static org.junit.jupiter.api.Assertions.*;

import dev.catac.api.*;
import dev.catac.check.*;
import dev.catac.config.*;
import dev.catac.engine.EnforcementEngine;
import dev.catac.internal.CheckRegistry;
import dev.catac.state.*;
import dev.catac.support.MinestomFixture;

import net.minestom.server.event.player.PlayerPacketEvent;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

class FoundationRegressionTest {
    @Test
    void irrelevantPacketsCannotEraseEvidence() {
        try (var f = new MinestomFixture()) {
            var data = f.data(1);
            var engine = new EnforcementEngine(f.config().build());
            var policy = new CheckPolicy(true, 2, 4, 8, 0, 1000, 1);
            var d = new CheckDescriptor("test.check", "Test", CheckCategory.PACKET, policy, false);
            long now = f.clock.nanoTime();
            engine.handle(data, 0, d, policy, CheckResult.fail(5, "test"), now);
            for (int i = 0; i < 10_000; i++)
                engine.handle(data, 0, d, policy, CheckResult.skip(), now);
            assertEquals(5, data.violation(0).buffer());
            assertEquals(1, data.violation(0).totalDetections());
            engine.handle(data, 0, d, policy, CheckResult.pass(), now + 1_000_000_000L);
            assertEquals(4, data.violation(0).buffer());
        }
    }

    @Test
    void uncertainAndSkipAreNotPassOrViolations() {
        assertFalse(CheckResult.skip().passed());
        assertFalse(CheckResult.uncertain(SkipReason.UNMODELED).failed());
        assertFalse(CheckResult.uncertain(SkipReason.UNMODELED).evaluated());
        assertTrue(CheckResult.pass().evaluated());
        assertEquals(512, CheckResult.fail(1, "x".repeat(2000)).evidence().length());
    }

    @Test
    void observeCapabilityNeverKicksInKickMode() {
        try (var f = new MinestomFixture()) {
            var config =
                    f.config().enforcementMode(EnforcementMode.KICK).warningsBeforeKick(0).build();
            var data = new PlayerData(f.player, 1, config, f.clock.nanoTime());
            var engine = new EnforcementEngine(config);
            var policy = new CheckPolicy(true, 1, 2, 3, 0, 1000);
            var d =
                    new CheckDescriptor(
                            "test.observe",
                            "Observe",
                            CheckCategory.INVENTORY,
                            policy,
                            false,
                            CheckCapabilities.OBSERVE);
            for (int i = 0; i < 20; i++) {
                var decision =
                        engine.handle(
                                data,
                                0,
                                d,
                                policy,
                                CheckResult.cancel(5, "signal"),
                                f.clock.nanoTime());
                assertFalse(decision.cancel());
                assertFalse(decision.kick());
            }
            assertTrue(f.connection.isOnline());
        }
    }

    @Test
    void suppressedMessagesDoNotCountAsWarnings() {
        try (var f = new MinestomFixture()) {
            var config =
                    f.config()
                            .enforcementMode(EnforcementMode.KICK)
                            .playerMessageProvider(n -> null)
                            .build();
            var data = new PlayerData(f.player, 1, config, f.clock.nanoTime());
            var engine = new EnforcementEngine(config);
            var policy = new CheckPolicy(true, 1, 2, 3, 0, 0);
            var d =
                    new CheckDescriptor(
                            "test.warn", "Warning", CheckCategory.PACKET, policy, false);
            for (int i = 0; i < 10; i++) {
                engine.handle(
                        data,
                        0,
                        d,
                        policy,
                        CheckResult.fail(3, "signal"),
                        f.clock.advanceNanos(5_000_000_000L));
            }
            assertEquals(0, data.violation(0).playerWarnings());
            assertTrue(f.connection.isOnline());
        }
    }

    @Test
    void boundedWindowsHandleNegativeOriginAndSignedRollover() {
        var w = new TimeWindow();
        w.open(-1000, 500);
        assertTrue(w.active(-999));
        assertFalse(w.active(-500));
        w.open(Long.MAX_VALUE - 50, 100);
        assertTrue(w.active(Long.MIN_VALUE + 20));
        assertFalse(w.active(Long.MIN_VALUE + 60));
        assertThrows(
                IllegalArgumentException.class, () -> TimeWindow.checkedNanos(Duration.ofDays(2)));
        var e = new ExemptionState(-1000, 100);
        assertFalse(e.manualExempt(-999));
        assertTrue(e.movementExempt(-999));
        assertFalse(e.movementExempt(-900));
    }

    @Test
    void damageGuardIsAtomicAndCorrelatedToAnAction() {
        var g = new DamageGuardState();
        UUID target = UUID.randomUUID();
        g.arm(target, 17, "combat.custom", -1000, 500);
        assertTrue(g.appliesTo(target, 17, -999));
        assertFalse(g.appliesTo(target, 18, -999));
        assertFalse(g.appliesTo(target, -999));
        assertThrows(
                IllegalArgumentException.class,
                () -> g.arm(UUID.randomUUID(), "bad reason", -900, 500));
        assertTrue(g.appliesTo(target, 17, -899));
        g.clearLegacy();
        assertTrue(g.appliesTo(target, 17, -899));
        g.clear();
        assertFalse(g.appliesTo(target, 17, -899));
    }

    @Test
    void incidentsExpireWarningsAndDetectionCounts() {
        var v = new ViolationState();
        v.advance(-1000, 0, 500);
        v.add(3, -1000);
        v.warningSent();
        v.advance(-500, 0, 500);
        assertEquals(0, v.buffer());
        assertEquals(0, v.playerWarnings());
        assertEquals(0, v.totalDetections());
    }

    @Test
    void unknownOverridesAndImpossibleCapabilitiesAreRejected() {
        var r = new CheckRegistry(CatACConfig.builder().disableCheck("typo.unknown").build());
        assertThrows(IllegalArgumentException.class, r::freeze);
        var r2 = new CheckRegistry(CatACConfig.defaults());
        PacketCheck packet =
                new PacketCheck() {
                    public CheckDescriptor descriptor() {
                        return new CheckDescriptor(
                                "test.bad",
                                "Bad",
                                CheckCategory.PACKET,
                                CheckPolicy.standard(1, 2, 3),
                                true);
                    }

                    public CheckResult evaluate(PlayerPacketEvent e, PlayerData d, long now) {
                        return CheckResult.pass();
                    }
                };
        assertThrows(IllegalArgumentException.class, () -> r2.register(packet));
        assertEquals(0, r2.size());
    }
}
