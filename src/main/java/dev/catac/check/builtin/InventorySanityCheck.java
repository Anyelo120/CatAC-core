package dev.catac.check.builtin;

import dev.catac.api.*;
import dev.catac.check.*;
import dev.catac.config.CheckPolicy;
import dev.catac.state.PlayerData;

import net.minestom.server.entity.*;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.inventory.*;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.play.*;

import java.util.Set;

/**
 * Structural checks before Minestom's authoritative click preprocessor. Hashes never prove item
 * ownership.
 */
public final class InventorySanityCheck implements PacketCheck {
    private static final CheckDescriptor D =
            new CheckDescriptor(
                    "inventory.invalid-click",
                    "Inventory structure",
                    CheckCategory.INVENTORY,
                    new CheckPolicy(true, 2, 4, 12, .2, 1000),
                    false,
                    new CheckCapabilities(true, false, false, true));

    public CheckDescriptor descriptor() {
        return D;
    }

    public Set<Class<? extends ClientPacket>> packetTypes() {
        return Set.of(
                ClientClickWindowPacket.class,
                ClientCloseWindowPacket.class,
                ClientHeldItemChangePacket.class,
                ClientCreativeInventoryActionPacket.class);
    }

    public CheckResult evaluate(PlayerPacketEvent e, PlayerData d, long now) {
        if (e.getPacket() instanceof ClientHeldItemChangePacket p)
            return p.slot() >= 0 && p.slot() < 9
                    ? CheckResult.pass()
                    : CheckResult.reject(2, "invalid held slot");
        if (e.getPacket() instanceof ClientCreativeInventoryActionPacket p) {
            if (e.getPlayer().getGameMode() != GameMode.CREATIVE)
                return CheckResult.reject(2, "creative inventory action outside creative");
            return p.slot() == -1 || p.slot() >= 0 && p.slot() < PlayerInventory.INVENTORY_SIZE
                    ? CheckResult.pass()
                    : CheckResult.reject(2, "invalid creative slot");
        }
        if (e.getPacket() instanceof ClientCloseWindowPacket p) {
            if (p.windowId() < 0 || p.windowId() > 127)
                return CheckResult.reject(1, "invalid close window id");
            var open = e.getPlayer().getOpenInventory();
            if (p.windowId() != 0 && (open == null || open.getWindowId() != p.windowId()))
                return CheckResult.uncertainCancel(SkipReason.STALE_REFERENCE);
            return CheckResult.pass();
        }
        if (!(e.getPacket() instanceof ClientClickWindowPacket p)) return CheckResult.skip();
        if (p.windowId() < 0
                || p.windowId() > 127
                || p.stateId() < 0
                || p.changedSlots().size() > ClientClickWindowPacket.MAX_CHANGED_SLOTS)
            return CheckResult.reject(2, "invalid inventory header");
        AbstractInventory inventory =
                p.windowId() == 0 ? e.getPlayer().getInventory() : e.getPlayer().getOpenInventory();
        if (inventory == null || inventory.getWindowId() != p.windowId())
            return CheckResult.uncertainCancel(SkipReason.STALE_REFERENCE);
        int size =
                p.windowId() == 0
                        ? PlayerInventory.INVENTORY_SIZE
                        : inventory.getSize() + PlayerInventory.INNER_INVENTORY_SIZE;
        if (!validSlotAndButton(p.slot(), p.button(), p.clickType(), size))
            return CheckResult.reject(2, "invalid click slot/button/type");
        for (short slot : p.changedSlots().keySet())
            if (slot < 0 || slot >= size)
                return CheckResult.reject(2, "invalid changed-slot reference");
        // The pinned Minestom always emits stateId=0. Do not invent an independent item transaction
        // engine.
        return CheckResult.pass();
    }

    public static boolean validSlotAndButton(
            int slot, int button, ClientClickWindowPacket.ClickType type, int size) {
        if (slot != -999 && (slot < 0 || slot >= size)) return false;
        if (slot == -999)
            return switch (type) {
                case PICKUP, THROW -> button == 0 || button == 1;
                case CLONE -> button == 2;
                case QUICK_CRAFT ->
                        button == 0
                                || button == 2
                                || button == 4
                                || button == 6
                                || button == 8
                                || button == 10;
                default -> false;
            };
        return switch (type) {
            case PICKUP, QUICK_MOVE, THROW, PICKUP_ALL -> button == 0 || button == 1;
            case SWAP -> button >= 0 && button <= 8 || button == 40;
            case CLONE -> button == 2;
            case QUICK_CRAFT -> button == 1 || button == 5 || button == 9;
        };
    }
}
