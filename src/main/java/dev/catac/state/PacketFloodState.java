package dev.catac.state;

import dev.catac.api.PacketCost;
import dev.catac.config.PacketBudget;

/** Two allocation-free token buckets and a bounded strike window per player. */
public final class PacketFloodState {
    private double totalTokens;
    private double heavyTokens;
    private long lastRefillNanos;
    private long strikeWindowStartedNanos;
    private int strikes;

    public PacketFloodState(PacketBudget total, PacketBudget heavy, long nowNanos) {
        totalTokens = total.burst();
        heavyTokens = heavy.burst();
        lastRefillNanos = nowNanos;
    }

    public boolean tryConsume(PacketCost cost, PacketBudget total, PacketBudget heavy, long nowNanos) {
        refill(total, heavy, nowNanos);
        if (totalTokens < 1.0 || (cost.heavy() && heavyTokens < 1.0)) return false;
        totalTokens -= 1.0;
        if (cost.heavy()) heavyTokens -= 1.0;
        return true;
    }

    public int strike(long nowNanos, long windowNanos) {
        if (strikes == 0 || nowNanos - strikeWindowStartedNanos >= windowNanos) {
            strikeWindowStartedNanos = nowNanos;
            strikes = 1;
        } else {
            strikes++;
        }
        return strikes;
    }

    private void refill(PacketBudget total, PacketBudget heavy, long nowNanos) {
        long elapsed = nowNanos - lastRefillNanos;
        if (elapsed <= 0) return;
        double seconds = elapsed / 1_000_000_000.0;
        totalTokens = Math.min(total.burst(), totalTokens + seconds * total.refillPerSecond());
        heavyTokens = Math.min(heavy.burst(), heavyTokens + seconds * heavy.refillPerSecond());
        lastRefillNanos = nowNanos;
    }
}
