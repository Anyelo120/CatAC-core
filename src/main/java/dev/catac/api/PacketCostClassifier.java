package dev.catac.api;

import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.client.ClientPacket;

/** Classifies custom packets without replacing Minestom's packet listeners. */
@FunctionalInterface
public interface PacketCostClassifier {
    PacketCost classify(Player player, ClientPacket packet);
}
