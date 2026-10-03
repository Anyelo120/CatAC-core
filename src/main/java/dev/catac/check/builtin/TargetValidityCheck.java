package dev.catac.check.builtin;

import dev.catac.api.*;
import dev.catac.check.*;
import dev.catac.config.CheckPolicy;
import dev.catac.state.PlayerData;

import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.play.ClientInteractEntityPacket;

import java.util.Set;

/** Stale targets are reconciled without scoring a violation. */
public final class TargetValidityCheck implements PacketCheck {
    private static final CheckDescriptor D =
            new CheckDescriptor(
                    "combat.target",
                    "Target reference",
                    CheckCategory.COMBAT,
                    new CheckPolicy(true, 2, 4, 12, .2, 1000),
                    false,
                    new CheckCapabilities(true, false, false, true));

    public CheckDescriptor descriptor() {
        return D;
    }

    public Set<Class<? extends ClientPacket>> packetTypes() {
        return Set.of(ClientInteractEntityPacket.class);
    }

    public CheckResult evaluate(PlayerPacketEvent e, PlayerData d, long now) {
        if (!(e.getPacket() instanceof ClientInteractEntityPacket p)) return CheckResult.skip();
        if (p.type() instanceof ClientInteractEntityPacket.InteractAt hit
                && (!Float.isFinite(hit.targetX())
                        || !Float.isFinite(hit.targetY())
                        || !Float.isFinite(hit.targetZ())))
            return CheckResult.reject(2, "non-finite entity interaction hit");
        var world = e.getPlayer().getInstance();
        if (world == null) return CheckResult.uncertainCancel(SkipReason.WORLD_UNAVAILABLE);
        var target = world.getEntityById(p.targetId());
        if (target == e.getPlayer()) return CheckResult.reject(1, "self entity interaction");
        if (target == null || !target.isViewer(e.getPlayer()))
            return CheckResult.uncertainCancel(SkipReason.STALE_REFERENCE);
        return CheckResult.pass();
    }
}
