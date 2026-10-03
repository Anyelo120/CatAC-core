package dev.catac.api;

import dev.catac.check.CheckResult;

/** Numeric, bounded, opt-in trace. Positions and packet payloads are deliberately omitted. */
public record DetectionTrace(
        long sequence,
        long timeNanos,
        String checkId,
        CheckResult.Outcome outcome,
        double severity,
        double buffer,
        CheckEvidence evidence,
        ViolationAction decision) {}
