package dev.catac.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnforcementMetricsTest {
    @Test
    void accumulatesEveryDecisionTypeIndependently() {
        EnforcementMetrics metrics = new EnforcementMetrics();
        metrics.violationSample();
        metrics.violationSample();
        metrics.alert();
        metrics.decision(true, false, false);
        metrics.decision(true, true, false);
        metrics.decision(true, false, true);
        metrics.flood(false);
        metrics.flood(true);

        assertEquals(2, metrics.violationSamples());
        assertEquals(1, metrics.alerts());
        assertEquals(3, metrics.cancelledPackets());
        assertEquals(1, metrics.setbacks());
        assertEquals(1, metrics.kicks());
        assertEquals(2, metrics.floodDrops());
        assertEquals(1, metrics.floodKicks());
    }
}
