package dev.catac.testing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalibrationAnalyzerTest {
    @Test
    void aggregatesPerCheckAndSortsTheOutput() {
        List<CalibrationSummary> summaries = CalibrationAnalyzer.summarize(List.of(
                new CalibrationSample("movement.speed", 2.0, false),
                new CalibrationSample("combat.reach", 4.0, true),
                new CalibrationSample("movement.speed", 6.0, true)));

        assertEquals("combat.reach", summaries.getFirst().checkId());
        CalibrationSummary speed = summaries.get(1);
        assertEquals(2, speed.samples());
        assertEquals(1, speed.cancelled());
        assertEquals(4.0, speed.averageSeverity());
        assertEquals(6.0, speed.maxSeverity());
    }
}
