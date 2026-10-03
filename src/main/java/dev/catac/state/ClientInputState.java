package dev.catac.state;

import net.minestom.server.network.packet.client.play.ClientInputPacket;

/** Untrusted input hints; they may widen an envelope, never prove an action legitimate. */
public final class ClientInputState {
    private ClientInputPacket input;
    private long time;

    public void record(ClientInputPacket packet, long now) {
        input = packet;
        time = now;
    }

    public ClientInputPacket recent(long now) {
        return input != null && now - time >= 0 && now - time <= 250_000_000L ? input : null;
    }
}
