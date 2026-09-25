package dev.catac.api;

import net.kyori.adventure.text.Component;

/**
 * Produces player-facing feedback. It runs on Minestom's event flow, so it
 * must be allocation-conscious and never perform blocking work.
 */
@FunctionalInterface
public interface PlayerMessageProvider {
    PlayerMessageProvider DEFAULT = notice -> switch (notice.type()) {
        case WARNING -> Component.text("CatAC detectó una acción irregular. Corrígela para evitar una sanción.");
        case SETBACK -> Component.text("Tu movimiento fue corregido por seguridad.");
    };

    /** Return {@code null} to suppress this individual message. */
    Component message(PlayerNotice notice);
}
