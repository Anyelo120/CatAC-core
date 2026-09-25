package dev.catac.internal;

import dev.catac.api.ViolationAction;

public record EnforcementDecision(boolean cancel, boolean setback, boolean kick, ViolationAction action) {
    public static final EnforcementDecision NONE = new EnforcementDecision(false, false, false, null);
}
