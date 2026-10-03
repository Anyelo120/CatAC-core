package dev.catac.regression;

import static org.junit.jupiter.api.Assertions.*;

import dev.catac.*;
import dev.catac.api.*;
import dev.catac.check.*;
import dev.catac.config.*;
import dev.catac.internal.*;
import dev.catac.state.*;
import dev.catac.support.MinestomFixture;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.*;
import net.minestom.server.entity.*;
import net.minestom.server.event.*;
import net.minestom.server.event.entity.*;
import net.minestom.server.event.player.*;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.client.play.*;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;

class AcceptanceRegressionTest {
    @Test
    void disablingAnExperimentalObserverDoesNotDisableTheCoreModel() {
        try (var f = new MinestomFixture();
                var ac = CatAC.install(f.config().disableCheck("movement.medium").build())) {
            for (int i = 1; i <= 5; i++) {
                f.clock.advanceNanos(50_000_000L);
                MinecraftServer.getPacketListenerManager()
                        .processClientPacket(
                                new ClientPlayerPositionPacket(
                                        new Pos(.5 + i * .2, 1, .5), true, false),
                                f.connection);
                EventDispatcher.call(new EntityTickEvent(f.player));
            }
            assertEquals(0, ac.metrics().violationSamples());
            assertTrue(
                    ac.diagnostics().stream()
                                    .filter(d -> d.id().equals("movement.speed"))
                                    .findFirst()
                                    .orElseThrow()
                                    .passes()
                            > 0);
        }
    }

    @Test
    void actualCountersDoNotCountACancellationReversedByTheHost() {
        try (var f = new MinestomFixture();
                var ac = CatAC.install(f.config().build())) {
            var host = EventNode.all("reverse-cancel");
            host.setPriority(100);
            host.addListener(
                    net.minestom.server.event.EventListener.builder(PlayerPacketEvent.class)
                            .ignoreCancelled(false)
                            .handler(e -> e.setCancelled(false))
                            .build());
            MinecraftServer.getGlobalEventHandler().addChild(host);
            try {
                MinecraftServer.getPacketListenerManager()
                        .processClientPacket(
                                new ClientHeldItemChangePacket((short) 9), f.connection);
                EventDispatcher.call(new EntityTickEvent(f.player));
                assertEquals(1, ac.metrics().violationSamples());
                assertEquals(0, ac.metrics().cancelledPackets());
            } finally {
                MinecraftServer.getGlobalEventHandler().removeChild(host);
            }
        }
    }

    @Test
    void aRepeatedSpeedViolationProducesAConfirmedNativeSetback() {
        try (var f = new MinestomFixture();
                var ac =
                        CatAC.install(
                                f.config()
                                        .enforcementMode(EnforcementMode.SETBACK)
                                        .policy(
                                                "movement.speed",
                                                new CheckPolicy(true, 1, 1, 20, 0, 1000))
                                        .build())) {
            MinecraftServer.getPacketListenerManager()
                    .processClientPacket(
                            new ClientPlayerPositionPacket(new Pos(3, 1, .5), true, false),
                            f.connection);
            EventDispatcher.call(new EntityTickEvent(f.player));
            assertEquals(.5, f.player.getPosition().x());
            assertEquals(1, ac.metrics().setbacks());
            assertTrue(ac.networkSnapshot(f.player).orElseThrow().teleportPending());
        }
    }

    @Test
    void aBlockedOldAnchorIsNeverUsedForACorrection() {
        try (var f = new MinestomFixture();
                var ac =
                        CatAC.install(
                                f.config()
                                        .enforcementMode(EnforcementMode.SETBACK)
                                        .policy(
                                                "movement.speed",
                                                new CheckPolicy(true, 1, 1, 20, 0, 1000))
                                        .build())) {
            ac.exempt(f.player, Duration.ZERO);
            f.world.setBlock(0, 1, 0, Block.STONE);
            MinecraftServer.getPacketListenerManager()
                    .processClientPacket(
                            new ClientPlayerPositionPacket(new Pos(3, 1, .5), true, false),
                            f.connection);
            EventDispatcher.call(new EntityTickEvent(f.player));
            assertEquals(0, ac.metrics().setbacks());
            assertTrue(f.connection.isOnline());
        }
    }

    @Test
    void serverLagOrGraceDoesNotTeachATemporalCandidate() {
        try (var f = new MinestomFixture()) {
            var d = f.data(1);
            var c = new CollisionSnapshot();
            c.reset();
            c.supported(true);
            var frame = new MovementFrame();
            frame.reset(
                    f.player,
                    f.player.getPosition(),
                    new Pos(1.5, 1, .5),
                    true,
                    f.clock.nanoTime(),
                    1,
                    c);
            d.stageMovement(frame, false);
            f.move(frame.to());
            d.confirmMovement();
            assertFalse(d.prediction().initialized());
            assertEquals(.5, d.lastSafePosition().x());
        }
    }

