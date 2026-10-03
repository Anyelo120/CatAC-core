package dev.catac.check.builtin;

import dev.catac.api.*;
import dev.catac.check.*;
import dev.catac.config.CheckPolicy;
import dev.catac.internal.AuraDecoyManager;
import dev.catac.state.PlayerData;

import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.play.ClientInteractEntityPacket;

import java.util.Set;

/** Private decoy evidence is experimental and can never punish by itself. */
public final class AuraDecoyCheck implements PacketCheck {
    private static final AuraDecoyManager VERIFIER = new AuraDecoyManager();
    private static final CheckDescriptor DESCRIPTOR =
            new CheckDescriptor(
                    "combat.aura-decoy",
                    "Private aura decoy",
                    CheckCategory.COMBAT,
                    new CheckPolicy(true, 2, 6, 24, .1, 1000),
                    false,
                    CheckCapabilities.OBSERVE);

    public CheckDescriptor descriptor() {
        return DESCRIPTOR;
    }

    public Set<Class<? extends ClientPacket>> packetTypes() {
        return Set.of(ClientInteractEntityPacket.class);
    }

    public CheckResult evaluate(PlayerPacketEvent event, PlayerData data, long now) {
        if (!(event.getPacket() instanceof ClientInteractEntityPacket attack)
                || !VERIFIER.targetsDecoy(data.auraDecoy(), attack)) return CheckResult.skip();
        if (!VERIFIER.confirmAttack(event.getPlayer(), data.auraDecoy(), attack, now))
            return CheckResult.uncertain(SkipReason.UNTRUSTED_STATE);
        return CheckResult.fail(1.5, new CheckEvidence(1, 0, 0, "armed-private-decoy"));
    }
}
