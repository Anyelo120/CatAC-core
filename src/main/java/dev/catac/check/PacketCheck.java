package dev.catac.check;

import dev.catac.state.PlayerData;
import net.minestom.server.event.player.PlayerPacketEvent;

public interface PacketCheck extends CatCheck {
    CheckResult evaluate(PlayerPacketEvent event, PlayerData data, long nowNanos);
}
