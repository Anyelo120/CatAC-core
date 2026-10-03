package dev.catac.regression;

import static org.junit.jupiter.api.Assertions.*;

import dev.catac.api.*;
import dev.catac.check.*;
import dev.catac.check.builtin.*;
import dev.catac.internal.*;
import dev.catac.state.*;
import dev.catac.support.MinestomFixture;
import dev.catac.testing.*;

import net.minestom.server.coordinate.*;
import net.minestom.server.entity.*;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.event.*;
import net.minestom.server.event.entity.EntityTickEvent;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.client.play.*;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

class CorpusAndModelTest {
    private static List<ReplayFrame<net.minestom.server.network.packet.client.ClientPacket>> corpus(
            String name) throws IOException {
        try (var source =
                new InputStreamReader(
                        Objects.requireNonNull(
                                CorpusAndModelTest.class.getResourceAsStream("/replays/" + name)),
                        StandardCharsets.UTF_8)) {
            return NativeReplayCodec.read(source);
        }
    }

    @Test
    void savedGroundCorpusRunsThroughTheNativeEngine() throws Exception {
        try (var f = new MinestomFixture();
                var replay = new MinestomReplayHarness(f.player, f.clock, f.config().build())) {
            replay.run(corpus("legitimate-flat.csv"));
            assertEquals(0, replay.anticheat().metrics().violationSamples());
            assertEquals(4.5, f.player.getPosition().x(), 1e-8);
        }
    }

    @Test
    void savedGravityCorpusAcceptsACompleteJumpAndLanding() throws Exception {
        try (var f = new MinestomFixture();
                var replay =
                        new MinestomReplayHarness(
                                f.player, f.clock, f.config().traceCapacity(32).build())) {
            replay.run(corpus("gravity-jump.csv"));
            assertEquals(
                    0,
                    replay.anticheat().metrics().violationSamples(),
                    replay.anticheat().traces(f.player).toString());
            assertEquals(1, f.player.getPosition().y(), 1e-8);
        }
    }

    @Test
    void savedHostileCorpusIsCancelledWithoutDisconnectingTheHarness() throws Exception {
        try (var f = new MinestomFixture();
                var replay =
                        new MinestomReplayHarness(
                                f.player,
                                f.clock,
                                f.config().disconnectMalformedPackets(false).build())) {
            replay.run(corpus("hostile-format.csv"));
            assertEquals(5, replay.anticheat().metrics().cancelledPackets());
            assertTrue(f.connection.isOnline());
        }
    }

