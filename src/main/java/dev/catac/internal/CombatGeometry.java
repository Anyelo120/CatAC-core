package dev.catac.internal;

import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.*;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

/** Slab ray/AABB intersection and continuous shape occlusion; no ray sampling gaps. */
public final class CombatGeometry {
    private static final BoundingBox POINT = new BoundingBox(.00002, .00002, .00002);

    private CombatGeometry() {}

    public static double distanceSquared(
            double ex, double ey, double ez, BoundingBox b, double x, double y, double z) {
        double dx = ex - Math.clamp(ex, x + b.minX(), x + b.maxX()),
                dy = ey - Math.clamp(ey, y + b.minY(), y + b.maxY()),
                dz = ez - Math.clamp(ez, z + b.minZ(), z + b.maxZ());
        return dx * dx + dy * dy + dz * dz;
    }

    public static double directionDot(
            Pos view,
            double ex,
            double ey,
            double ez,
            BoundingBox b,
            double x,
            double y,
            double z) {
        Vec d =
                new Vec(
                        Math.clamp(ex, x + b.minX(), x + b.maxX()) - ex,
                        Math.clamp(ey, y + b.minY(), y + b.maxY()) - ey,
                        Math.clamp(ez, z + b.minZ(), z + b.maxZ()) - ez);
        return d.lengthSquared() < 1e-12 ? 1 : view.direction().dot(d) / d.length();
    }

    /** Distance along a normalized ray, or +infinity when there is no forward hit. */
    public static double rayDistance(
            Point eye, Vec direction, BoundingBox b, Point target, double expansion) {
        double lo = 0, hi = Double.POSITIVE_INFINITY;
        double[] origin = {eye.x(), eye.y(), eye.z()},
                dir = {direction.x(), direction.y(), direction.z()},
                min =
                        {
                            target.x() + b.minX() - expansion,
                            target.y() + b.minY() - expansion,
                            target.z() + b.minZ() - expansion
                        },
                max =
                        {
                            target.x() + b.maxX() + expansion,
                            target.y() + b.maxY() + expansion,
                            target.z() + b.maxZ() + expansion
                        };
        for (int i = 0; i < 3; i++) {
            if (Math.abs(dir[i]) < 1e-12) {
                if (origin[i] < min[i] || origin[i] > max[i]) return Double.POSITIVE_INFINITY;
                continue;
            }
            double a = (min[i] - origin[i]) / dir[i], c = (max[i] - origin[i]) / dir[i];
            lo = Math.max(lo, Math.min(a, c));
            hi = Math.min(hi, Math.max(a, c));
            if (hi < lo) return Double.POSITIVE_INFINITY;
        }
        return lo;
    }

    public static LineOfSight lineOfSight(
            Instance world,
            double ex,
            double ey,
            double ez,
            BoundingBox b,
            double x,
            double y,
            double z) {
        return segment(
                world,
                new Vec(ex, ey, ez),
                new Vec(
                        Math.clamp(ex, x + b.minX(), x + b.maxX()),
                        Math.clamp(ey, y + b.minY(), y + b.maxY()),
                        Math.clamp(ez, z + b.minZ(), z + b.maxZ())),
                null);
    }

    public static LineOfSight segment(Instance world, Point start, Point end, Point ignoredBlock) {
        if (world == null) return LineOfSight.INCOMPLETE;
        Vec delta = new Vec(end.x() - start.x(), end.y() - start.y(), end.z() - start.z());
        int minX = (int) Math.floor(Math.min(start.x(), end.x())),
                maxX = (int) Math.floor(Math.max(start.x(), end.x())),
                minZ = (int) Math.floor(Math.min(start.z(), end.z())),
                maxZ = (int) Math.floor(Math.max(start.z(), end.z())),
                minY = (int) Math.floor(Math.min(start.y(), end.y())) - 1,
                maxY = (int) Math.floor(Math.max(start.y(), end.y()));
        if (maxX - minX + 1 > 512 || maxY - minY + 1 > 512 || maxZ - minZ + 1 > 512)
            return LineOfSight.INCOMPLETE;
        if ((long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1) > 512)
            return LineOfSight.INCOMPLETE;
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) return LineOfSight.INCOMPLETE;
                for (int y = minY; y <= maxY; y++) {
                    if (ignoredBlock != null
                            && ignoredBlock.blockX() == x
                            && ignoredBlock.blockY() == y
                            && ignoredBlock.blockZ() == z) continue;
                    var shape =
                            world.getBlock(x, y, z, Block.Getter.Condition.TYPE)
                                    .registry()
                                    .collisionShape();
                    if (shape.relativeEnd().isZero()) continue;
                    Vec pos = new Vec(x, y, z);
                    if (shape.intersectBox(start.sub(pos), POINT)
                            || CollisionAnalyzer.swept(shape, pos, POINT, start, delta))
                        return LineOfSight.BLOCKED;
                }
            }
        return LineOfSight.CLEAR;
    }

    public enum LineOfSight {
        CLEAR,
        BLOCKED,
        INCOMPLETE
    }
}
