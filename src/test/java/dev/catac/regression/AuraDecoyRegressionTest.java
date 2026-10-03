package dev.catac.regression;

import static org.junit.jupiter.api.Assertions.*;

import dev.catac.CatAC;
import dev.catac.api.*;
import dev.catac.check.*;
import dev.catac.config.*;
import dev.catac.internal.AuraDecoyManager;
import dev.catac.state.PlayerData;
import dev.catac.support.MinestomFixture;

import net.minestom.server.MinecraftServer;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.entity.EntityTickEvent;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.network.packet.client.play.*;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

class AuraDecoyRegressionTest {
    @Test
    void armedDecoyUsesNormalDiagnosticsWithoutPunishingInKickMode() {
        try (var f = new MinestomFixture()) {
            var capture = new CaptureData();
            try (var ac =
                    CatAC.builder()
                            .config(
                                    f.config()
                                            .enforcementMode(EnforcementMode.KICK)
                                            .auraDecoyPolicy(AuraDecoyPolicy.defaults())
                                            .traceCapacity(8)
                                            .build())
                            .addCheck(capture)
                            .build()
                            .start()) {
                int target = spawn(f, capture);
                attack(f, target);
                var diagnostics =
                        ac.diagnostics().stream()
                                .filter(d -> d.id().equals("combat.aura-decoy"))
                                .findFirst()
                                .orElseThrow();
                assertEquals(1, diagnostics.evaluations());
                assertEquals(1, diagnostics.failures());
                assertEquals(0, diagnostics.faults());
                assertEquals(1, ac.metrics().violationSamples());
                assertEquals(0, ac.metrics().kicks());
                assertEquals(0, ac.metrics().setbacks());
                assertEquals(1, ac.metrics().cancelledPackets());
                assertEquals(-1, capture.data.get().auraDecoy().entityId());
                assertEquals(
                        "armed-private-decoy", ac.traces(f.player).getFirst().evidence().model());
            }
        }
    }

    @Test
    void anExemptDecoyCannotAccumulateEvidenceButItsVirtualTargetIsRemoved() {
        try (var f = new MinestomFixture()) {
            var capture = new CaptureData();
            try (var ac =
                    CatAC.builder().config(f.config().build()).addCheck(capture).build().start()) {
                int target = spawn(f, capture);
                ac.exempt(f.player, "combat.aura-decoy", Duration.ofSeconds(1));
                attack(f, target);
                var diagnostics =
                        ac.diagnostics().stream()
                                .filter(d -> d.id().equals("combat.aura-decoy"))
                                .findFirst()
                                .orElseThrow();
                assertEquals(0, diagnostics.evaluations());
                assertEquals(1, diagnostics.bypassed());
                assertEquals(0, ac.metrics().violationSamples());
                assertEquals(0, ac.metrics().kicks());
                assertEquals(1, ac.metrics().cancelledPackets());
                assertEquals(-1, capture.data.get().auraDecoy().entityId());
            }
        }
    }

    private static int spawn(MinestomFixture f, CaptureData capture) {
        MinecraftServer.getPacketListenerManager()
                .processClientPacket(
                        new ClientPlayerPositionStatusPacket(true, false), f.connection);
        var data = capture.data.get();
        assertNotNull(data);
        assertTrue(
                new AuraDecoyManager()
                        .deploy(
                                f.player,
                                data.auraDecoy(),
                                AuraDecoyPolicy.defaults(),
                                f.clock.nanoTime()));
        f.clock.advanceNanos(225_000_000L);
        return data.auraDecoy().entityId();
    }

    private static void attack(MinestomFixture f, int target) {
        MinecraftServer.getPacketListenerManager()
                .processClientPacket(
                        new ClientInteractEntityPacket(
                                target, new ClientInteractEntityPacket.Attack(), false),
                        f.connection);
        EventDispatcher.call(new EntityTickEvent(f.player));
    }

    private static final class CaptureData implements PacketCheck {
        private final AtomicReference<PlayerData> data = new AtomicReference<>();

        public CheckDescriptor descriptor() {
            return new CheckDescriptor(
                    "host.capture",
                    "Test capture",
                    CheckCategory.PACKET,
                    CheckPolicy.standard(2, 4, 8),
                    false,
                    CheckCapabilities.OBSERVE);
        }

        public CheckResult evaluate(PlayerPacketEvent event, PlayerData data, long now) {
            this.data.set(data);
            return CheckResult.skip();
        }
    }
}