    @Test
    void nativeStatusClaimWithoutSupportIsCancelledInSetbackMode() {
        try (var f = new MinestomFixture()) {
            var d = f.data(1);
            d.airFrames(4);
            f.move(new Pos(.5, 5, .5));
            var check = new dev.catac.check.builtin.GroundStatusCheck(new CollisionAnalyzer());
            var result =
                    check.evaluate(
                            new PlayerPacketEvent(
                                    f.player, new ClientPlayerPositionStatusPacket(true, false)),
                            d,
                            f.clock.nanoTime());
            assertTrue(result.failed());
            assertTrue(result.cancelImmediately());
        }
    }

    @Test
    void stairsAllowTwoLegalHalfSteps() {
        try (var f = new MinestomFixture()) {
            f.world.setBlock(
                    1,
                    1,
                    0,
                    Block.STONE_STAIRS
                            .withProperty("facing", "east")
                            .withProperty("half", "bottom"));
            var c = new CollisionSnapshot();
            var analyzer = new CollisionAnalyzer();
            analyzer.analyze(f.player, new Pos(.5, 1, .5), new Pos(1.1, 1.5, .5), c);
            assertTrue(c.complete());
            assertFalse(c.insideSolid());
            assertFalse(c.sweptIntoSolid());
            analyzer.analyze(f.player, new Pos(1.1, 1.5, .5), new Pos(1.8, 2, .5), c);
            assertTrue(c.complete());
            assertFalse(c.insideSolid());
            assertFalse(c.sweptIntoSolid());
            assertTrue(c.supported());
        }
    }

    @Test
    void aDiagonalCornerSlideHasALegalAxisRoute() {
        try (var f = new MinestomFixture()) {
            f.world.setBlock(1, 1, 0, Block.STONE);
            f.world.setBlock(1, 2, 0, Block.STONE);
            var c = new CollisionSnapshot();
            new CollisionAnalyzer().analyze(f.player, new Pos(.5, 1, .5), new Pos(1.5, 1, 1.5), c);
            assertTrue(c.complete());
            assertFalse(c.insideSolid());
            assertFalse(c.sweptIntoSolid());
        }
    }

    @Test
    void iceAndMixedContactUseTheLargestSupportedFrictionEnvelope() {
        try (var f = new MinestomFixture()) {
            f.world.setBlock(0, 0, 0, Block.ICE);
            var c = new CollisionSnapshot();
            new CollisionAnalyzer().analyze(f.player, new Pos(.5, 1, .5), c);
            assertEquals(Block.ICE.registry().friction(), c.surfaceFriction());
            new CollisionAnalyzer().analyze(f.player, new Pos(.9, 1, .5), c);
            assertEquals(Block.ICE.registry().friction(), c.surfaceFriction());
        }
    }

    @Test
    void aHostModifiedDestinationIsWithheldFromThePredictor() {
        try (var f = new MinestomFixture()) {
            var d = f.data(1);
            var c = new CollisionSnapshot();
            c.reset();
            c.supported(true);
            var frame = new MovementFrame();
            frame.reset(
                    f.player,
                    f.player.getPosition(),
                    new Pos(.7, 1, .5),
                    true,
                    f.clock.nanoTime(),
                    1,
                    c);
            d.stageMovement(frame, true);
            f.move(new Pos(.9, 1, .5));
            d.confirmMovement();
            assertFalse(d.prediction().initialized());
            assertEquals(.5, d.lastSafePosition().x());
        }
    }

    @Test
    void providerFailuresRemainVisibleWithoutCountingSuppressedWarnings() {
        try (var f = new MinestomFixture()) {
            var config =
                    f.config()
                            .enforcementMode(EnforcementMode.SETBACK)
                            .playerMessageProvider(
                                    n -> {
                                        throw new IllegalStateException("fixture provider");
                                    })
                            .build();
            var d = new PlayerData(f.player, 1, config, f.clock.nanoTime());
            var e = new dev.catac.engine.EnforcementEngine(config);
            var policy = CheckPolicy.standard(1, 2, 8);
            e.handle(
                    d,
                    0,
                    new CheckDescriptor(
                            "test.callback", "Callback", CheckCategory.PACKET, policy, false),
                    policy,
                    CheckResult.fail(3, "fixture"),
                    f.clock.nanoTime());
            assertEquals(0, d.violation(0).playerWarnings());
            assertEquals(1, e.callbackFaults());
        }
    }
}
