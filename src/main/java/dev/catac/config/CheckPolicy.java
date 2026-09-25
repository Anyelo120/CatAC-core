package dev.catac.config;

public record CheckPolicy(
        boolean enabled,
        double alertBuffer,
        double setbackBuffer,
        double kickBuffer,
        double decayPerPass,
        long alertCooldownMillis
) {
    public static final CheckPolicy DISABLED = new CheckPolicy(false, 1, 1, 1, 0, 1_000);

    public CheckPolicy {
        requireFinitePositive(alertBuffer, "alertBuffer");
        requireFinitePositive(setbackBuffer, "setbackBuffer");
        requireFinitePositive(kickBuffer, "kickBuffer");
        if (setbackBuffer < alertBuffer) {
            throw new IllegalArgumentException("setbackBuffer must be >= alertBuffer");
        }
        if (kickBuffer < setbackBuffer) {
            throw new IllegalArgumentException("kickBuffer must be >= setbackBuffer");
        }
        if (!Double.isFinite(decayPerPass) || decayPerPass < 0) {
            throw new IllegalArgumentException("decayPerPass must be finite and >= 0");
        }
        if (alertCooldownMillis < 0) {
            throw new IllegalArgumentException("alertCooldownMillis must be >= 0");
        }
    }

    public static CheckPolicy standard(double alertBuffer, double setbackBuffer, double kickBuffer) {
        return new CheckPolicy(true, alertBuffer, setbackBuffer, kickBuffer, 0.25, 1_000);
    }

    public CheckPolicy disabled() {
        return new CheckPolicy(false, alertBuffer, setbackBuffer, kickBuffer, decayPerPass, alertCooldownMillis);
    }

    private static void requireFinitePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(name + " must be finite and > 0");
        }
    }
}
