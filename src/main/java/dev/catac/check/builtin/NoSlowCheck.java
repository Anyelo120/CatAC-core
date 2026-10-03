package dev.catac.check.builtin;

import dev.catac.api.*;
import dev.catac.check.*;
import dev.catac.config.CheckPolicy;
import dev.catac.internal.MovementExemptions;
import dev.catac.state.*;

/**
 * Experimental: host item-use state is authoritative, but client use cancellation needs
 * calibration.
 */
public final class NoSlowCheck implements MovementCheck {
    private static final CheckDescriptor D =
            new CheckDescriptor(
                    "movement.no-slow",
                    "Item-use slowdown",
                    CheckCategory.MOVEMENT,
                    new CheckPolicy(true, 6, 12, 36, .2, 1200),
                    false,
                    CheckCapabilities.OBSERVE);

    public CheckDescriptor descriptor() {
        return D;
    }

    public CheckResult evaluate(MovementFrame f, PlayerData d) {
        if (!f.player().isUsingItem()) return CheckResult.skip();
        if (!f.collision().supported()
                || MovementExemptions.physicalBypass(f.player(), f.collision())
                || d.impulse().active(f.nowNanos())) return CheckResult.skip();
        double limit = d.prediction().horizontalLimit() * .45 + .035;
        return f.horizontalDistance() > limit
                ? CheckResult.fail(
                        .5,
                        new CheckEvidence(
                                f.horizontalDistance(), limit, .035, "item-use-experimental"))
                : CheckResult.pass();
    }
}