    @Test
    void parserRejectsUnknownVersionOversizedLinesAndBadOrdering() {
        assertThrows(
                IllegalArgumentException.class,
                () -> NativeReplayCodec.read(new StringReader("wrong\n")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        NativeReplayCodec.read(
                                new StringReader(
                                        NativeReplayCodec.HEADER + "\n" + "x".repeat(1025))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        NativeReplayCodec.read(
                                new StringReader(
                                        NativeReplayCodec.HEADER + "\n1,0,HELD,0\n0,1,HELD,0")));
    }

    @Test
    void predictorNeverUsesTheCurrentSuspiciousDeltaAsItsBaseline() {
        try (var f = new MinestomFixture()) {
            var d = f.data(1);
            var c = new CollisionSnapshot();
            c.reset();
            c.supported(true);
            var frame = new MovementFrame();
            frame.reset(
                    f.player,
                    new Pos(.5, 1, .5),
                    new Pos(15, 1, .5),
                    true,
                    f.clock.nanoTime(),
                    1,
                    c);
            d.prediction().prepare(frame, d.synchronization());
            assertTrue(d.prediction().horizontalLimit() < .5);
            assertTrue(new HorizontalSpeedCheck().evaluate(frame, d).failed());
        }
    }

    @Test
    void aReducedMovementSpeedAttributeReducesTheEnvelope() {
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
            f.player.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(.01);
            d.prediction().prepare(frame, d.synchronization());
            assertTrue(new HorizontalSpeedCheck().evaluate(frame, d).failed());
        }
    }

    @Test
    void jumpStrengthAndGravityAttributesChangeThePhysicalEnvelope() {
        try (var f = new MinestomFixture()) {
            var d = f.data(1);
            var c = new CollisionSnapshot();
            c.reset();
            var frame = new MovementFrame();
            frame.reset(
                    f.player,
                    f.player.getPosition(),
                    new Pos(.5, 1.8, .5),
                    false,
                    f.clock.nanoTime(),
                    1,
                    c);
            f.player.getAttribute(Attribute.JUMP_STRENGTH).setBaseValue(.9);
            d.prediction().prepare(frame, d.synchronization());
            assertTrue(new VerticalPhysicsCheck().evaluate(frame, d).passed());
        }
    }

    @Test
    void anAcknowledgedServerImpulseWidensTheModelWithoutLearningClientMotion() {
        try (var f = new MinestomFixture()) {
            var d = f.data(1);
            var c = new CollisionSnapshot();
            c.reset();
            var frame = new MovementFrame();
            frame.reset(
                    f.player,
                    f.player.getPosition(),
                    new Pos(1.2, 1.4, .5),
                    false,
                    f.clock.nanoTime(),
                    1,
                    c);
            d.impulse().sent(new Vec(.8, .5, 0), f.clock.nanoTime());
            d.impulse().acknowledge(new Vec(.8, .5, 0), f.clock.nanoTime());
            d.prediction().prepare(frame, d.synchronization(), d.impulse());
            assertTrue(d.prediction().horizontalLimit() > .8);
            assertFalse(d.prediction().initialized());
        }
    }

    @Test
    void physicalMediumExemptionsCannotBypassSolidGeometry() {
        try (var f = new MinestomFixture()) {
            var d = f.data(1);
            var c = new CollisionSnapshot();
            c.reset();
            c.touchingLiquid(true);
            c.sweptIntoSolid(true);
            var frame = new MovementFrame();
            frame.reset(
                    f.player,
                    f.player.getPosition(),
                    new Pos(2, 1, .5),
                    false,
                    f.clock.nanoTime(),
                    1,
                    c);
            assertEquals(
                    CheckResult.Outcome.UNCERTAIN,
                    new HorizontalSpeedCheck().evaluate(frame, d).outcome());
            assertTrue(new PhaseCheck().evaluate(frame, d).failed());
        }
    }

    @Test
    void allAdvancedHeuristicsHaveAHardObservationCeiling() {
        for (var check :
                List.of(
                        new NoSlowCheck(),
                        new KnockbackCheck(),
                        new MediumMovementCheck(),
                        new VerticalDescentCheck()))
            assertEquals(CheckCapabilities.OBSERVE, check.descriptor().capabilities());
        assertEquals(CheckCapabilities.OBSERVE, new AuraDecoyCheck().descriptor().capabilities());
    }

    @Test
    void aTargetBehindThePlayerStillHasItsOcclusionEvaluated() {
        try (var f = new MinestomFixture()) {
            var target = new LivingEntity(EntityType.ZOMBIE);
            target.setInstance(f.world, new Pos(2.5, 1, .5)).join();
            target.addViewer(f.player);
            var history = new EntityHistoryManager();
            history.track(target, f.clock.nanoTime() - 100_000_000L);
            history.track(target, f.clock.nanoTime());
            f.world.setBlock(1, 1, 0, Block.GLASS_PANE);
            f.world.setBlock(1, 2, 0, Block.GLASS_PANE);
            var check = new ReachCheck(history, f.config().build());
            var result =
                    check.evaluate(
                            new PlayerPacketEvent(
                                    f.player,
                                    new ClientInteractEntityPacket(
                                            target.getEntityId(),
                                            new ClientInteractEntityPacket.Attack(),
                                            false)),
                            f.data(1),
                            f.clock.nanoTime());
            assertTrue(result.failed());
            assertTrue(result.cancelImmediately());
            target.remove();
        }
    }

    @Test
    void unconfirmedOrMissingHistoryCannotCreateAPunitiveReachDecision() {
        try (var f = new MinestomFixture()) {
            var target = new LivingEntity(EntityType.ZOMBIE);
            target.setInstance(f.world, new Pos(4.5, 1, .5)).join();
            target.addViewer(f.player);
            var check = new ReachCheck(new EntityHistoryManager(), f.config().build());
            var result =
                    check.evaluate(
                            new PlayerPacketEvent(
                                    f.player,
                                    new ClientInteractEntityPacket(
                                            target.getEntityId(),
                                            new ClientInteractEntityPacket.Attack(),
                                            false)),
                            f.data(1),
                            f.clock.nanoTime());
            assertEquals(CheckResult.Outcome.UNCERTAIN, result.outcome());
            assertFalse(result.cancelImmediately());
            target.remove();
        }
    }

    @Test
    void aTeleportWithoutConfirmationDoesNotWaitForMinusOneForever() {
        try (var f = new MinestomFixture();
                var ac = dev.catac.CatAC.install(f.config().build())) {
            ac.exempt(f.player, java.time.Duration.ZERO);
            f.player.teleport(new Pos(3, 1, .5), Vec.ZERO, null, RelativeFlags.NONE, false).join();
            EventDispatcher.call(new EntityTickEvent(f.player));
            assertFalse(ac.networkSnapshot(f.player).orElseThrow().teleportPending());
        }
    }

    @Test
    void labeledQualityRequiresLegitimateDenominatorsAndReportsUncertainty() {
        var quality =
                CalibrationAnalyzer.quality(
                                List.of(
                                        new LabeledTrial("test", LabeledTrial.Label.CHEAT, true),
                                        new LabeledTrial("test", LabeledTrial.Label.CHEAT, false),
                                        new LabeledTrial(
                                                "test", LabeledTrial.Label.LEGITIMATE, false),
                                        new LabeledTrial("test", LabeledTrial.Label.UNKNOWN, true)))
                        .getFirst();
        assertEquals(1, quality.truePositives());
        assertEquals(.5, quality.recall());
        assertEquals(0, quality.falsePositiveRate());
        assertTrue(quality.legitimateUpper95Wilson() > .5);
        assertEquals(1, quality.unknown());
    }
}
