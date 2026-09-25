package dev.catac.check;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CheckResultTest {
    @Test
    void boundsUntrustedEvidence() {
        String evidence = "x".repeat(CheckResult.MAX_EVIDENCE_LENGTH + 100);

        CheckResult result = CheckResult.fail(1.0, evidence);

        assertEquals(CheckResult.MAX_EVIDENCE_LENGTH, result.evidence().length());
    }
}
