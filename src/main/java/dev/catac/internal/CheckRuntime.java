package dev.catac.internal;

import java.util.concurrent.atomic.AtomicBoolean;

/** Internal circuit breaker for a check that throws during evaluation. */
public final class CheckRuntime {
    private final AtomicBoolean active = new AtomicBoolean(true);

    public boolean active() {
        return active.get();
    }

    /**
     * Disables this check and returns true only for the caller that changed its
     * state. This makes fault reporting rate-safe.
     */
    public boolean disableAfterFault() {
        return active.compareAndSet(true, false);
    }
}
