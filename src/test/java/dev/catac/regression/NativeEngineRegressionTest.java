package dev.catac.regression;

import static org.junit.jupiter.api.Assertions.*;

import dev.catac.*;
import dev.catac.api.*;
import dev.catac.check.*;
import dev.catac.check.builtin.*;
import dev.catac.config.*;
import dev.catac.engine.CatEngine;
import dev.catac.state.*;
import dev.catac.support.MinestomFixture;
import dev.catac.testing.*;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.*;
import net.minestom.server.event.*;
import net.minestom.server.event.entity.EntityTickEvent;
import net.minestom.server.event.player.*;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.common.ClientPongPacket;
import net.minestom.server.network.packet.client.play.*;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;

class NativeEngineRegressionTest {
    @Test
    void realNativeReplayAcceptsOrdinaryGroundMovement() {
        try (var f = new MinestomFixture();
                var replay = new MinestomReplayHarness(f.player, f.clock, f.config().build())) {
            List<ReplayFrame<ClientPacket>> frames = new ArrayList<>();
            for (int i = 1; i <= 20; i++)
                frames.add(
                        new ReplayFrame<>(
                                i,
                                f.clock.nanoTime() + i * 50_000_000L,
                                new ClientPlayerPositionPacket(
                                        new Pos(.5 + i * .2, 1, .5), true, false)));
            var report = replay.run(frames);
            assertEquals(20, report.frames());
            assertEquals(4.5, f.player.getPosition().x(), 1e-8);
            assertEquals(0, replay.anticheat().metrics().violationSamples());
            assertEquals(0, replay.anticheat().metrics().setbacks());
        }
    }

    @Test
    void malformedNativePacketIsCancelledEvenWithDisconnectDisabled() {
        try (var f = new MinestomFixture();
                var replay =
                        new MinestomReplayHarness(
                                f.player,
                                f.clock,
                                f.config().disconnectMalformedPackets(false).build())) {
            var position = f.player.getPosition();
            var result =
                    replay.run(
                            List.of(
                                    new ReplayFrame<ClientPacket>(
                                            0,
                                            f.clock.nanoTime(),
                                            new ClientPlayerPositionPacket(
                                                    new Vec(Double.NaN, 1, 0), false, false))));
            assertEquals(position, f.player.getPosition());
            assertEquals(1, result.results().getFirst().cancellations());
            assertTrue(f.connection.isOnline());
        }
    }

    @Test
    void aLaterNativeListenerCannotCommitACancelledCandidate() {
        try (var f = new MinestomFixture()) {
            var engine = new CatEngine(f.config().build(), List.of());
            var node = engine.eventNode();
            MinecraftServer.getGlobalEventHandler().addChild(node);
            var later = EventNode.all("later");
            later.setPriority(100);
            later.addListener(PlayerMoveEvent.class, e -> e.setCancelled(true));
            MinecraftServer.getGlobalEventHandler().addChild(later);
            try {
                MinecraftServer.getPacketListenerManager()
                        .processClientPacket(
                                new ClientPlayerPositionPacket(new Pos(.7, 1, .5), true, false),
                                f.connection);
                EventDispatcher.call(new EntityTickEvent(f.player));
                assertEquals(.5, f.player.getPosition().x());
                // Pure state regression verifies the withheld model independently of private engine
                // fields.
                var data = f.data(1);
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
                data.stageMovement(frame, true);
                data.confirmMovement();
                assertFalse(data.prediction().initialized());
                assertEquals(.5, data.lastSafePosition().x());
            } finally {
                MinecraftServer.getGlobalEventHandler().removeChild(later);
                MinecraftServer.getGlobalEventHandler().removeChild(node);
                engine.clear();
            }
        }
    }

    @Test
    void suspiciousMonitorMovementNeverTeachesItsSpeedOrSafePosition() {
        try (var f = new MinestomFixture()) {
            var d = f.data(1);
            var c = new CollisionSnapshot();
            c.reset();
            c.supported(true);
            var frame = new MovementFrame();
            frame.reset(
                    f.player,
                    f.player.getPosition(),
                    new Pos(5, 1, .5),
                    true,
                    f.clock.nanoTime(),
                    1,
                    c);
            d.stageMovement(frame, false);
            f.move(frame.to());
            d.confirmMovement();
            assertFalse(d.prediction().initialized());
            assertEquals(.5, d.lastSafePosition().x());
            d.prediction().prepare(frame, d.synchronization());
            assertTrue(d.prediction().horizontalLimit() < 1);
        }
    }

