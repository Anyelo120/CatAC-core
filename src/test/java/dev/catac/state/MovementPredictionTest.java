package dev.catac.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementPredictionTest {
    @Test
    void modelsGroundInertiaAndAccelerationSeparately() {
        double next = MovementPrediction.nextGroundHorizontal(0.20, 0.10, 0.546);
        assertEquals(0.2092, next, 0.000_001);
    }

    @Test
    void sneakingAirInputIsBelowRegularAirInput() {
        double regular = MovementPrediction.nextAirHorizontal(0.20, 0.10, false);
        double sneaking = MovementPrediction.nextAirHorizontal(0.20, 0.10, true);
        assertTrue(regular > sneaking);
    }

    @Test
    void appliesVanillaGravityThenVerticalDrag() {
        assertEquals(0.3332, MovementPrediction.nextVertical(0.42), 0.000_001);
    }
}
