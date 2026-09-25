package dev.catac.config;

import dev.catac.api.DamageDecisionProvider;

import java.time.Duration;
import java.util.Objects;

/** Typed settings for denying damage caused by a cancelled combat action. */
public record DamageProtectionPolicy(
        boolean enabled,
        Duration denialWindow,
        DamageDecisionProvider decisionProvider
) {
    public DamageProtectionPolicy {
        Objects.requireNonNull(denialWindow, "denialWindow");
        Objects.requireNonNull(decisionProvider, "decisionProvider");
        if (denialWindow.isNegative() || denialWindow.isZero()) {
            throw new IllegalArgumentException("denialWindow must be positive");
        }
    }

    public static DamageProtectionPolicy defaults() {
        return new DamageProtectionPolicy(true, Duration.ofMillis(500), DamageDecisionProvider.DENY_BY_DEFAULT);
    }

    public static DamageProtectionPolicy disabled() {
        DamageProtectionPolicy defaults = defaults();
        return new DamageProtectionPolicy(false, defaults.denialWindow, defaults.decisionProvider);
    }
}
