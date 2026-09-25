package dev.catac.state;

import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Pos;

public final class PositionHistory {
    private static final int CAPACITY = 32;

    private final double[] x = new double[CAPACITY];
    private final double[] y = new double[CAPACITY];
    private final double[] z = new double[CAPACITY];
    private final long[] time = new long[CAPACITY];
    private int writeIndex;
    private int size;

    public void add(Pos position, long nowNanos) {
        x[writeIndex] = position.x();
        y[writeIndex] = position.y();
        z[writeIndex] = position.z();
        time[writeIndex] = nowNanos;
        writeIndex = (writeIndex + 1) % CAPACITY;
        if (size < CAPACITY) {
            size++;
        }
    }

    public double minimumEyeToBoxDistanceSquared(
            double eyeX, double eyeY, double eyeZ,
            BoundingBox box, long notBeforeNanos,
            Pos fallback
    ) {
        double minimum = distanceSquared(eyeX, eyeY, eyeZ, box,
                fallback.x(), fallback.y(), fallback.z());

        for (int i = 0; i < size; i++) {
            int index = writeIndex - 1 - i;
            if (index < 0) {
                index += CAPACITY;
            }
            if (time[index] < notBeforeNanos) {
                break;
            }
            minimum = Math.min(minimum, distanceSquared(
                    eyeX, eyeY, eyeZ, box, x[index], y[index], z[index]));
        }
        return minimum;
    }

    /**
     * Returns the position represented by the requested instant. Samples are
     * interpolated only between adjacent observations; this never searches for
     * the most favourable target position in a latency window.
     */
    public RewoundPosition rewind(long targetNanos, Pos fallback) {
        if (size == 0) {
            return new RewoundPosition(fallback.x(), fallback.y(), fallback.z(), false);
        }
        int oldest = (writeIndex - size + CAPACITY) % CAPACITY;
        int newest = (writeIndex - 1 + CAPACITY) % CAPACITY;
        if (targetNanos <= time[oldest]) {
            return at(oldest);
        }
        if (targetNanos >= time[newest]) {
            return at(newest);
        }
        for (int offset = 0; offset < size - 1; offset++) {
            int before = (oldest + offset) % CAPACITY;
            int after = (before + 1) % CAPACITY;
            if (targetNanos > time[after]) {
                continue;
            }
            long interval = time[after] - time[before];
            if (interval <= 0L) {
                return at(before);
            }
            double factor = (double) (targetNanos - time[before]) / interval;
            return new RewoundPosition(
                    x[before] + (x[after] - x[before]) * factor,
                    y[before] + (y[after] - y[before]) * factor,
                    z[before] + (z[after] - z[before]) * factor,
                    true);
        }
        return at(newest);
    }

    public int size() {
        return size;
    }

    private static double distanceSquared(double eyeX, double eyeY, double eyeZ,
                                          BoundingBox box, double targetX, double targetY, double targetZ) {
        double minX = targetX + box.minX();
        double maxX = targetX + box.maxX();
        double minY = targetY + box.minY();
        double maxY = targetY + box.maxY();
        double minZ = targetZ + box.minZ();
        double maxZ = targetZ + box.maxZ();

        double closestX = Math.clamp(eyeX, minX, maxX);
        double closestY = Math.clamp(eyeY, minY, maxY);
        double closestZ = Math.clamp(eyeZ, minZ, maxZ);
        double dx = eyeX - closestX;
        double dy = eyeY - closestY;
        double dz = eyeZ - closestZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private RewoundPosition at(int index) {
        return new RewoundPosition(x[index], y[index], z[index], true);
    }

    public record RewoundPosition(double x, double y, double z, boolean historical) {
    }
}
