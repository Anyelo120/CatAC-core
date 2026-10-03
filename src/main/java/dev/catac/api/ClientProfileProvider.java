package dev.catac.api;

import net.minestom.server.entity.Player;

@FunctionalInterface
public interface ClientProfileProvider {
    ClientProfileProvider JAVA = player -> ClientProfile.JAVA_1_21_11;

    ClientProfile profile(Player player);
}
