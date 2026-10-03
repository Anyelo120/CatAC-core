package dev.catac.internal;

import dev.catac.api.CheckDescriptor;
import dev.catac.check.PacketCheck;
import dev.catac.config.CheckPolicy;

import net.minestom.server.network.packet.client.ClientPacket;

import java.util.Set;

public record RegisteredPacketCheck(
        PacketCheck check,
        int slot,
        CheckPolicy policy,
        CheckRuntime runtime,
        CheckDescriptor descriptor,
        Set<Class<? extends ClientPacket>> packetTypes) {
    public boolean accepts(ClientPacket packet) {
        return packetTypes.isEmpty() || packetTypes.contains(packet.getClass());
    }
}
