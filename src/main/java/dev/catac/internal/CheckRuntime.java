package dev.catac.internal;

import dev.catac.api.CheckDiagnostics;
import dev.catac.check.CheckResult;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/** Bounded circuit breaker and contention-safe aggregate diagnostics. */
public final class CheckRuntime {
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final LongAdder evaluations = new LongAdder(),
            passes = new LongAdder(),
            failures = new LongAdder(),
            skipped = new LongAdder(),
            uncertain = new LongAdder(),
            faults = new LongAdder(),
            nanos = new LongAdder();
    private final LongAdder bypassed = new LongAdder();
    private final LongAdder[] reasons = new LongAdder[dev.catac.check.SkipReason.values().length];

    public CheckRuntime() {
        for (int i = 0; i < reasons.length; i++) reasons[i] = new LongAdder();
    }

    public void bypass(dev.catac.check.SkipReason reason) {
        bypassed.increment();
        reasons[reason.ordinal()].increment();
    }

    private final AtomicLong maximum = new AtomicLong();

    public boolean active() {
        return active.get();
    }

    public boolean disableAfterFault() {
        faults.increment();
        return active.compareAndSet(true, false);
    }

    public void record(CheckResult result, long elapsed) {
        if (result.skipReason() != null) reasons[result.skipReason().ordinal()].increment();
        evaluations.increment();
        nanos.add(Math.max(0, elapsed));
        maximum.accumulateAndGet(Math.max(0, elapsed), Math::max);
        switch (result.outcome()) {
            case PASS -> passes.increment();
            case FAIL, MALFORMED -> failures.increment();
            case UNCERTAIN -> uncertain.increment();
            case NOT_APPLICABLE -> skipped.increment();
        }
    }

    public CheckDiagnostics snapshot(String id, boolean enabled) {
        java.util.Map<dev.catac.check.SkipReason, Long> map =
                new java.util.EnumMap<>(dev.catac.check.SkipReason.class);
        for (var reason : dev.catac.check.SkipReason.values()) {
            long count = reasons[reason.ordinal()].sum();
            if (count > 0) map.put(reason, count);
        }
        return new CheckDiagnostics(
                id,
                active(),
                enabled,
                evaluations.sum(),
                passes.sum(),
                failures.sum(),
                skipped.sum(),
                uncertain.sum(),
                faults.sum(),
                nanos.sum(),
                maximum.get(),
                bypassed.sum(),
                map);
    }
}
