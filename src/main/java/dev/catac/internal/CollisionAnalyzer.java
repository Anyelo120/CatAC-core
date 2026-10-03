package dev.catac.internal;

import dev.catac.state.CollisionSnapshot;

import net.minestom.server.collision.*;
import net.minestom.server.coordinate.*;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

import java.util.ArrayList;
import java.util.List;

/** Continuous native shape tests with a strictly bounded broad phase. */
public final class CollisionAnalyzer {
    public static final int MAX_BLOCK_READS = 512;
    private static final double EPS = 1e-7;

    public void analyze(Player player, Pos pos, CollisionSnapshot s) {
        analyze(player, pos, pos, s);
    }

    public void analyze(Player player, Pos from, Pos to, CollisionSnapshot s) {
        s.reset();
        Instance world = player.getInstance();
        if (world == null) {
            s.complete(false);
            return;
        }
        BoundingBox box = player.getBoundingBox();
        double step = player.getAttributeValue(Attribute.STEP_HEIGHT);
        if (!Double.isFinite(step) || step < 0 || step > 16) {
            s.complete(false);
            return;
        }
        int minX = floor(Math.min(from.x(), to.x()) + box.minX() + EPS),
                maxX = floor(Math.max(from.x(), to.x()) + box.maxX() - EPS);
        int minZ = floor(Math.min(from.z(), to.z()) + box.minZ() + EPS),
                maxZ = floor(Math.max(from.z(), to.z()) + box.maxZ() - EPS);
        // Shapes such as fences extend above their block cell.
        int minY = floor(Math.min(from.y(), to.y()) + box.minY() - .051) - 1;
        int maxY = floor(Math.max(from.y(), to.y()) + box.maxY() + step - EPS);
        if (maxX - minX + 1 > MAX_BLOCK_READS
                || maxY - minY + 1 > MAX_BLOCK_READS
                || maxZ - minZ + 1 > MAX_BLOCK_READS) {
            s.complete(false);
            return;
        }
        long cells = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (cells <= 0 || cells > MAX_BLOCK_READS) {
            s.complete(false);
            return;
        }
        List<Cell> solid = new ArrayList<>();
        boolean surface = false;
        Pos lowered = to.sub(0, .05, 0);
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    s.complete(false);
                    return;
                }
                for (int y = minY; y <= maxY; y++) {
                    Block block = world.getBlock(x, y, z, Block.Getter.Condition.TYPE);
                    Shape shape = block.registry().collisionShape();
                    Vec p = new Vec(x, y, z);
                    // Inspect medium only in cells touched by the destination body.
                    if (box.intersectBox(to.sub(p), new BoundingBox(1, 1, 1, Vec.ZERO)))
                        inspectMedium(block, s);
                    if (shape.relativeEnd().isZero()) continue;
                    solid.add(new Cell(p, shape));
                    if (shape.intersectBox(to.sub(p), box)) s.insideSolid(true);
                    if (shape.intersectBox(lowered.sub(p), box)
                            && !shape.intersectBox(to.sub(p), box)) {
                        s.supported(true);
                        if (!surface) {
                            s.surfaceFriction(block.registry().friction());
                            s.surfaceSpeedFactor(block.registry().speedFactor());
                            surface = true;
                        } else {
                            s.surfaceFriction(
                                    Math.max(s.surfaceFriction(), block.registry().friction()));
                            s.surfaceSpeedFactor(
                                    Math.max(
                                            s.surfaceSpeedFactor(),
                                            block.registry().speedFactor()));
                        }
                        inspectMedium(block, s);
                    }
                }
            }
        if (from.samePoint(to)) return;
        if (intersects(solid, box, from)) {
            s.complete(false);
            return;
        }
        if (clear(solid, box, from, to)) return;
        // Vanilla moves on separate axes. A diagonal straight sweep alone would reject legal corner
        // movement.
        Pos yFirst = from.withY(to.y()), xFirst = yFirst.withX(to.x());
        Pos zFirst = yFirst.withZ(to.z());
        if (clear(solid, box, from, yFirst)
                && ((clear(solid, box, yFirst, xFirst) && clear(solid, box, xFirst, to))
                        || (clear(solid, box, yFirst, zFirst) && clear(solid, box, zFirst, to))))
            return;
        // Verify a bounded step route: up, horizontal, down; no destination penetration.
        if (step > 0
                && to.y() - from.y() >= -EPS
                && to.y() - from.y() <= step + EPS
                && !s.insideSolid()) {
            Pos raised = from.add(0, step, 0), across = to.withY(from.y() + step);
            if (clear(solid, box, from, raised)
                    && clear(solid, box, raised, across)
                    && clear(solid, box, across, to)) return;
        }
        s.sweptIntoSolid(true);
    }

    private static boolean intersects(List<Cell> cells, BoundingBox box, Pos p) {
        for (Cell c : cells) if (c.shape.intersectBox(p.sub(c.pos), box)) return true;
        return false;
    }

    private static boolean clear(List<Cell> cells, BoundingBox box, Pos from, Pos to) {
        Vec delta = new Vec(to.x() - from.x(), to.y() - from.y(), to.z() - from.z());
        if (delta.isZero()) return !intersects(cells, box, to);
        for (Cell c : cells) if (swept(c.shape, c.pos, box, from, delta)) return false;
        return true;
    }

    public static boolean swept(
            Shape shape, Point shapePos, BoundingBox box, Point from, Point delta) {
        box =
                new BoundingBox(
                        box.relativeStart().add(EPS, EPS, EPS),
                        box.relativeEnd().sub(EPS, EPS, EPS));
        // Native RayUtils moves collision time back by 0.99999; exclude an endpoint-only contact.
        SweepResult result = new SweepResult(1 - 2e-5, 0, 0, 0, null, 0, 0, 0, 0, 0, 0);
        return shape.intersectBoxSwept(from, delta, shapePos, box, result);
    }

    private record Cell(Vec pos, Shape shape) {}

    private static int floor(double x) {
        return (int) Math.floor(x);
    }

    private static void inspectMedium(Block b, CollisionSnapshot s) {
        if (b.isLiquid()) s.touchingLiquid(true);
        int id = b.id();
        if (id == Block.LADDER.id()
                || id == Block.VINE.id()
                || id == Block.WEEPING_VINES.id()
                || id == Block.WEEPING_VINES_PLANT.id()
                || id == Block.TWISTING_VINES.id()
                || id == Block.TWISTING_VINES_PLANT.id()
                || id == Block.SCAFFOLDING.id()) s.touchingClimbable(true);
        if (id == Block.COBWEB.id()
                || id == Block.POWDER_SNOW.id()
                || id == Block.SWEET_BERRY_BUSH.id()
                || id == Block.HONEY_BLOCK.id()
                || id == Block.SLIME_BLOCK.id()) s.touchingSlowBlock(true);
    }
}
