package dev.catac.api;

import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;

import java.util.Objects;

public record CatViolationEvent(
        Player player,
        CheckDescriptor check,
        double severity,
        double buffer,
        String evidence,
        ViolationAction action
) implements Event {
    public CatViolationEvent {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(check, "check");
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(action, "action");
    }
}
