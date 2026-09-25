package dev.catac.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CheckPolicyTest {
    @Test
    void rejectsThresholdsInTheWrongOrder() {
        assertThrows(IllegalArgumentException.class,
                () -> new CheckPolicy(true, 5, 4, 10, 0.2, 1_000));
        assertThrows(IllegalArgumentException.class,
                () -> new CheckPolicy(true, 5, 8, 7, 0.2, 1_000));
    }

    @Test
    void disabledCopyKeepsTuningValues() {
        CheckPolicy disabled = CheckPolicy.standard(3, 6, 12).disabled();
        assertFalse(disabled.enabled());
    }
}
