package dev.catac.api;

/** Receives bounded flood notifications; it must never block the event thread. */
@FunctionalInterface
public interface PacketFloodHandler {
    PacketFloodHandler NOOP = event -> { };

    void onFlood(PacketFloodEvent event);
}
