package dev.catac.api;

/**
 * Cumulative per-session protocol outcomes; a Pong is transport evidence, not proof of physical
 * response.
 */
public record SynchronizationDiagnostics(
        long teleportTimeouts,
        long velocityTimeouts,
        long overwrittenVelocities,
        long ignoredPongs) {}
