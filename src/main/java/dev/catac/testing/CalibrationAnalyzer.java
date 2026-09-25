package dev.catac.testing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Offline aggregation of samples exported by a host application's violation handler. */
public final class CalibrationAnalyzer {
    private CalibrationAnalyzer() {
    }

    public static List<CalibrationSummary> summarize(List<CalibrationSample> samples) {
        Objects.requireNonNull(samples, "samples");
        Map<String, MutableSummary> grouped = new HashMap<>();
        for (CalibrationSample sample : samples) {
            Objects.requireNonNull(sample, "samples cannot contain null");
            grouped.computeIfAbsent(sample.checkId(), MutableSummary::new).add(sample);
        }
        List<CalibrationSummary> summaries = new ArrayList<>(grouped.size());
        for (MutableSummary summary : grouped.values()) summaries.add(summary.freeze());
        summaries.sort(Comparator.comparing(CalibrationSummary::checkId));
        return List.copyOf(summaries);
    }

    private static final class MutableSummary {
        private final String checkId;
        private long samples;
        private long cancelled;
        private double severityTotal;
        private double maximum;

        private MutableSummary(String checkId) { this.checkId = checkId; }
        private void add(CalibrationSample sample) {
            samples++;
            if (sample.cancelled()) cancelled++;
            severityTotal += sample.severity();
            maximum = Math.max(maximum, sample.severity());
        }
        private CalibrationSummary freeze() {
            return new CalibrationSummary(checkId, samples, cancelled, severityTotal / samples, maximum);
        }
    }
}
