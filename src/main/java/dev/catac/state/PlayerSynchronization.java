package dev.catac.state;

import dev.catac.api.NetworkSnapshot;

import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.common.PingPacket;

/** Coordinates bounded network probes with teleport and velocity state. */
public final class PlayerSynchronization {
    private boolean nativeTeleportPending;
    private long ignoredPongs;
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
        } else ignoredPongs++;
    }

    public void markTeleport(long nowNanos) {
        teleports.markPending(nowNanos);
    }

    public void markTeleport(long nowNanos, int previousId) {
        teleports.markPending(nowNanos, previousId);
    }

    public void observeNativeTeleport(int sent, int received) {
        nativeTeleportPending = sent != received;
        teleports.captureExpectedId(sent);
    }

    public void captureTeleportId(int id) {
        teleports.captureExpectedId(id);
    }

    public void sentTeleport(int id, long now) {
        teleports.sent(id, now);
    }

    public void markVelocity(net.minestom.server.coordinate.Vec perTick, long now) {
        velocities.markVelocity(perTick, now);
    }

    public net.minestom.server.coordinate.Vec consumeAcknowledgedVelocity() {
        return velocities.consumeAcknowledged();
    }

    public void onTeleportConfirm(int teleportId, long nowNanos) {
        teleports.acknowledge(teleportId, nowNanos);
    }

    public void markVelocity(long nowNanos) {
        velocities.markVelocity(nowNanos);
    }

    public boolean movementUncertain(long nowNanos) {
        return nativeTeleportPending
                || teleports.pending(nowNanos)
                || velocities.pendingCount(nowNanos) > 0;
    }

    public dev.catac.api.SynchronizationDiagnostics diagnostics(long now) {
        teleports.pending(now);
        velocities.pendingCount(now);
        return new dev.catac.api.SynchronizationDiagnostics(
                teleports.timeouts(),
                velocities.timeouts(),
                velocities.overwritten(),
                ignoredPongs);
    }

    public NetworkSnapshot snapshot(long nowNanos) {
        return new NetworkSnapshot(
                latency.available(),
                latency.roundTripMillis(),
                latency.jitterMillis(),
                nativeTeleportPending || teleports.pending(nowNanos),
                velocities.pendingCount(nowNanos));
    }

    /** Internal hot-path accessor; unlike {@link #snapshot(long)} it allocates nothing. */
    public double latencyAllowanceMillis() {
        return latency.available()
                ? Math.min(100.0, Math.max(0.0, latency.roundTripMillis()))
                : 0.0;
    }

    /** Bounded server-side estimate of when the client saw a combat target. */
    public long combatRewindNanos(long paddingNanos, long maximumNanos) {
        double roundTrip = latency.available() ? Math.max(0.0, latency.roundTripMillis()) : 0.0;
        double jitter = latency.jitterMillis() < 0.0 ? 0.0 : latency.jitterMillis();
        long estimated = paddingNanos + (long) ((roundTrip * 0.5 + jitter) * 1_000_000.0);
        return Math.min(maximumNanos, Math.max(paddingNanos, estimated));
    }
}
