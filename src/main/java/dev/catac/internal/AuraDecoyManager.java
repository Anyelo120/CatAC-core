package dev.catac.internal;

import dev.catac.config.AuraDecoyPolicy;
import dev.catac.state.AuraDecoyState;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.client.play.ClientInteractEntityPacket;
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket;
import net.minestom.server.network.packet.server.play.PlayerInfoRemovePacket;
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/** Sends a player-shaped decoy only to the player currently being probed. */
public final class AuraDecoyManager {
    public boolean deploy(Player player, AuraDecoyState state, AuraDecoyPolicy policy, long nowNanos) {
        if (!policy.enabled() || !state.canSpawn(nowNanos)) return false;

        Pos origin = player.getPosition();
        Vec look = origin.direction();
        double horizontalLength = Math.hypot(look.x(), look.z());
        if (horizontalLength < 1.0E-6) return false;
        double factor = policy.behindDistance() / horizontalLength;
        Pos position = new Pos(origin.x() - look.x() * factor, origin.y(), origin.z() - look.z() * factor,
                origin.yaw() + 180.0f, 0.0f);
        int entityId = Entity.generateId();
        UUID uuid = UUID.randomUUID();
        String username = "catac_" + Integer.toUnsignedString(entityId, 36);
        PlayerInfoUpdatePacket.Entry entry = new PlayerInfoUpdatePacket.Entry(uuid, username, List.of(), false,
                0, GameMode.SURVIVAL, Component.empty(), null, 0, false);
        try {
            player.sendPacket(new PlayerInfoUpdatePacket(EnumSet.of(PlayerInfoUpdatePacket.Action.ADD_PLAYER),
                    List.of(entry)));
            player.sendPacket(new SpawnEntityPacket(entityId, uuid, EntityType.PLAYER, position,
                    position.yaw(), 0, Vec.ZERO));
            state.spawn(entityId, uuid, position.x(), position.z(), nowNanos, policy.armingDelay().toNanos(),
                    policy.lifetime().toNanos(), policy.spawnCooldown().toNanos(), policy.maximumFacingDot());
            return true;
        } catch (RuntimeException exception) {
            state.clear();
            try { player.sendPacket(new PlayerInfoRemovePacket(uuid)); } catch (RuntimeException ignored) { }
            return false;
        }
    }

    public boolean confirmAttack(Player player, AuraDecoyState state, ClientInteractEntityPacket packet,
                                 long nowNanos) {
        if (!(packet.type() instanceof ClientInteractEntityPacket.Attack)) return false;
        int entityId = state.entityId();
        if (entityId == -1 || packet.targetId() != entityId) return false;
        Pos playerPosition = player.getPosition();
        Vec direction = playerPosition.direction();
        double distance = Math.hypot(state.x() - playerPosition.x(), state.z() - playerPosition.z());
        if (distance < 1.0E-6) return false;
        double facingDot = (direction.x() * (state.x() - playerPosition.x()) +
                direction.z() * (state.z() - playerPosition.z())) / distance;
        return state.confirmsAttack(entityId, nowNanos, facingDot);
    }

    public boolean targetsDecoy(AuraDecoyState state, ClientInteractEntityPacket packet) {
        return packet.type() instanceof ClientInteractEntityPacket.Attack &&
                state.entityId() != -1 && packet.targetId() == state.entityId();
    }

    public void remove(Player player, AuraDecoyState state) {
        int entityId = state.entityId();
        UUID uuid = state.uuid();
        if (entityId != -1) {
            try { player.sendPacket(new DestroyEntitiesPacket(entityId)); } catch (RuntimeException ignored) { }
        }
        if (uuid != null) {
            try { player.sendPacket(new PlayerInfoRemovePacket(uuid)); } catch (RuntimeException ignored) { }
        }
        state.clear();
    }
}
