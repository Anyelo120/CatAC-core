package dev.catac.check.builtin;

import dev.catac.api.*;
import dev.catac.check.*;
import dev.catac.config.CheckPolicy;
import dev.catac.internal.MovementExemptions;
import dev.catac.state.*;

/**
 * Downward gravity residual remains observation-only until packet omission and falls are
 * calibrated.
 */
public final class VerticalDescentCheck implements MovementCheck {
    private static final CheckDescriptor D =
            new CheckDescriptor(
                    "movement.descent",
                    "Downward gravity residual",
                    CheckCategory.MOVEMENT,
                    new CheckPolicy(true, 6, 12, 36, .1, 1500),
                    false,
                    CheckCapabilities.OBSERVE);

    public CheckDescriptor descriptor() {
        return D;
    }

    public CheckResult evaluate(MovementFrame f, PlayerData d) {
        if (!d.prediction().initialized()
                || d.prediction().wasSupported()
                || f.collision().supported()
                || MovementExemptions.physicalBypass(f.player(), f.collision())
                || d.impulse().active(f.nowNanos())) return CheckResult.skip();
        double lower = d.prediction().verticalLowerBound();
        return f.deltaY() < lower
                ? CheckResult.fail(
                        .5, new CheckEvidence(f.deltaY(), lower, .12, "descent-experimental"))
                : CheckResult.pass();
    }
}
