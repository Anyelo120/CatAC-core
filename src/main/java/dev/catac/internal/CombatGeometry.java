package dev.catac.internal;

import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

/** Allocation-bounded geometric helpers for a single melee attack. */
public final class CombatGeometry {
    private static final double RAY_STEP = 0.20;
    private static final int MAX_RAY_STEPS = 32;
    private static final BoundingBox POINT_BOX = new BoundingBox(0.002, 0.002, 0.002);

    private CombatGeometry() {
    }

    public static double distanceSquared(double eyeX, double eyeY, double eyeZ,
                                         BoundingBox box, double targetX, double targetY, double targetZ) {
        double dx = eyeX - closest(eyeX, targetX + box.minX(), targetX + box.maxX());
        double dy = eyeY - closest(eyeY, targetY + box.minY(), targetY + box.maxY());
        double dz = eyeZ - closest(eyeZ, targetZ + box.minZ(), targetZ + box.maxZ());
        return dx * dx + dy * dy + dz * dz;
    }

    public static double directionDot(Pos view, double eyeX, double eyeY, double eyeZ,
                                      BoundingBox box, double targetX, double targetY, double targetZ) {
        double pointX = closest(eyeX, targetX + box.minX(), targetX + box.maxX());
        double pointY = closest(eyeY, targetY + box.minY(), targetY + box.maxY());
        double pointZ = closest(eyeZ, targetZ + box.minZ(), targetZ + box.maxZ());
        double dx = pointX - eyeX;
        double dy = pointY - eyeY;
        double dz = pointZ - eyeZ;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-6) {
            return 1.0;
        }
        Vec direction = view.direction();
        return (direction.x() * dx + direction.y() * dy + direction.z() * dz) / length;
    }

    public static LineOfSight lineOfSight(Instance instance, double eyeX, double eyeY, double eyeZ,
                                          BoundingBox box, double targetX, double targetY, double targetZ) {
        double endX = closest(eyeX, targetX + box.minX(), targetX + box.maxX());
        double endY = closest(eyeY, targetY + box.minY(), targetY + box.maxY());
        double endZ = closest(eyeZ, targetZ + box.minZ(), targetZ + box.maxZ());
        double dx = endX - eyeX;
        double dy = endY - eyeY;
        double dz = endZ - eyeZ;
        int steps = Math.min(MAX_RAY_STEPS, Math.max(1,
                (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) / RAY_STEP)));
        for (int step = 1; step < steps; step++) {
            double ratio = (double) step / steps;
            double x = eyeX + dx * ratio;
            double y = eyeY + dy * ratio;
            double z = eyeZ + dz * ratio;
            int blockX = (int) Math.floor(x);
            int blockY = (int) Math.floor(y);
            int blockZ = (int) Math.floor(z);
            if (!instance.isChunkLoaded(blockX >> 4, blockZ >> 4)) {
                return LineOfSight.INCOMPLETE;
            }
            Block block = instance.getBlock(blockX, blockY, blockZ, Block.Getter.Condition.TYPE);
            if (!block.registry().collisionShape().relativeEnd().isZero() &&
                    block.registry().collisionShape().intersectBox(new Vec(x - blockX, y - blockY, z - blockZ), POINT_BOX)) {
                return LineOfSight.BLOCKED;
            }
        }
        return LineOfSight.CLEAR;
    }

    private static double closest(double value, double min, double max) {
        return Math.clamp(value, min, max);
    }

    public enum LineOfSight { CLEAR, BLOCKED, INCOMPLETE }
}
