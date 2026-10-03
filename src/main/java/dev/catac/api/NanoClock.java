package dev.catac.api;

/** Monotonic elapsed-time source. Its origin may be negative. */
@FunctionalInterface
public interface NanoClock {
    NanoClock SYSTEM = System::nanoTime;

    long nanoTime();
}
