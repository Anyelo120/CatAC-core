package dev.catac.api;

import net.minestom.server.entity.Player;

import java.util.Objects;

/** Immutable context supplied when CatAC gives quiet feedback to a player. */
public record PlayerNotice(
        Player player,
        CheckDescriptor check,
        PlayerNoticeType type,
        double severity,
        double buffer,
        int warningNumber
) {
    public PlayerNotice {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(check, "check");
        Objects.requireNonNull(type, "type");
        if (!Double.isFinite(severity) || severity <= 0) {
            throw new IllegalArgumentException("severity must be finite and > 0");
        }
        if (!Double.isFinite(buffer) || buffer < 0) {
            throw new IllegalArgumentException("buffer must be finite and >= 0");
        }
        if (warningNumber < 1) {
            throw new IllegalArgumentException("warningNumber must be >= 1");
        }
    }
}
