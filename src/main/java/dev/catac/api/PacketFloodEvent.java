package dev.catac.api;

import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;

import java.util.Objects;

/** Published only when CatAC has dropped or disconnected a packet flood. */
public record PacketFloodEvent(
        Player player,
        Class<?> packetType,
        PacketCost cost,
        int strikes,
        FloodAction action
) implements Event {
    public PacketFloodEvent {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(packetType, "packetType");
        Objects.requireNonNull(cost, "cost");
        Objects.requireNonNull(action, "action");
        if (strikes < 1) throw new IllegalArgumentException("strikes must be >= 1");
    }
}
