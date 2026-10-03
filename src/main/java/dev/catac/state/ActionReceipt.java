package dev.catac.state;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.instance.Instance;

/** Retained for at most the next input/tick to inspect the final event and native state. */
public record ActionReceipt(
        PlayerPacketEvent packet,
        PlayerMoveEvent movement,
        Pos target,
        Instance instance,
        boolean flood,
        int previousTeleportId) {
    public static ActionReceipt packet(PlayerPacketEvent event, boolean flood) {
        return new ActionReceipt(event, null, null, event.getPlayer().getInstance(), flood, 0);
    }

    public static ActionReceipt movement(PlayerMoveEvent event, Pos target) {
        return new ActionReceipt(
                null,
                event,
                target,
                event.getPlayer().getInstance(),
                false,
                event.getPlayer().getLastSentTeleportId());
    }
}
