package dev.catac.check;

import java.util.Objects;

public final class CheckResult {
    public static final int MAX_EVIDENCE_LENGTH = 512;
    private static final CheckResult PASS = new CheckResult(true, 0.0, "", false, false);

    private final boolean passed;
    private final double severity;
    private final String evidence;
    private final boolean cancelImmediately;
    private final boolean disconnectRecommended;

    private CheckResult(boolean passed, double severity, String evidence,
                        boolean cancelImmediately, boolean disconnectRecommended) {
        this.passed = passed;
        this.severity = severity;
        this.evidence = evidence;
        this.cancelImmediately = cancelImmediately;
        this.disconnectRecommended = disconnectRecommended;
    }

    public static CheckResult pass() {
        return PASS;
    }

    public static CheckResult fail(double severity, String evidence) {
        return fail(severity, evidence, false, false);
    }

    public static CheckResult cancel(double severity, String evidence) {
        return fail(severity, evidence, true, false);
    }

    public static CheckResult disconnect(double severity, String evidence) {
        return fail(severity, evidence, true, true);
    }

    private static CheckResult fail(double severity, String evidence,
                                    boolean cancelImmediately, boolean disconnectRecommended) {
        if (!Double.isFinite(severity) || severity <= 0) {
            throw new IllegalArgumentException("severity must be finite and > 0");
        }
        return new CheckResult(false, severity, boundedEvidence(evidence),
                cancelImmediately, disconnectRecommended);
    }

    private static String boundedEvidence(String evidence) {
        Objects.requireNonNull(evidence, "evidence");
        return evidence.length() <= MAX_EVIDENCE_LENGTH
                ? evidence
                : evidence.substring(0, MAX_EVIDENCE_LENGTH);
    }

    public boolean passed() { return passed; }
    public double severity() { return severity; }
    public String evidence() { return evidence; }
    public boolean cancelImmediately() { return cancelImmediately; }
    public boolean disconnectRecommended() { return disconnectRecommended; }
}
