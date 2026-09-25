package dev.catac.internal;

import dev.catac.state.CollisionSnapshot;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

public final class CollisionAnalyzer {
    private static final double EDGE_EPSILON = 1.0E-7;
    private static final double SUPPORT_EPSILON = 0.05;
    private static final double SWEEP_STEP = 0.20;
    private static final int MAX_SWEEP_STEPS = 32;

    public void analyze(Player player, Pos position, CollisionSnapshot snapshot) {
        analyze(player, position, position, snapshot);
    }

    /**
     * Samples every AABB crossed by a move. A 0.20-block step catches thin
     * shapes and prevents an invalid large packet from skipping a wall while
     * retaining a strict upper bound on world reads.
     */
    public void analyze(Player player, Pos from, Pos position, CollisionSnapshot snapshot) {
        snapshot.reset();
        Instance instance = player.getInstance();
        if (instance == null) {
            snapshot.complete(false);
            return;
        }

        BoundingBox box = player.getBoundingBox();
        int minX = floor(position.x() + box.minX() + EDGE_EPSILON);
        int maxX = floor(position.x() + box.maxX() - EDGE_EPSILON);
        int minY = floor(position.y() + box.minY() + EDGE_EPSILON);
        int maxY = floor(position.y() + box.maxY() - EDGE_EPSILON);
        int minZ = floor(position.z() + box.minZ() + EDGE_EPSILON);
        int maxZ = floor(position.z() + box.maxZ() - EDGE_EPSILON);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!instance.isChunkLoaded(x >> 4, z >> 4)) {
                    snapshot.complete(false);
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    Block block = instance.getBlock(x, y, z, Block.Getter.Condition.TYPE);
                    inspectMedium(block, snapshot);
                    if (!block.registry().collisionShape().relativeEnd().isZero() &&
                            block.registry().collisionShape().intersectBox(
                                    new Vec(position.x() - x, position.y() - y, position.z() - z), box)) {
                        snapshot.insideSolid(true);
                    }
                }
            }
        }

        Pos lowered = position.sub(0.0, SUPPORT_EPSILON, 0.0);
        int supportY = floor(position.y() + box.minY() - SUPPORT_EPSILON);
        float highestFriction = 0.6f;
        float highestSpeedFactor = 1.0f;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!instance.isChunkLoaded(x >> 4, z >> 4)) {
                    snapshot.complete(false);
                    continue;
                }
                Block block = instance.getBlock(x, supportY, z, Block.Getter.Condition.TYPE);
                if (block.registry().collisionShape().relativeEnd().isZero()) {
                    continue;
                }
                if (block.registry().collisionShape().intersectBox(
                        new Vec(lowered.x() - x, lowered.y() - supportY, lowered.z() - z), box)) {
                    snapshot.supported(true);
                    highestFriction = Math.max(highestFriction, block.registry().friction());
                    highestSpeedFactor = Math.max(highestSpeedFactor, block.registry().speedFactor());
                    inspectMedium(block, snapshot);
                }
            }
        }
        snapshot.surfaceFriction(highestFriction);
        snapshot.surfaceSpeedFactor(highestSpeedFactor);

        double distance = Math.max(Math.abs(position.x() - from.x()),
                Math.max(Math.abs(position.y() - from.y()), Math.abs(position.z() - from.z())));
        int steps = Math.min(MAX_SWEEP_STEPS, Math.max(1, (int) Math.ceil(distance / SWEEP_STEP)));
        for (int step = 1; step < steps; step++) {
            double ratio = (double) step / steps;
            if (intersectsSolid(player, instance, snapshot,
                    from.x() + (position.x() - from.x()) * ratio,
                    from.y() + (position.y() - from.y()) * ratio,
                    from.z() + (position.z() - from.z()) * ratio)) {
                snapshot.sweptIntoSolid(true);
                return;
            }
            if (!snapshot.complete()) {
                return;
            }
        }
    }

    private static boolean intersectsSolid(Player player, Instance instance, CollisionSnapshot snapshot,
                                           double x, double y, double z) {
        BoundingBox box = player.getBoundingBox();
        int minX = floor(x + box.minX() + EDGE_EPSILON);
        int maxX = floor(x + box.maxX() - EDGE_EPSILON);
        int minY = floor(y + box.minY() + EDGE_EPSILON);
        int maxY = floor(y + box.maxY() - EDGE_EPSILON);
        int minZ = floor(z + box.minZ() + EDGE_EPSILON);
        int maxZ = floor(z + box.maxZ() - EDGE_EPSILON);
        for (int blockX = minX; blockX <= maxX; blockX++) {
            for (int blockZ = minZ; blockZ <= maxZ; blockZ++) {
                if (!instance.isChunkLoaded(blockX >> 4, blockZ >> 4)) {
                    snapshot.complete(false);
                    return false;
                }
                for (int blockY = minY; blockY <= maxY; blockY++) {
                    Block block = instance.getBlock(blockX, blockY, blockZ, Block.Getter.Condition.TYPE);
                    if (!block.registry().collisionShape().relativeEnd().isZero() &&
                            block.registry().collisionShape().intersectBox(
                                    new Vec(x - blockX, y - blockY, z - blockZ), box)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void inspectMedium(Block block, CollisionSnapshot snapshot) {
        if (block.isLiquid()) {
            snapshot.touchingLiquid(true);
        }
        int id = block.id();
        if (id == Block.LADDER.id() || id == Block.VINE.id() ||
                id == Block.WEEPING_VINES.id() || id == Block.WEEPING_VINES_PLANT.id() ||
                id == Block.TWISTING_VINES.id() || id == Block.TWISTING_VINES_PLANT.id() ||
                id == Block.SCAFFOLDING.id()) {
            snapshot.touchingClimbable(true);
        }
        if (id == Block.COBWEB.id() || id == Block.POWDER_SNOW.id() ||
                id == Block.SWEET_BERRY_BUSH.id() || id == Block.HONEY_BLOCK.id() ||
                id == Block.SLIME_BLOCK.id()) {
            snapshot.touchingSlowBlock(true);
        }
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
