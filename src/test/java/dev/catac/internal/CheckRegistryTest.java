package dev.catac.internal;

import dev.catac.api.CheckCategory;
import dev.catac.api.CheckDescriptor;
import dev.catac.check.CatCheck;
import dev.catac.check.CheckResult;
import dev.catac.check.MovementCheck;
import dev.catac.config.CatACConfig;
import dev.catac.config.CheckPolicy;
import dev.catac.state.MovementFrame;
import dev.catac.state.PlayerData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckRegistryTest {
    private static final CheckDescriptor DESCRIPTOR = new CheckDescriptor(
            "test.movement", "Test movement", CheckCategory.MOVEMENT,
            CheckPolicy.standard(2, 4, 8), true);

    @Test
    void invalidCheckDoesNotMutateRegistry() {
        CheckRegistry registry = new CheckRegistry(CatACConfig.defaults());
        CatCheck invalid = () -> DESCRIPTOR;

        assertThrows(IllegalArgumentException.class, () -> registry.register(invalid));
        assertEquals(0, registry.size());
        assertTrue(registry.packetChecks().isEmpty());
        assertTrue(registry.movementChecks().isEmpty());
    }

    @Test
    void freezesAndRejectsDuplicateIdentifiers() {
        CheckRegistry registry = new CheckRegistry(CatACConfig.defaults());
        registry.register(new TestMovementCheck());

        assertEquals(1, registry.size());
        assertThrows(IllegalArgumentException.class, () -> registry.register(new TestMovementCheck()));

        registry.freeze();
        assertTrue(registry.isFrozen());
        assertThrows(IllegalStateException.class, () -> registry.register(new TestMovementCheck()));
        assertFalse(registry.movementChecks().isEmpty());
    }

    private static final class TestMovementCheck implements MovementCheck {
        @Override
        public CheckDescriptor descriptor() {
            return DESCRIPTOR;
        }

        @Override
        public CheckResult evaluate(MovementFrame frame, PlayerData data) {
            return CheckResult.pass();
        }
    }
}
