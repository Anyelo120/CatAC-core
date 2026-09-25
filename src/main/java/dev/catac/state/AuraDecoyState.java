package dev.catac.state;

import java.util.UUID;

/** Allocation-free lifetime and confirmation state for one private decoy. */
public final class AuraDecoyState {
    private int entityId = -1;
    private long armAtNanos;
    private long expireAtNanos;
    private long nextSpawnAtNanos;
    private double maximumFacingDot;
    private double x;
    private double z;
    private UUID uuid;

    public boolean canSpawn(long nowNanos) {
        return entityId == -1 && nowNanos >= nextSpawnAtNanos;
    }

    public void spawn(int entityId, UUID uuid, double x, double z, long nowNanos, long armingDelayNanos,
                      long lifetimeNanos, long cooldownNanos, double maximumFacingDot) {
        this.entityId = entityId;
        this.uuid = uuid;
        this.x = x;
        this.z = z;
        this.armAtNanos = saturatedAdd(nowNanos, armingDelayNanos);
        this.expireAtNanos = saturatedAdd(nowNanos, lifetimeNanos);
        this.nextSpawnAtNanos = saturatedAdd(nowNanos, cooldownNanos);
        this.maximumFacingDot = maximumFacingDot;
    }

    public boolean confirmsAttack(int targetEntityId, long nowNanos, double facingDot) {
        return entityId == targetEntityId && nowNanos >= armAtNanos && nowNanos < expireAtNanos &&
                facingDot <= maximumFacingDot;
    }

    public boolean expired(long nowNanos) { return entityId != -1 && nowNanos >= expireAtNanos; }
    public int entityId() { return entityId; }
    public UUID uuid() { return uuid; }
    public double x() { return x; }
    public double z() { return z; }
    public void clear() { entityId = -1; armAtNanos = 0; expireAtNanos = 0; uuid = null; }

    private static long saturatedAdd(long value, long amount) {
        return amount > 0 && Long.MAX_VALUE - value < amount ? Long.MAX_VALUE : value + Math.max(0, amount);
    }
}
