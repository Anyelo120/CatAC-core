package dev.catac.config;

import dev.catac.api.PacketCost;
import dev.catac.api.PacketCostClassifier;
import dev.catac.api.PacketFloodHandler;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.common.ClientPluginMessagePacket;
import net.minestom.server.network.packet.client.play.ClientClickWindowPacket;
import net.minestom.server.network.packet.client.play.ClientCreativeInventoryActionPacket;
import net.minestom.server.network.packet.client.play.ClientEditBookPacket;
import net.minestom.server.network.packet.client.play.ClientTabCompletePacket;
import net.minestom.server.network.packet.client.play.ClientUpdateSignPacket;

import java.time.Duration;
import java.util.Objects;

/**
 * Per-player packet budgets. Unknown/custom packets are NORMAL by default so
 * a host only needs to classify packets which it knows are unusually costly.
 */
public record PacketFloodPolicy(
        boolean enabled,
        PacketBudget totalBudget,
        PacketBudget heavyBudget,
        int strikesBeforeKick,
        Duration strikeWindow,
        PacketCostClassifier classifier,
        PacketFloodHandler handler,
        Component kickMessage
) {
    public PacketFloodPolicy {
        Objects.requireNonNull(totalBudget, "totalBudget");
        Objects.requireNonNull(heavyBudget, "heavyBudget");
        if (strikesBeforeKick < 1 || strikesBeforeKick > 100) {
            throw new IllegalArgumentException("strikesBeforeKick must be between 1 and 100");
        }
        Objects.requireNonNull(strikeWindow, "strikeWindow");
        if (strikeWindow.isNegative() || strikeWindow.isZero()) {
            throw new IllegalArgumentException("strikeWindow must be positive");
        }
        Objects.requireNonNull(classifier, "classifier");
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(kickMessage, "kickMessage");
    }

    public static PacketFloodPolicy defaults() {
        return new PacketFloodPolicy(true, new PacketBudget(160, 240), new PacketBudget(24, 40),
                8, Duration.ofSeconds(3), PacketFloodPolicy::defaultCost, PacketFloodHandler.NOOP,
                Component.text("Too many packets were received."));
    }

    public static PacketFloodPolicy disabled() {
        PacketFloodPolicy defaults = defaults();
        return new PacketFloodPolicy(false, defaults.totalBudget, defaults.heavyBudget,
                defaults.strikesBeforeKick, defaults.strikeWindow, defaults.classifier, defaults.handler,
                defaults.kickMessage);
    }

    private static PacketCost defaultCost(Player player, ClientPacket packet) {
        return packet instanceof ClientClickWindowPacket || packet instanceof ClientCreativeInventoryActionPacket ||
                packet instanceof ClientEditBookPacket || packet instanceof ClientPluginMessagePacket ||
                packet instanceof ClientTabCompletePacket || packet instanceof ClientUpdateSignPacket
                ? PacketCost.HEAVY : PacketCost.NORMAL;
    }
}
