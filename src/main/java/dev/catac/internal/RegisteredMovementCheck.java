package dev.catac.internal;

import dev.catac.api.CheckDescriptor;
import dev.catac.check.MovementCheck;
import dev.catac.config.CheckPolicy;

public record RegisteredMovementCheck(
        MovementCheck check,
        int slot,
        CheckPolicy policy,
        CheckRuntime runtime,
        CheckDescriptor descriptor) {}
