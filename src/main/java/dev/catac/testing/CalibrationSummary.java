package dev.catac.testing;

/** Per-check aggregate used to choose initial monitor/alert/setback thresholds. */
public record CalibrationSummary(String checkId, long samples, long cancelled, double averageSeverity,
                                 double maxSeverity) {
}