    @Test
    void enabledInventoryMoveCannotDisconnectEvenAtLargeBuffers() {
        try (var f = new MinestomFixture()) {
            assertTrue(f.connection.isOnline());
            var check = new InventoryMoveCheck();
            assertEquals(CheckCapabilities.OBSERVE, check.descriptor().capabilities());
        }
    }

    @Test
    void statusPacketsReachTheirOwnCheckWithoutCreatingAPhysicsFrame() {
        try (var f = new MinestomFixture();
                var replay = new MinestomReplayHarness(f.player, f.clock, f.config().build())) {
            replay.run(
                    List.of(
                            new ReplayFrame<ClientPacket>(
                                    0,
                                    f.clock.nanoTime(),
                                    new ClientPlayerPositionStatusPacket(true, false))));
            var diagnostics = replay.anticheat().diagnostics();
            assertEquals(
                    1,
                    diagnostics.stream()
                            .filter(d -> d.id().equals("packet.ground-status"))
                            .findFirst()
                            .orElseThrow()
                            .evaluations());
            assertEquals(
                    0,
                    diagnostics.stream()
                            .filter(d -> d.id().equals("movement.speed"))
                            .findFirst()
                            .orElseThrow()
                            .evaluations());
        }
    }

    @Test
    void aSaturatedOrdinaryBudgetStillAcceptsAPong() {
        try (var f = new MinestomFixture()) {
            var policy =
                    new PacketFloodPolicy(
                            true,
                            new PacketBudget(1, 1),
                            new PacketBudget(1, 1),
                            2,
                            Duration.ofSeconds(2),
                            (p, packet) -> PacketCost.NORMAL,
                            PacketFloodHandler.NOOP,
                            net.kyori.adventure.text.Component.text("flood"));
            var engine = new CatEngine(f.config().packetFloodPolicy(policy).build(), List.of());
            var e = new PlayerPacketEvent(f.player, new ClientHeldItemChangePacket((short) 0));
            engine.eventNode().call(e);
            var dropped =
                    new PlayerPacketEvent(f.player, new ClientHeldItemChangePacket((short) 0));
            engine.eventNode().call(dropped);
            assertTrue(dropped.isCancelled());
            var pong = new PlayerPacketEvent(f.player, new ClientPongPacket(1));
            engine.eventNode().call(pong);
            assertFalse(pong.isCancelled());
            assertTrue(f.connection.isOnline());
            assertEquals(0, engine.metrics().floodKicks());
            engine.clear();
        }
    }

    @Test
    void faultyExemptionProviderMakesGameplayUncertainAndKeepsHardening() {
        try (var f = new MinestomFixture()) {
            var engine =
                    new CatEngine(
                            f.config()
                                    .exemptionProvider(
                                            (p, id) -> {
                                                throw new IllegalStateException("fixture");
                                            })
                                    .disconnectMalformedPackets(false)
                                    .build(),
                            List.of());
            var valid = new PlayerPacketEvent(f.player, new ClientHeldItemChangePacket((short) 0));
            engine.eventNode().call(valid);
            var invalid =
                    new PlayerPacketEvent(
                            f.player,
                            new ClientPlayerPositionPacket(
                                    new Vec(Double.NaN, 0, 0), false, false));
            engine.eventNode().call(invalid);
            assertTrue(invalid.isCancelled());
            engine.clear();
        }
    }

    @Test
    void scopedExemptionsNeverDisableProtocolHardening() {
        try (var f = new MinestomFixture();
                var ac = CatAC.install(f.config().disconnectMalformedPackets(false).build())) {
            ac.exempt(f.player, "movement.speed", Duration.ofSeconds(1));
            ac.exempt(f.player, Duration.ofSeconds(1));
            MinecraftServer.getPacketListenerManager()
                    .processClientPacket(
                            new ClientPlayerPositionPacket(new Vec(Double.NaN, 0, 0), false, false),
                            f.connection);
            EventDispatcher.call(new EntityTickEvent(f.player));
            assertEquals(1, ac.metrics().cancelledPackets());
            assertTrue(f.connection.isOnline());
        }
    }
}
