package dev.catac.engine;

import java.util.concurrent.atomic.LongAdder;

/** Lock-free counters kept separate from player/check hot state. */
final class EnforcementMetrics {
    private final LongAdder violationSamples = new LongAdder();
    private final LongAdder alerts = new LongAdder();
    private final LongAdder cancelledPackets = new LongAdder();
    private final LongAdder setbacks = new LongAdder();
    private final LongAdder kicks = new LongAdder();
    private final LongAdder floodDrops = new LongAdder();
    private final LongAdder floodKicks = new LongAdder();

    void violationSample() { violationSamples.increment(); }
    void alert() { alerts.increment(); }
    void decision(boolean cancel, boolean setback, boolean kick) {
        if (cancel) cancelledPackets.increment();
        if (setback) setbacks.increment();
        if (kick) kicks.increment();
    }
    void flood(boolean kick) { floodDrops.increment(); if (kick) floodKicks.increment(); }

    long violationSamples() { return violationSamples.sum(); }
    long alerts() { return alerts.sum(); }
    long cancelledPackets() { return cancelledPackets.sum(); }
    long setbacks() { return setbacks.sum(); }
    long kicks() { return kicks.sum(); }
    long floodDrops() { return floodDrops.sum(); }
    long floodKicks() { return floodKicks.sum(); }
}
