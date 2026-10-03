package dev.catac.api;

import java.util.Objects;

/** Versioned numeric evidence; allocated only for a detection. */
public record CheckEvidence(
        int schemaVersion, double observed, double limit, double tolerance, String model) {
    public CheckEvidence(double observed, double limit, double tolerance, String model) {
        this(1, observed, limit, tolerance, model);
    }

    public CheckEvidence {
        Objects.requireNonNull(model, "model");
        if (schemaVersion != 1
                || !Double.isFinite(observed)
                || !Double.isFinite(limit)
                || !Double.isFinite(tolerance)
                || tolerance < 0
                || model.isBlank()
                || model.length() > 96) throw new IllegalArgumentException("Invalid evidence");
    }
}
