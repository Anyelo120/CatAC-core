package dev.catac.internal;

import dev.catac.config.CatACConfig;
import dev.catac.state.*;

import net.minestom.server.coordinate.*;
import net.minestom.server.entity.*;
import net.minestom.server.network.packet.client.play.ClientInteractEntityPacket;

/** One immutable historical sample; incomplete or discontinuous history is never promoted. */
public record CombatSnapshot(
        Entity target,
        PositionHistory.RewoundPosition position,
        Vec eye,
        Pos view,
        long rewindNanos) {
    public static CombatSnapshot capture(
            Player attacker,
            PlayerData data,
            ClientInteractEntityPacket packet,
            EntityHistoryManager histories,
            CatACConfig config,
            long now) {
        if (attacker.getInstance() == null) return null;
        Entity target = attacker.getInstance().getEntityById(packet.targetId());
        if (target == null || target == attacker || !target.isViewer(attacker)) return null;
        long rewind =
                data.synchronization()
                        .combatRewindNanos(
                                config.combatRewindPaddingNanos(), config.combatMaxRewindNanos());
        PositionHistory history = histories.find(target);
        if (history == null) return null;
        var position =
                history.rewind(
                        now - rewind,
                        target.getPosition(),
                        target.getBoundingBox(),
                        attacker.getInstance());
        Pos view = attacker.getPosition();
        return new CombatSnapshot(
                target,
                position,
                new Vec(view.x(), view.y() + attacker.getEyeHeight(), view.z()),
                view,
                rewind);
    }

    public Vec point() {
        return new Vec(position.x(), position.y(), position.z());
    }
}
