package dev.catac.check.builtin;

import dev.catac.api.*;
import dev.catac.check.*;
import dev.catac.config.CheckPolicy;
import dev.catac.state.*;

import net.minestom.server.entity.GameMode;
import net.minestom.server.potion.PotionEffect;

/** Experimental coarse envelopes for media whose full client physics is not reconstructed yet. */
public final class MediumMovementCheck implements MovementCheck {
    private static final CheckDescriptor D =
            new CheckDescriptor(
                    "movement.medium",
                    "Liquid, climb and slow medium",
                    CheckCategory.MOVEMENT,
                    new CheckPolicy(true, 6, 12, 36, .1, 1500),
                    false,
                    CheckCapabilities.OBSERVE);

    public CheckDescriptor descriptor() {
        return D;
    }

    public CheckResult evaluate(MovementFrame f, PlayerData d) {
        var c = f.collision();
        var p = f.player();
        if (!c.complete()
                || p.getVehicle() != null
                || p.isFlying()
                || p.isFlyingWithElytra()
                || p.isAllowFlying()
                || p.getGameMode() == GameMode.SPECTATOR
                || d.impulse().active(f.nowNanos())
                || p.getLivingEntityMeta().isInRiptideSpinAttack()) return CheckResult.skip();
        double horizontal, up;
        String model;
        if (c.touchingLiquid()) {
            if (p.hasEffect(PotionEffect.DOLPHINS_GRACE)) return CheckResult.skip();
            horizontal = .8;
            up = .65;
            model = "liquid-experimental";
        } else if (c.touchingClimbable()) {
            horizontal = .45;
            up = .35;
            model = "climb-experimental";
        } else if (c.touchingSlowBlock()) {
            horizontal = .5;
            up = .65;
            model = "slow-medium-experimental";
        } else return CheckResult.skip();
        if (f.horizontalDistance() > horizontal)
            return CheckResult.fail(
                    .5, new CheckEvidence(f.horizontalDistance(), horizontal, .1, model));
        return f.deltaY() > up
                ? CheckResult.fail(.5, new CheckEvidence(f.deltaY(), up, .1, model))
                : CheckResult.pass();
    }
}
