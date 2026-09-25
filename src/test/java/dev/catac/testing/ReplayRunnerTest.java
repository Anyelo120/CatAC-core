package dev.catac.testing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReplayRunnerTest {
    @Test
    void executesFramesAtTheirRecordedTimestamp() {
        ReplayReport<String> report = ReplayRunner.run(List.of(
                new ReplayFrame<>(0, 100, "one"),
                new ReplayFrame<>(1, 350, "two")),
                (payload, now) -> payload + '@' + now);

        assertEquals(2, report.frames());
        assertEquals(250, report.durationNanos());
        assertEquals(List.of("one@100", "two@350"), report.results());
    }

    @Test
    void rejectsNonDeterministicOrdering() {
        assertThrows(IllegalArgumentException.class, () -> ReplayRunner.run(List.of(
                new ReplayFrame<>(1, 100, "first"), new ReplayFrame<>(0, 200, "second")),
                (payload, now) -> payload));
    }
}
