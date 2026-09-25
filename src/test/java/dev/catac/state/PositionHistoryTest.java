package dev.catac.state;

import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Pos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PositionHistoryTest {
    @Test
    void usesRecentHistoricalPositionForLatencyRewind() {
        PositionHistory history = new PositionHistory();
        BoundingBox playerBox = new BoundingBox(0.6, 1.8, 0.6);
        history.add(new Pos(3.0, 0.0, 0.0), 100);
        history.add(new Pos(2.5, 0.0, 0.0), 200);

        double distanceSquared = history.minimumEyeToBoxDistanceSquared(
                0.0, 1.62, 0.0, playerBox, 150, new Pos(4.0, 0.0, 0.0));

        assertEquals(4.84, distanceSquared, 1.0E-9);
    }

    @Test
    void ringBufferHasFixedCapacity() {
        PositionHistory history = new PositionHistory();
        for (int i = 0; i < 100; i++) {
            history.add(new Pos(i, 0, 0), i);
        }
        assertEquals(32, history.size());
    }

    @Test
    void interpolatesThePositionAtTheRequestedAttackInstant() {
        PositionHistory history = new PositionHistory();
        history.add(new Pos(0.0, 0.0, 0.0), 100);
        history.add(new Pos(2.0, 4.0, 6.0), 300);

        PositionHistory.RewoundPosition position = history.rewind(200, new Pos(100, 100, 100));

        assertEquals(1.0, position.x());
        assertEquals(2.0, position.y());
        assertEquals(3.0, position.z());
        assertEquals(true, position.historical());
    }
}
