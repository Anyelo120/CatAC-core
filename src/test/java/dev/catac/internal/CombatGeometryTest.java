package dev.catac.internal;

import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Pos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatGeometryTest {
    private static final BoundingBox BOX = new BoundingBox(0.6, 1.8, 0.6);

    @Test
    void measuresDistanceToTheNearestPointOfTheHitbox() {
        double distanceSquared = CombatGeometry.distanceSquared(0.0, 1.62, 0.0, BOX, 3.0, 0.0, 0.0);
        assertEquals(7.29, distanceSquared, 1.0E-9);
    }

    @Test
    void distinguishesTargetsInFrontOfAndBehindTheView() {
        Pos lookingSouth = new Pos(0.0, 0.0, 0.0, 0.0f, 0.0f);
        double front = CombatGeometry.directionDot(lookingSouth, 0.0, 1.62, 0.0, BOX, 0.0, 0.0, 3.0);
        double back = CombatGeometry.directionDot(lookingSouth, 0.0, 1.62, 0.0, BOX, 0.0, 0.0, -3.0);

        assertTrue(front > 0.0);
        assertTrue(back < 0.0);
    }
}
