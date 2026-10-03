package dev.catac.state;

import java.util.UUID;

public final class AuraDecoyState {
    private int entityId = -1;
    private final TimeWindow arm = new TimeWindow(),
            lifetime = new TimeWindow(),
            cooldown = new TimeWindow();
    private double facing, x, z;
    private UUID uuid;

    public boolean canSpawn(long now) {
        return entityId == -1 && !cooldown.active(now);
    }

    public void spawn(
            int id,
            UUID uuid,
            double x,
            double z,
            long now,
            long arming,
            long life,
            long cool,
            double facing) {
        if (arming < 0
                || life <= arming
                || life > TimeWindow.MAX_DURATION_NANOS
                || cool < 0
                || cool > TimeWindow.MAX_DURATION_NANOS)
            throw new IllegalArgumentException("Invalid decoy timings");
        this.entityId = id;
        this.uuid = uuid;
        this.x = x;
        this.z = z;
        this.facing = facing;
        arm.open(now, arming);
        lifetime.open(now, life);
        cooldown.open(now, cool);
    }

    public boolean confirmsAttack(int id, long now, double dot) {
        return entityId == id && !arm.active(now) && lifetime.active(now) && dot <= facing;
    }

    public boolean expired(long now) {
        return entityId != -1 && !lifetime.active(now);
    }

    public int entityId() {
        return entityId;
    }

    public UUID uuid() {
        return uuid;
    }

    public double x() {
        return x;
    }

    public double z() {
        return z;
    }

    public void clear() {
        entityId = -1;
        uuid = null;
        arm.clear();
        lifetime.clear();
    }
}
