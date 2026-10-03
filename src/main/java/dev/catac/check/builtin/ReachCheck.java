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

public final class ReachCheck implements PacketCheck {
    private static final CheckDescriptor D =
            new CheckDescriptor(
                    "combat.reach",
                    "Melee range and occlusion",
                    CheckCategory.COMBAT,
                    new CheckPolicy(true, 2, 5, 16, .18, 800),
                    false);
    private final EntityHistoryManager histories;
    private final CatACConfig config;

    public ReachCheck(EntityHistoryManager histories, CatACConfig config) {
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
        if (d.synchronization().movementUncertain(now))
            return CheckResult.uncertain(SkipReason.SYNCHRONIZING);
        var s = CombatSnapshot.capture(e.getPlayer(), d, p, histories, config, now);
        if (s == null || !s.position().historical())
            return CheckResult.uncertain(SkipReason.INSUFFICIENT_HISTORY);
        var b = s.position().box();
        var t = s.position();
        var eye = s.eye();
        double distance =
                Math.sqrt(
                        CombatGeometry.distanceSquared(
                                eye.x(), eye.y(), eye.z(), b, t.x(), t.y(), t.z()));
        double limit = e.getPlayer().getAttributeValue(Attribute.ENTITY_INTERACTION_RANGE) + .10;
        if (distance > limit)
            return CheckResult.cancel(
                    Math.min(8, 1 + (distance - limit) * 6),
                    new CheckEvidence(distance, limit, .10, "historical-melee-range"));
        // Occlusion is evaluated independently of facing, including targets behind the player.
        var los =
                CombatGeometry.lineOfSight(
                        e.getPlayer().getInstance(),
                        eye.x(),
                        eye.y(),
                        eye.z(),
                        b,
                        t.x(),
                        t.y(),
                        t.z());
        if (los == CombatGeometry.LineOfSight.INCOMPLETE)
            return CheckResult.uncertain(SkipReason.WORLD_UNAVAILABLE);
        if (los == CombatGeometry.LineOfSight.BLOCKED)
            return CheckResult.cancel(1.5, "melee segment crossed native block shape");
        return CheckResult.pass();
    }
}
