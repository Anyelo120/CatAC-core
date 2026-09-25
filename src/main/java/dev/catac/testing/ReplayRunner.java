package dev.catac.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Executes a pre-captured timeline in strict timestamp and sequence order. */
public final class ReplayRunner {
    private ReplayRunner() {
    }

    public static <T, R> ReplayReport<R> run(List<ReplayFrame<T>> frames, ReplayExecutor<T, R> executor) {
        Objects.requireNonNull(frames, "frames");
        Objects.requireNonNull(executor, "executor");
        long previousSequence = -1L;
        long previousNanos = -1L;
        List<R> results = new ArrayList<>(frames.size());
        for (ReplayFrame<T> frame : frames) {
            Objects.requireNonNull(frame, "frames cannot contain null");
            if (frame.sequence() <= previousSequence || frame.timestampNanos() < previousNanos) {
                throw new IllegalArgumentException("frames must be strictly sequenced and time ordered");
            }
            results.add(executor.execute(frame.payload(), frame.timestampNanos()));
            previousSequence = frame.sequence();
            previousNanos = frame.timestampNanos();
        }
        long duration = frames.size() < 2 ? 0L : previousNanos - frames.getFirst().timestampNanos();
        return new ReplayReport<>(frames.size(), duration, results);
    }
}
