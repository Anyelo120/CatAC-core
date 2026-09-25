package dev.catac.api;

/**
 * Lets the host keep or deny damage tied to an action CatAC already rejected.
 * It executes on the entity damage event and must not block.
 */
@FunctionalInterface
public interface DamageDecisionProvider {
    DamageDecisionProvider DENY_BY_DEFAULT = context -> DamageDecision.DENY;

    DamageDecision decide(DamageContext context);
}
