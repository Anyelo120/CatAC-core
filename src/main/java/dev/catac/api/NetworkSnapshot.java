package dev.catac.api;

/**
 * Immutable, allocation-on-read view of CatAC's network synchronization state
 * for one player.
 *
 * @param latencyAvailable whether at least one CatAC probe has been acknowledged
 * @param roundTripMillis estimated round-trip time, or {@code -1} when unavailable
 * @param jitterMillis rolling RTT variation, or {@code -1} when unavailable
 * @param teleportPending whether CatAC is waiting for a teleport confirmation
 * @param pendingVelocities number of velocity updates not yet acknowledged
 */
public record NetworkSnapshot(
        boolean latencyAvailable,
        double roundTripMillis,
        double jitterMillis,
        boolean teleportPending,
        int pendingVelocities
) {
}
