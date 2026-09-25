package dev.catac.api;

/** Immutable cumulative telemetry snapshot for the current CatAC instance. */
public record CatACMetrics(
        long violationSamples,
        long alerts,
        long cancelledPackets,
        long setbacks,
        long kicks,
        long floodDrops,
        long floodKicks,
        int trackedPlayers
) {
}
