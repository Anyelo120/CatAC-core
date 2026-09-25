package dev.catac.api;

import dev.catac.config.CheckPolicy;

import java.util.Objects;

public record CheckDescriptor(
        String id,
        String displayName,
        CheckCategory category,
        CheckPolicy defaultPolicy,
        boolean setbackEligible
) {
    public CheckDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(defaultPolicy, "defaultPolicy");
        if (!id.matches("[a-z0-9]+(?:[._-][a-z0-9]+)*")) {
            throw new IllegalArgumentException("Invalid check id: " + id);
        }
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName cannot be blank");
        }
    }
}
