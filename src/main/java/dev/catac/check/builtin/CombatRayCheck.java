package dev.catac.check.builtin;

import dev.catac.api.*;
import dev.catac.check.*;
import dev.catac.config.*;
import dev.catac.internal.*;
import dev.catac.state.PlayerData;

import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.play.ClientInteractEntityPacket;

import java.util.Set;

/** Exact historical ray/AABB measurement; view timing makes this an observation signal. */
public final class CombatRayCheck implements PacketCheck {
    private static final CheckDescriptor D =
            new CheckDescriptor(
                    "combat.ray",
                    "Historical attack ray",
                    CheckCategory.COMBAT,
                    new CheckPolicy(true, 4, 8, 24, .1, 1000),
                    false,
                    CheckCapabilities.OBSERVE);
    private final EntityHistoryManager histories;
    private final CatACConfig config;

    public CombatRayCheck(EntityHistoryManager histories, CatACConfig config) {
        this.histories = histories;
        this.config = config;
    }

    public CheckDescriptor descriptor() {
        return D;
    }

    public Set<Class<? extends ClientPacket>> packetTypes() {
        return Set.of(ClientInteractEntityPacket.class);
    }

    public CheckResult evaluate(PlayerPacketEvent e, PlayerData d, long now) {
        if (!(e.getPacket() instanceof ClientInteractEntityPacket p)
                || !(p.type() instanceof ClientInteractEntityPacket.Attack))
            return CheckResult.skip();
        var s = CombatSnapshot.capture(e.getPlayer(), d, p, histories, config, now);
        if (s == null || !s.position().historical())
            return CheckResult.uncertain(SkipReason.INSUFFICIENT_HISTORY);
        double hit =
                CombatGeometry.rayDistance(
                        s.eye(), s.view().direction(), s.position().box(), s.point(), .10);
        double limit = e.getPlayer().getAttributeValue(Attribute.ENTITY_INTERACTION_RANGE) + .10;
        if (!Double.isFinite(hit))
            return CheckResult.fail(.75, "historical attack ray missed expanded target box");
        return hit > limit
                ? CheckResult.fail(
                        1, new CheckEvidence(hit, limit, .10, "historical-ray-experimental"))
                : CheckResult.pass();
    }
}
