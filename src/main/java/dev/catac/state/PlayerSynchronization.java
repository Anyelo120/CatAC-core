package dev.catac.state;

import dev.catac.api.NetworkSnapshot;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.common.PingPacket;

/** Coordinates bounded network probes with teleport and velocity state. */
public final class PlayerSynchronization {
    private final LatencyTracker latency;
    private final TeleportTracker teleports;
    private final VelocityTracker velocities;

    public PlayerSynchronization(long probeIntervalNanos, long acknowledgementTimeoutNanos) {
        this.latency = new LatencyTracker(probeIntervalNanos, acknowledgementTimeoutNanos);
        this.teleports = new TeleportTracker(acknowledgementTimeoutNanos);
        this.velocities = new VelocityTracker(acknowledgementTimeoutNanos);
    }

    public void tick(Player player, long nowNanos) {
        teleports.captureExpectedId(player.getLastSentTeleportId());
        int probeId = latency.createProbe(nowNanos, velocities.hasUnarmed(nowNanos));
        if (probeId == LatencyTracker.NO_PROBE) {
            return;
        }
        try {
            player.sendPacket(new PingPacket(probeId));
            velocities.arm(probeId, nowNanos);
        } catch (RuntimeException exception) {
            latency.discard(probeId);
        }
    }

    public void onPong(int probeId, long nowNanos) {
        if (latency.acknowledge(probeId, nowNanos)) {
            velocities.acknowledge(probeId, nowNanos);
        }
    }

    public void markTeleport(long nowNanos) { teleports.markPending(nowNanos); }
    public void onTeleportConfirm(int teleportId, long nowNanos) { teleports.acknowledge(teleportId, nowNanos); }
    public void markVelocity(long nowNanos) { velocities.markVelocity(nowNanos); }
    public boolean movementUncertain(long nowNanos) {
        return teleports.pending(nowNanos) || velocities.pendingCount(nowNanos) > 0;
    }

    public NetworkSnapshot snapshot(long nowNanos) {
        return new NetworkSnapshot(latency.available(), latency.roundTripMillis(), latency.jitterMillis(),
                teleports.pending(nowNanos), velocities.pendingCount(nowNanos));
    }

    /** Internal hot-path accessor; unlike {@link #snapshot(long)} it allocates nothing. */
    public double latencyAllowanceMillis() {
        return latency.available() ? Math.min(100.0, Math.max(0.0, latency.roundTripMillis())) : 0.0;
    }

    /** Bounded server-side estimate of when the client saw a combat target. */
    public long combatRewindNanos(long paddingNanos, long maximumNanos) {
        double roundTrip = latency.available() ? Math.max(0.0, latency.roundTripMillis()) : 0.0;
        double jitter = latency.jitterMillis() < 0.0 ? 0.0 : latency.jitterMillis();
        long estimated = paddingNanos + (long) ((roundTrip * 0.5 + jitter) * 1_000_000.0);
        return Math.min(maximumNanos, Math.max(paddingNanos, estimated));
    }
}
