package dev.catac.api;

import net.minestom.server.entity.Player;

@FunctionalInterface
public interface ExemptionProvider {
    ExemptionProvider NONE = (player, checkId) -> false;

    boolean isExempt(Player player, String checkId);
}
