package dev.catac.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketFloodPolicyTest {
    @Test
    void defaultsToAConservativeEnabledBudget() {
        assertTrue(PacketFloodPolicy.defaults().enabled());
    }

    @Test
    void rejectsImpossibleBudgetValues() {
        assertThrows(IllegalArgumentException.class, () -> new PacketBudget(10, 9));
        assertThrows(IllegalArgumentException.class, () -> new PacketFloodPolicy(true,
                new PacketBudget(10, 10), new PacketBudget(2, 2), 0, Duration.ofSeconds(1),
                (player, packet) -> null, event -> { }, net.kyori.adventure.text.Component.empty()));
    }
}
