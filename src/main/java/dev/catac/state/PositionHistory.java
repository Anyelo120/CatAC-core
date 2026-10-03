package dev.catac.state;

import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.Instance;

import java.util.Objects;

/** Synchronized bounded snapshots including dimensions, world identity and discontinuities. */
public final class PositionHistory {
    private static final int CAPACITY = 64;
    private final Sample[] samples = new Sample[CAPACITY];
    private int next, size;
    private long generation;

    public synchronized void add(Pos pos, long now) {
        add(pos, new BoundingBox(.6, 1.8, .6), null, now);
    }

    public synchronized void add(Pos pos, BoundingBox box, Instance instance, long now) {
        Objects.requireNonNull(pos);
        Objects.requireNonNull(box);
        Sample previous = size == 0 ? null : samples[(next - 1 + CAPACITY) % CAPACITY];
        if (previous != null
                && (previous.instance != instance
                        || now - previous.time < 0
                        || now - previous.time > 250_000_000L
                        || previous.pos.distanceSquared(pos) > 64)) clear();
        previous = size == 0 ? null : samples[(next - 1 + CAPACITY) % CAPACITY];
        Sample sample = new Sample(pos, box, instance, now, generation);
        if (previous != null && previous.time == now) {
            samples[(next - 1 + CAPACITY) % CAPACITY] = sample;
            return;
        }
        samples[next] = sample;
        next = (next + 1) % CAPACITY;
        size = Math.min(size + 1, CAPACITY);
    }

    public synchronized void clear() {
        java.util.Arrays.fill(samples, null);
        next = 0;
        size = 0;
        generation++;
    }

    public synchronized int size() {
        return size;
    }

    public synchronized long generation() {
        return generation;
    }

    public synchronized RewoundPosition rewind(long target, Pos fallback) {
        return rewind(target, fallback, new BoundingBox(.6, 1.8, .6), null);
    }

    public synchronized RewoundPosition rewind(
            long target, Pos fallback, BoundingBox fallbackBox, Instance instance) {
        if (size == 0) return fallback(fallback, fallbackBox, Quality.MISSING);
        Sample oldest = samples[(next - size + CAPACITY) % CAPACITY],
                newest = samples[(next - 1 + CAPACITY) % CAPACITY];
        if (oldest.instance != instance || newest.instance != instance)
            return fallback(fallback, fallbackBox, Quality.DISCONTINUITY);
        if (target - oldest.time < 0 || target - newest.time > 0)
            return fallback(fallback, fallbackBox, Quality.OUT_OF_RANGE);
        for (int i = 0; i < size; i++) {
            Sample before = samples[(next - size + i + CAPACITY) % CAPACITY];
            if (target == before.time) return at(before, Quality.EXACT);
            if (i == size - 1) break;
            Sample after = samples[(next - size + i + 1 + CAPACITY) % CAPACITY];
            if (target - before.time < 0 || target - after.time > 0) continue;
            if (before.generation != after.generation || !before.box.equals(after.box))
                return fallback(fallback, fallbackBox, Quality.DISCONTINUITY);
            long interval = after.time - before.time;
            if (interval <= 0 || interval > 250_000_000L)
                return fallback(fallback, fallbackBox, Quality.GAP);
            double f = (double) (target - before.time) / interval;
            return new RewoundPosition(
                    before.pos.x() + (after.pos.x() - before.pos.x()) * f,
                    before.pos.y() + (after.pos.y() - before.pos.y()) * f,
                    before.pos.z() + (after.pos.z() - before.pos.z()) * f,
                    true,
                    before.box,
                    Quality.INTERPOLATED);
        }
        return fallback(fallback, fallbackBox, Quality.MISSING);
    }

    public synchronized double minimumEyeToBoxDistanceSquared(
            double ex, double ey, double ez, BoundingBox box, long notBefore, Pos fallback) {
        double result =
                dev.catac.internal.CombatGeometry.distanceSquared(
                        ex, ey, ez, box, fallback.x(), fallback.y(), fallback.z());
        for (Sample sample : samples)
            if (sample != null && sample.time - notBefore >= 0)
                result =
                        Math.min(
                                result,
                                dev.catac.internal.CombatGeometry.distanceSquared(
                                        ex,
                                        ey,
                                        ez,
                                        sample.box,
                                        sample.pos.x(),
                                        sample.pos.y(),
                                        sample.pos.z()));
        return result;
    }

    private RewoundPosition at(Sample s, Quality quality) {
        return new RewoundPosition(s.pos.x(), s.pos.y(), s.pos.z(), true, s.box, quality);
    }

    private RewoundPosition fallback(Pos p, BoundingBox box, Quality quality) {
        return new RewoundPosition(p.x(), p.y(), p.z(), false, box, quality);
    }

    private record Sample(
            Pos pos, BoundingBox box, Instance instance, long time, long generation) {}

    public enum Quality {
        EXACT,
        INTERPOLATED,
        MISSING,
        OUT_OF_RANGE,
        DISCONTINUITY,
        GAP
    }

    public record RewoundPosition(
            double x, double y, double z, boolean historical, BoundingBox box, Quality quality) {
        public RewoundPosition(double x, double y, double z, boolean historical) {
            this(
                    x,
                    y,
                    z,
                    historical,
                    new BoundingBox(.6, 1.8, .6),
                    historical ? Quality.EXACT : Quality.MISSING);
        }
    }
}
