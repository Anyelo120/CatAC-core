package dev.catac.regression;

import static org.junit.jupiter.api.Assertions.*;

import dev.catac.internal.*;
import dev.catac.state.*;
import dev.catac.support.MinestomFixture;

import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.*;
import net.minestom.server.instance.block.Block;

import org.junit.jupiter.api.Test;

class GeometryRegressionTest {
    private final CollisionAnalyzer analyzer = new CollisionAnalyzer();

    @Test
    void walkingOnAFlatFloorDoesNotPhase() {
        try (var f = new MinestomFixture()) {
            var s = new CollisionSnapshot();
            analyzer.analyze(f.player, new Pos(.5, 1, .5), new Pos(1.5, 1, .5), s);
            assertTrue(s.complete());
            assertTrue(s.supported());
            assertFalse(s.insideSolid());
            assertFalse(s.sweptIntoSolid());
        }
    }

    @Test
    void landingOnAFloorIsAContactNotPenetration() {
        try (var f = new MinestomFixture()) {
            var s = new CollisionSnapshot();
            analyzer.analyze(f.player, new Pos(.5, 1.121298, .5), new Pos(.5, 1, .5), s);
            assertTrue(s.complete());
            assertTrue(s.supported(), "landing must be supported");
            assertFalse(s.insideSolid());
            assertFalse(s.sweptIntoSolid(), "landing must not penetrate");
        }
    }

    @Test
    void aLongMoveCannotSkipAThinPane() {
        try (var f = new MinestomFixture()) {
            f.world.setBlock(4, 1, 0, Block.GLASS_PANE);
            f.world.setBlock(4, 2, 0, Block.GLASS_PANE);
            var s = new CollisionSnapshot();
            analyzer.analyze(f.player, new Pos(.5, 1, .5), new Pos(14.5, 1, .5), s);
            assertTrue(s.complete());
            assertFalse(s.insideSolid());
            assertTrue(s.sweptIntoSolid());
        }
    }

    @Test
    void supportsASlabAndAllowsItsHalfBlockStep() {
        try (var f = new MinestomFixture()) {
            f.world.setBlock(1, 1, 0, Block.STONE_SLAB.withProperty("type", "bottom"));
            var s = new CollisionSnapshot();
            analyzer.analyze(f.player, new Pos(.5, 1, .5), new Pos(1.5, 1.5, .5), s);
            assertTrue(s.complete());
            assertTrue(s.supported());
            assertFalse(s.insideSolid());
            assertFalse(s.sweptIntoSolid());
        }
    }

    @Test
    void recognizesFenceSupportAboveItsOwnCell() {
        try (var f = new MinestomFixture()) {
            f.world.setBlock(1, 1, 0, Block.OAK_FENCE);
            var s = new CollisionSnapshot();
            analyzer.analyze(f.player, new Pos(1.5, 2.5, .5), s);
            assertTrue(s.complete());
            assertTrue(s.supported());
            assertFalse(s.insideSolid());
        }
    }

    @Test
    void aLargeQueryIsExplicitlyIncomplete() {
        try (var f = new MinestomFixture()) {
            var s = new CollisionSnapshot();
            analyzer.analyze(f.player, new Pos(.5, 1, .5), new Pos(500, 1, .5), s);
            assertFalse(s.complete());
        }
    }

    @Test
    void slowsOnSoulSandWithoutLosingItsFactor() {
        try (var f = new MinestomFixture()) {
            f.world.setBlock(0, 0, 0, Block.SOUL_SAND);
            var s = new CollisionSnapshot();
            analyzer.analyze(f.player, new Pos(.5, .875, .5), s);
            assertTrue(s.complete());
            assertTrue(s.supported());
            assertEquals(.4, s.surfaceSpeedFactor(), 1e-6);
        }
    }

    @Test
    void exactLineOfSightFindsAnUnalignedThinShape() {
        try (var f = new MinestomFixture()) {
            f.world.setBlock(2, 1, 0, Block.IRON_BARS);
            f.world.setBlock(2, 2, 0, Block.IRON_BARS);
            assertEquals(
                    CombatGeometry.LineOfSight.BLOCKED,
                    CombatGeometry.segment(
                            f.world, new Vec(.5, 2.62, .5), new Vec(4.5, 2.62, .5), null));
            assertEquals(
                    CombatGeometry.LineOfSight.CLEAR,
                    CombatGeometry.segment(
                            f.world, new Vec(.5, 2.62, 2.5), new Vec(4.5, 2.62, 2.5), null));
        }
    }

    @Test
    void rayDistanceHandlesAxisParallelAndRearTargets() {
        var box = new BoundingBox(.6, 1.8, .6);
        var eye = new Vec(0, 1.62, 0);
        assertEquals(
                2.7,
                CombatGeometry.rayDistance(eye, new Vec(1, 0, 0), box, new Vec(3, 0, 0), 0),
                1e-8);
        assertEquals(
                Double.POSITIVE_INFINITY,
                CombatGeometry.rayDistance(eye, new Vec(-1, 0, 0), box, new Vec(3, 0, 0), 0));
        assertEquals(
                Double.POSITIVE_INFINITY,
                CombatGeometry.rayDistance(eye, new Vec(0, 0, 1), box, new Vec(3, 0, 0), 0));
    }

    @Test
    void rewindNeverPretendsAnOutOfRangeSampleIsHistorical() {
        var h = new PositionHistory();
        h.add(new Pos(0, 1, 0), -1000);
        h.add(new Pos(2, 1, 0), -500);
        var p = h.rewind(-1500, new Pos(10, 1, 0));
        assertFalse(p.historical());
        assertEquals(PositionHistory.Quality.OUT_OF_RANGE, p.quality());
        assertTrue(h.rewind(-750, Pos.ZERO).historical());
        h.clear();
        assertFalse(h.rewind(-750, Pos.ZERO).historical());
    }

    @Test
    void historyDoesNotInterpolateAcrossPoseOrWorldChanges() {
        try (var f = new MinestomFixture()) {
            var h = new PositionHistory();
            var standing = new BoundingBox(.6, 1.8, .6);
            var crouching = new BoundingBox(.6, 1.5, .6);
            h.add(new Pos(0, 1, 0), standing, f.world, -1000);
            h.add(new Pos(1, 1, 0), crouching, f.world, -500);
            assertFalse(h.rewind(-750, Pos.ZERO, standing, f.world).historical());
            assertFalse(h.rewind(-500, Pos.ZERO, standing, null).historical());
        }
    }
}
