package dev.catac.check;

import dev.catac.state.PlayerData;

import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.network.packet.client.ClientPacket;

import java.util.Set;

public interface PacketCheck extends CatCheck {
    /**
     * Empty means all packet types; custom checks must return skip for unrelated input. Cached at
     * registration.
     */
    default Set<Class<? extends ClientPacket>> packetTypes() {
        return Set.of();
    }

    CheckResult evaluate(PlayerPacketEvent event, PlayerData data, long nowNanos);
}
