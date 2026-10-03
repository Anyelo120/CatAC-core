package dev.catac.check;

import dev.catac.api.CheckEvidence;

import java.util.Objects;

public final class CheckResult {
    public enum Outcome {
        NOT_APPLICABLE,
        UNCERTAIN,
        PASS,
        FAIL,
        MALFORMED
    }

    public static final int MAX_EVIDENCE_LENGTH = 512;
    private static final CheckResult PASS =
            new CheckResult(Outcome.PASS, null, 0, "", false, false, null);
    private static final CheckResult SKIP =
            new CheckResult(
                    Outcome.NOT_APPLICABLE, SkipReason.NOT_APPLICABLE, 0, "", false, false, null);
    private final Outcome outcome;
    private final SkipReason skipReason;
    private final double severity;
    private final String evidence;
    private final boolean cancelImmediately;
    private final boolean disconnectRecommended;
    private final CheckEvidence details;

    private CheckResult(
            Outcome outcome,
            SkipReason reason,
            double severity,
            String evidence,
            boolean cancel,
            boolean disconnect,
            CheckEvidence details) {
        this.outcome = outcome;
        this.skipReason = reason;
        this.severity = severity;
        this.evidence = evidence;
        this.cancelImmediately = cancel;
        this.disconnectRecommended = disconnect;
        this.details = details;
    }

    public static CheckResult pass() {
        return PASS;
    }

    public static CheckResult skip() {
        return SKIP;
    }

    public static CheckResult uncertain(SkipReason reason) {
        return new CheckResult(
                Outcome.UNCERTAIN, Objects.requireNonNull(reason), 0, "", false, false, null);
    }

    public static CheckResult uncertainCancel(SkipReason reason) {
        return new CheckResult(
                Outcome.UNCERTAIN, Objects.requireNonNull(reason), 0, "", true, false, null);
    }

    public static CheckResult fail(double severity, String evidence) {
        return failure(severity, evidence, false, false, null);
    }

    public static CheckResult fail(double severity, CheckEvidence evidence) {
        return failure(severity, evidence.toString(), false, false, evidence);
    }

    public static CheckResult cancel(double severity, String evidence) {
        return failure(severity, evidence, true, false, null);
    }

    public static CheckResult cancel(double severity, CheckEvidence evidence) {
        return failure(severity, evidence.toString(), true, false, evidence);
    }

    public static CheckResult reject(double severity, String evidence) {
        CheckResult r = failure(severity, evidence, true, false, null);
        return new CheckResult(Outcome.MALFORMED, null, r.severity, r.evidence, true, false, null);
    }

    public static CheckResult disconnect(double severity, String evidence) {
        return failure(severity, evidence, true, true, null);
    }

    private static CheckResult failure(
            double severity,
            String evidence,
            boolean cancel,
            boolean disconnect,
            CheckEvidence details) {
        if (!Double.isFinite(severity) || severity <= 0)
            throw new IllegalArgumentException("severity must be finite and > 0");
        Objects.requireNonNull(evidence, "evidence");
        String bounded =
                evidence.length() <= MAX_EVIDENCE_LENGTH
                        ? evidence
                        : evidence.substring(0, MAX_EVIDENCE_LENGTH);
        return new CheckResult(
                disconnect ? Outcome.MALFORMED : Outcome.FAIL,
                null,
                severity,
                bounded,
                cancel,
                disconnect,
                details);
    }

    public Outcome outcome() {
        return outcome;
    }

    public SkipReason skipReason() {
        return skipReason;
    }

    public boolean passed() {
        return outcome == Outcome.PASS;
    }

    public boolean evaluated() {
        return passed() || failed();
    }

    public boolean failed() {
        return outcome == Outcome.FAIL || outcome == Outcome.MALFORMED;
    }

    public double severity() {
        return severity;
    }

    public String evidence() {
        return evidence;
    }

    public boolean cancelImmediately() {
        return cancelImmediately;
    }

    public boolean disconnectRecommended() {
        return disconnectRecommended;
    }

    public CheckEvidence details() {
        return details;
    }
}
