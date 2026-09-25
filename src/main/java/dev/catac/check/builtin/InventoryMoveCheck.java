package dev.catac.check.builtin;

import dev.catac.api.CheckCategory;
import dev.catac.api.CheckDescriptor;
import dev.catac.check.CheckResult;
import dev.catac.check.PacketCheck;
import dev.catac.config.CheckPolicy;
import dev.catac.state.PlayerData;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.network.packet.client.play.ClientClickWindowPacket;

/** Telemetry-only heuristic for inventory interaction immediately after movement. */
public final class InventoryMoveCheck implements PacketCheck {
    private static final long MOVEMENT_WINDOW_NANOS = 75_000_000L;
    private static final CheckDescriptor DESCRIPTOR = new CheckDescriptor(
            "inventory.move", "Inventory move", CheckCategory.INVENTORY,
            new CheckPolicy(true, 8, 16, 40, 0.1, 1_500), false);

    @Override
    public CheckDescriptor descriptor() { return DESCRIPTOR; }

    @Override
    public CheckResult evaluate(PlayerPacketEvent event, PlayerData data, long nowNanos) {
        if (!(event.getPacket() instanceof ClientClickWindowPacket) || data.lastMovementPacketNanos() == 0L) {
            return CheckResult.pass();
        }
        long elapsed = nowNanos - data.lastMovementPacketNanos();
        return elapsed >= 0L && elapsed <= MOVEMENT_WINDOW_NANOS
                ? CheckResult.fail(0.5, "inventory click " + elapsed / 1_000_000L + "ms after movement")
                : CheckResult.pass();
    }
}
