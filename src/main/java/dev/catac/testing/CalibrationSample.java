package dev.catac.testing;

import java.util.Objects;

/** One exported observation from a monitor-only server run. */
public record CalibrationSample(String checkId, double severity, boolean cancelled) {
    public CalibrationSample {
        Objects.requireNonNull(checkId, "checkId");
        if (!Double.isFinite(severity) || severity < 0.0) {
            throw new IllegalArgumentException("severity must be finite and non-negative");
        }
    }
}
