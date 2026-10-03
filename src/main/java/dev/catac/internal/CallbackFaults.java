package dev.catac.internal;

import dev.catac.state.TimeWindow;

import java.util.concurrent.atomic.LongAdder;

/** Fixed-key fault counters with one log per category per five seconds across all players. */
public final class CallbackFaults {
    public enum Kind {
        VIOLATION_EVENT,
        VIOLATION_HANDLER,
        PLAYER_NOTICE,
        FLOOD_EVENT
    }

    private static final System.Logger LOG = System.getLogger(CallbackFaults.class.getName());
    private final TimeWindow[] windows = new TimeWindow[Kind.values().length];
    private final LongAdder faults = new LongAdder();

    public CallbackFaults() {
        for (int i = 0; i < windows.length; i++) windows[i] = new TimeWindow();
    }

    public void report(Kind kind, long now, RuntimeException failure) {
        faults.increment();
        boolean log;
        synchronized (windows) {
            var window = windows[kind.ordinal()];
            log = !window.active(now);
            if (log) window.open(now, 5_000_000_000L);
        }
        if (log) LOG.log(System.Logger.Level.ERROR, "CatAC callback failed: " + kind, failure);
    }

    public long count() {
        return faults.sum();
    }
}
