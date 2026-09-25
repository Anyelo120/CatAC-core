package dev.catac.config;

import net.kyori.adventure.text.Component;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for the single-viewer KillAura decoy. A decoy is only spawned
 * after a combat anomaly; it is never a general NPC visible to other players.
 */
public record AuraDecoyPolicy(
        boolean enabled,
        double triggerSeverity,
        Duration spawnCooldown,
        Duration armingDelay,
        Duration lifetime,
        double behindDistance,
        double maximumFacingDot,
        Component kickMessage
) {
    public AuraDecoyPolicy {
        if (!Double.isFinite(triggerSeverity) || triggerSeverity <= 0) {
            throw new IllegalArgumentException("triggerSeverity must be finite and > 0");
        }
        spawnCooldown = positive(spawnCooldown, "spawnCooldown");
        armingDelay = nonNegative(armingDelay, "armingDelay");
        lifetime = positive(lifetime, "lifetime");
        if (armingDelay.compareTo(lifetime) >= 0) {
            throw new IllegalArgumentException("armingDelay must be shorter than lifetime");
        }
        if (!Double.isFinite(behindDistance) || behindDistance < 3.5 || behindDistance > 6.0) {
            throw new IllegalArgumentException("behindDistance must be finite and between 3.5 and 6.0 blocks");
        }
        if (!Double.isFinite(maximumFacingDot) || maximumFacingDot < -1.0 || maximumFacingDot > 0.0) {
            throw new IllegalArgumentException("maximumFacingDot must be finite and between -1 and 0");
        }
        Objects.requireNonNull(kickMessage, "kickMessage");
    }

    public static AuraDecoyPolicy defaults() {
        return new AuraDecoyPolicy(true, 1.5, Duration.ofSeconds(12), Duration.ofMillis(175),
                Duration.ofMillis(650), 3.75, -0.35,
                Component.text("Se detectó ataque automatizado."));
    }

    public static AuraDecoyPolicy disabled() {
        AuraDecoyPolicy defaults = defaults();
        return new AuraDecoyPolicy(false, defaults.triggerSeverity, defaults.spawnCooldown,
                defaults.armingDelay, defaults.lifetime, defaults.behindDistance,
                defaults.maximumFacingDot, defaults.kickMessage);
    }

    private static Duration positive(Duration value, String name) {
        nonNegative(value, name);
        if (value.isZero()) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static Duration nonNegative(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isNegative()) throw new IllegalArgumentException(name + " cannot be negative");
        return value;
    }
}
