package dev.catac.config;

/** Token bucket parameters expressed in packets per second. */
public record PacketBudget(double refillPerSecond, double burst) {
    public PacketBudget {
        if (!Double.isFinite(refillPerSecond) || refillPerSecond <= 0) {
            throw new IllegalArgumentException("refillPerSecond must be finite and > 0");
        }
        if (!Double.isFinite(burst) || burst < refillPerSecond) {
            throw new IllegalArgumentException("burst must be finite and >= refillPerSecond");
        }
    }
}
