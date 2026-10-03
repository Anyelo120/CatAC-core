package dev.catac.api;

import dev.catac.config.CheckPolicy;

import java.util.Objects;

public record CheckDescriptor(
        String id,
        String displayName,
        CheckCategory category,
        CheckPolicy defaultPolicy,
        boolean setbackEligible,
        CheckCapabilities capabilities) {
    public CheckDescriptor(
            String id,
            String displayName,
            CheckCategory category,
            CheckPolicy policy,
            boolean setbackEligible) {
        this(
                id,
                displayName,
                category,
                policy,
                setbackEligible,
                setbackEligible ? CheckCapabilities.MOVEMENT : CheckCapabilities.PACKET);
    }

    public CheckDescriptor {
        Objects.requireNonNull(id);
        Objects.requireNonNull(displayName);
        Objects.requireNonNull(category);
        Objects.requireNonNull(defaultPolicy);
        Objects.requireNonNull(capabilities);
        if (!id.matches("[a-z0-9]+(?:[._-][a-z0-9]+)*") || displayName.isBlank())
            throw new IllegalArgumentException("Invalid descriptor");
        if (setbackEligible != capabilities.correctMovement())
            throw new IllegalArgumentException("Inconsistent correction capability");
    }
}
