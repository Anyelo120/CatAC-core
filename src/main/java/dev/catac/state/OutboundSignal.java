package dev.catac.state;

import net.minestom.server.coordinate.Vec;

/** Immutable output mailbox entry, protocol velocity in blocks/tick. */
public record OutboundSignal(Integer teleportId, Vec velocity, long timeNanos) {}
