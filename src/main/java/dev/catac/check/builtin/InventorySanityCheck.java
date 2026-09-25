package dev.catac.check.builtin;

import dev.catac.api.CheckCategory;
import dev.catac.api.CheckDescriptor;
import dev.catac.check.CheckResult;
import dev.catac.check.PacketCheck;
import dev.catac.config.CheckPolicy;
import dev.catac.state.PlayerData;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.inventory.AbstractInventory;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.network.packet.client.play.ClientClickWindowPacket;
import net.minestom.server.network.packet.client.play.ClientCloseWindowPacket;
import net.minestom.server.network.packet.client.play.ClientHeldItemChangePacket;

/** Rejects malformed inventory references while delegating normal click logic to Minestom. */
public final class InventorySanityCheck implements PacketCheck {
    private static final CheckDescriptor DESCRIPTOR = new CheckDescriptor(
            "inventory.invalid-click", "Invalid inventory click", CheckCategory.INVENTORY,
            new CheckPolicy(true, 2, 4, 12, 0.2, 1_000), false);

    @Override
    public CheckDescriptor descriptor() { return DESCRIPTOR; }

    @Override
    public CheckResult evaluate(PlayerPacketEvent event, PlayerData data, long nowNanos) {
        if (event.getPacket() instanceof ClientHeldItemChangePacket held) {
            return held.slot() >= 0 && held.slot() < 9 ? CheckResult.pass()
                    : CheckResult.cancel(2.0, "held-item slot outside [0,8]: " + held.slot());
        }
        if (event.getPacket() instanceof ClientCloseWindowPacket close) {
            return close.windowId() >= 0 && close.windowId() <= 127 ? CheckResult.pass()
                    : CheckResult.cancel(1.5, "invalid window close id=" + close.windowId());
        }
        if (event.getPacket() instanceof ClientClickWindowPacket click) {
            return validateClick(event.getPlayer(), click);
        }
        return CheckResult.pass();
    }

    private static CheckResult validateClick(Player player, ClientClickWindowPacket packet) {
        if (packet.windowId() < 0 || packet.windowId() > 127 || packet.stateId() < 0) {
            return CheckResult.cancel(2.0, "invalid inventory window/state reference");
        }
        AbstractInventory inventory = packet.windowId() == 0 ? player.getInventory() : player.getOpenInventory();
        if (inventory == null || inventory.getWindowId() != packet.windowId()) {
            return CheckResult.cancel(1.5, "click references a window not open for the player");
        }
        int maxSlot = packet.windowId() == 0 ? PlayerInventory.INVENTORY_SIZE - 1
                : inventory.getSize() + PlayerInventory.INNER_INVENTORY_SIZE - 1;
        if (packet.slot() < -999 || packet.slot() > maxSlot) {
            return CheckResult.cancel(2.0, "click slot outside protocol inventory bounds");
        }
        if (packet.clickType() == ClientClickWindowPacket.ClickType.SWAP &&
                (packet.button() < 0 || (packet.button() > 8 && packet.button() != 40))) {
            return CheckResult.cancel(1.5, "invalid hotbar/offhand swap button=" + packet.button());
        }
        return CheckResult.pass();
    }
}
