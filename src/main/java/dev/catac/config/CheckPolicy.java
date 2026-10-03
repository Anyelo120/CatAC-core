package dev.catac.config;

public record CheckPolicy(
        boolean enabled,
        double alertBuffer,
        double setbackBuffer,
        double kickBuffer,
        double decayPerPass,
        long alertCooldownMillis,
        double decayPerSecond) {
    public static final CheckPolicy DISABLED = new CheckPolicy(false, 1, 1, 1, 0, 1_000, 0);

    /** Legacy constructor: explicit time decay is derived at two reference samples per second. */
    public CheckPolicy(
            boolean enabled,
            double alert,
            double setback,
            double kick,
            double legacyDecay,
            long cooldown) {
        this(enabled, alert, setback, kick, legacyDecay, cooldown, legacyDecay * 2);
    }

    public CheckPolicy {
        positive(alertBuffer, "alertBuffer");
        positive(setbackBuffer, "setbackBuffer");
        positive(kickBuffer, "kickBuffer");
        if (setbackBuffer < alertBuffer || kickBuffer < setbackBuffer)
            throw new IllegalArgumentException("Threshold order");
        if (!Double.isFinite(decayPerPass)
                || decayPerPass < 0
                || !Double.isFinite(decayPerSecond)
                || decayPerSecond < 0
                || alertCooldownMillis < 0
                || alertCooldownMillis > 86_400_000)
            throw new IllegalArgumentException("Invalid decay/cooldown");
    }

    public static CheckPolicy standard(double a, double s, double k) {
        return new CheckPolicy(true, a, s, k, .25, 1_000);
    }

    public CheckPolicy disabled() {
        return new CheckPolicy(
                false,
                alertBuffer,
                setbackBuffer,
                kickBuffer,
                decayPerPass,
                alertCooldownMillis,
                decayPerSecond);
    }

    public CheckPolicy withTimeDecay(double rate) {
        return new CheckPolicy(
                enabled,
                alertBuffer,
                setbackBuffer,
                kickBuffer,
                decayPerPass,
                alertCooldownMillis,
                rate);
    }

    private static void positive(double n, String name) {
        if (!Double.isFinite(n) || n <= 0) throw new IllegalArgumentException(name);
    }
}
