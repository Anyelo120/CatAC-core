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
import dev.catac.testing.PacketFuzzer;

import net.minestom.server.coordinate.*;
import net.minestom.server.event.player.*;
import net.minestom.server.network.packet.client.play.*;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class FuzzAndConcurrencyTest {
    @Test
    void seededHostileInputsRunThroughRealPacketChecksWithoutUnboundedState() {
        try (var f = new MinestomFixture()) {
            var coordinate = new InvalidMovementPacketCheck();
            var world = new WorldInteractionCheck();
            var d = f.data(2);
            for (var input : PacketFuzzer.generate(20261003, 10_000)) {
                var event =
                        new PlayerPacketEvent(
                                f.player,
                                new ClientPlayerPositionPacket(
                                        new Vec(input.x(), input.y(), input.z()), false, false));
                assertEquals(
                        input.hasUnsafeCoordinate(),
                        coordinate.evaluate(event, d, f.clock.nanoTime()).failed());
                var placement =
                        new ClientPlayerBlockPlacementPacket(
                                net.minestom.server.entity.PlayerHand.MAIN,
                                new Vec(1, 1, 0),
                                net.minestom.server.instance.block.BlockFace.TOP,
                                input.cursorX(),
                                input.cursorY(),
                                input.cursorZ(),
                                false,
                                false,
                                1);
                assertDoesNotThrow(
                        () ->
                                world.evaluate(
                                        new PlayerPacketEvent(f.player, placement),
                                        d,
                                        f.clock.nanoTime()));
            }
            assertEquals(0, d.traces().snapshot().size());
        }
    }

    @Test
    void historyReadsAreCoherentWhileAnotherWorkerWritesAndResets() throws Exception {
        var h = new PositionHistory();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Void>> work = new ArrayList<>();
            work.add(
                    () -> {
                        for (int i = 0; i < 20_000; i++) {
                            if (i % 100 == 0) h.clear();
                            h.add(new Pos(i % 8, 1, 0), i);
                        }
                        return null;
                    });
            for (int j = 0; j < 3; j++)
                work.add(
                        () -> {
                            for (int i = 0; i < 20_000; i++) {
                                var p = h.rewind(i, Pos.ZERO);
                                assertTrue(Double.isFinite(p.x()));
                                assertTrue(h.size() <= 64);
                            }
                            return null;
                        });
            for (var future : executor.invokeAll(work)) future.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void multiplePacketWorkersAndShutdownCannotRecreatePlayersAfterClear() throws Exception {
        try (var f = new MinestomFixture()) {
            var engine =
                    new CatEngine(
                            f.config().packetFloodPolicy(PacketFloodPolicy.disabled()).build(),
                            List.of());
            var executor = Executors.newFixedThreadPool(4);
            var begin = new CountDownLatch(1);
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (int worker = 0; worker < 3; worker++)
                    futures.add(
                            executor.submit(
                                    () -> {
                                        begin.await();
                                        for (int i = 0; i < 1000; i++)
                                            engine.eventNode()
                                                    .call(
                                                            new PlayerPacketEvent(
                                                                    f.player,
                                                                    new ClientHeldItemChangePacket(
                                                                            (short) 0)));
                                        return null;
                                    }));
                futures.add(
                        executor.submit(
                                () -> {
                                    begin.await();
                                    engine.clear();
                                    return null;
                                }));
                begin.countDown();
                for (var future : futures) future.get(10, TimeUnit.SECONDS);
                engine.eventNode()
                        .call(
                                new PlayerPacketEvent(
                                        f.player, new ClientHeldItemChangePacket((short) 0)));
                assertEquals(0, engine.trackedPlayers());
            } finally {
                executor.shutdownNow();
                engine.clear();
            }
        }
    }

    @Test
    void aFaultyCustomCheckIsExposedAndCannotContributeAViolation() {
        try (var f = new MinestomFixture()) {
            var calls = new AtomicInteger();
            PacketCheck faulty =
                    new PacketCheck() {
                        public CheckDescriptor descriptor() {
                            return new CheckDescriptor(
                                    "test.faulty",
                                    "Faulty",
                                    CheckCategory.PACKET,
                                    CheckPolicy.standard(1, 2, 3),
                                    false);
                        }

                        public CheckResult evaluate(PlayerPacketEvent e, PlayerData d, long now) {
                            calls.incrementAndGet();
                            throw new IllegalStateException("fixture");
                        }
                    };
            var engine = new CatEngine(f.config().build(), List.of(faulty));
            for (int i = 0; i < 10; i++)
                engine.eventNode()
                        .call(
                                new PlayerPacketEvent(
                                        f.player, new ClientHeldItemChangePacket((short) 0)));
            var state =
                    engine.diagnostics().stream()
                            .filter(d -> d.id().equals("test.faulty"))
                            .findFirst()
                            .orElseThrow();
            assertFalse(state.active());
            assertEquals(1, state.faults());
            assertEquals(1, calls.get());
            assertEquals(0, engine.metrics().violationSamples());
            engine.clear();
        }
    }

    @Test
    void translatedProfilesCannotKickFromGameplayChecks() {
        try (var f = new MinestomFixture()) {
            var config =
                    f.config()
                            .clientProfileProvider(p -> ClientProfile.TRANSLATED)
                            .enforcementMode(EnforcementMode.KICK)
                            .warningsBeforeKick(0)
                            .build();
            var data = new PlayerData(f.player, 1, config, f.clock.nanoTime());
            var engine = new dev.catac.engine.EnforcementEngine(config);
            var policy = new CheckPolicy(true, 1, 2, 3, 0, 1000);
            var descriptor =
                    new CheckDescriptor(
                            "test.gameplay", "Gameplay", CheckCategory.COMBAT, policy, false);
            for (int i = 0; i < 20; i++)
                assertFalse(
                        engine.handle(
                                        data,
                                        0,
                                        descriptor,
                                        policy,
                                        CheckResult.cancel(10, "fixture"),
                                        f.clock.nanoTime())
                                .kick());
            assertEquals(ClientProfile.TRANSLATED, data.clientProfile());
        }
    }

    @Test
    void overflowedOutputMailboxIsBoundedAndReported() {
        try (var f = new MinestomFixture()) {
            var data = f.data(1);
            for (int i = 0; i < 1000; i++)
                data.offerOutbound(new OutboundSignal(i, null, f.clock.nanoTime()));
            int size = 0;
            while (data.pollOutbound() != null) size++;
            assertEquals(16, size);
            assertTrue(data.outboundOverflows() > 0);
            assertTrue(data.outputDegraded());
            assertFalse(data.outputDegraded());
        }
    }

    @Test
    void checkDescriptorsAreReadOnlyOnceAtRegistration() {
        try (var f = new MinestomFixture()) {
            AtomicInteger calls = new AtomicInteger();
            PacketCheck custom =
                    new PacketCheck() {
                        public CheckDescriptor descriptor() {
                            calls.incrementAndGet();
                            return new CheckDescriptor(
                                    "test.cached",
                                    "Cached",
                                    CheckCategory.PACKET,
                                    CheckPolicy.standard(1, 2, 3),
                                    false);
                        }

                        public CheckResult evaluate(PlayerPacketEvent e, PlayerData d, long now) {
                            return CheckResult.skip();
                        }
                    };
            var engine = new CatEngine(f.config().build(), List.of(custom));
            for (int i = 0; i < 10; i++)
                engine.eventNode()
                        .call(
                                new PlayerPacketEvent(
                                        f.player, new ClientHeldItemChangePacket((short) 0)));
            assertEquals(1, calls.get());
            engine.clear();
        }
    }

    @Test
    void tracesNeverGrowBeyondTheConfiguredCapacity() {
        try (var f = new MinestomFixture()) {
            var config = f.config().traceCapacity(8).build();
            var data = new PlayerData(f.player, 1, config, f.clock.nanoTime());
            var enforcement = new dev.catac.engine.EnforcementEngine(config);
            var policy = CheckPolicy.standard(1, 2, 3);
            var descriptor =
                    new CheckDescriptor("test.trace", "Trace", CheckCategory.PACKET, policy, false);
            for (int i = 0; i < 1000; i++)
                enforcement.handle(
                        data,
                        0,
                        descriptor,
                        policy,
                        CheckResult.fail(1, new CheckEvidence(i, 10, 0, "fixture")),
                        f.clock.nanoTime());
            assertEquals(8, data.traces().snapshot().size());
            assertEquals(999, data.traces().snapshot().getLast().evidence().observed());
        }
    }
}
