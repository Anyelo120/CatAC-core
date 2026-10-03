package dev.catac.check.builtin;

import dev.catac.api.*;
import dev.catac.check.*;
import dev.catac.config.CheckPolicy;
import dev.catac.state.PlayerData;

import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.play.ClientInputPacket;

import java.util.Set;

public final class ClientInputCheck implements PacketCheck {
    private static final CheckDescriptor D =
            new CheckDescriptor(
                    "packet.input",
                    "Client input",
                    CheckCategory.PACKET,
                    new CheckPolicy(true, 2, 4, 12, 0, 1000),
                    false,
                    new CheckCapabilities(true, false, false, true));

    public CheckDescriptor descriptor() {
        return D;
    }

    public Set<Class<? extends ClientPacket>> packetTypes() {
        return Set.of(ClientInputPacket.class);
    }

    public CheckResult evaluate(PlayerPacketEvent e, PlayerData d, long now) {
        if (!(e.getPacket() instanceof ClientInputPacket p)) return CheckResult.skip();
        if ((p.flags() & 0x80) != 0) return CheckResult.reject(1, "input uses reserved flag");
        d.input().record(p, now);
        return CheckResult.pass();
    }
}
