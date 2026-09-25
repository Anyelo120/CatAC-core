package dev.catac.api;

import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.Damage;

import java.util.Objects;

/** Context passed to the host before CatAC cancels protected damage. */
public record DamageContext(
        Player attacker,
        LivingEntity victim,
        Damage damage,
        String reason
) {
    public DamageContext {
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(victim, "victim");
        Objects.requireNonNull(damage, "damage");
        Objects.requireNonNull(reason, "reason");
    }
}
