package dev.catac.check.builtin;

import dev.catac.api.*;
import dev.catac.check.*;
import dev.catac.config.CheckPolicy;
import dev.catac.internal.MovementExemptions;
import dev.catac.state.*;

/** Experimental lower response bound for acknowledged impulses in clear air. */
public final class KnockbackCheck implements MovementCheck {
    private static final CheckDescriptor D =
            new CheckDescriptor(
                    "movement.knockback",
                    "Impulse response",
                    CheckCategory.MOVEMENT,
                    new CheckPolicy(true, 6, 12, 36, .1, 1500),
                    false,
                    CheckCapabilities.OBSERVE);

    public CheckDescriptor descriptor() {
        return D;
    }

    public CheckResult evaluate(MovementFrame f, PlayerData d) {
        if (!d.impulse().canEvaluate(f.nowNanos())) return CheckResult.skip();
        if (MovementExemptions.physicalBypass(f.player(), f.collision())
                || f.collision().supported()
                || f.collision().insideSolid()
                || f.collision().sweptIntoSolid()) return CheckResult.skip();
        var expected = d.impulse().allowance(f.nowNanos());
        double horizontal = Math.hypot(expected.x(), expected.z());
        if (horizontal < .20) return CheckResult.skip();
        double projected = (f.deltaX() * expected.x() + f.deltaZ() * expected.z()) / horizontal;
        double minimum = Math.max(0, horizontal * .55 - .12);
        return projected < minimum
                ? CheckResult.fail(
                        .75,
                        new CheckEvidence(projected, minimum, .12, "impulse-response-experimental"))
                : CheckResult.pass();
    }
}
