package dev.catac.api;

/** Immutable cumulative diagnostics; decisions and applied actions are separate. */
public record CheckDiagnostics(
        String id,
        boolean active,
        boolean enabled,
        long evaluations,
        long passes,
        long failures,
        long skipped,
        long uncertain,
        long faults,
        long totalNanos,
        long maximumNanos,
        long bypassed,
        java.util.Map<dev.catac.check.SkipReason, Long> reasons) {
    public CheckDiagnostics {
        reasons = java.util.Map.copyOf(reasons);
    }
}
