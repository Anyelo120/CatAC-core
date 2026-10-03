package dev.catac.check.builtin;

import dev.catac.api.CheckCategory;
import dev.catac.api.CheckDescriptor;
import dev.catac.check.CheckResult;
import dev.catac.check.MovementCheck;
import dev.catac.config.CheckPolicy;
import dev.catac.internal.MovementExemptions;
import dev.catac.state.CollisionSnapshot;
import dev.catac.state.MovementFrame;
import dev.catac.state.PlayerData;

public final class HorizontalSpeedCheck implements MovementCheck {
    private static final CheckDescriptor DESCRIPTOR =
            new CheckDescriptor(
                    "movement.speed",
                    "Horizontal speed",
                    CheckCategory.MOVEMENT,
                    new CheckPolicy(true, 4, 8, 24, 0.20, 1_000),
                    true);

    @Override
    public CheckDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public CheckResult evaluate(MovementFrame frame, PlayerData data) {
        CollisionSnapshot collision = frame.collision();
        if (MovementExemptions.physicalBypass(frame.player(), collision)
                || collision.insideSolid()) {
            return CheckResult.uncertain(dev.catac.check.SkipReason.UNMODELED);
        }
        double allowed = data.prediction().horizontalLimit();

        double actual = frame.horizontalDistance();
        if (data.prediction().initialized()
                && collision.supported() == data.prediction().wasSupported()
                && !collision.sweptIntoSolid()
                && !data.impulse().active(frame.nowNanos())
                && actual > .08
                && data.prediction().vectorResidual(frame) > data.prediction().residualLimit()) {
            return CheckResult.fail(
                    .75,
                    new dev.catac.api.CheckEvidence(
                            data.prediction().vectorResidual(frame),
                            data.prediction().residualLimit(),
                            .12,
                            "vector-direction-envelope"));
        }
        if (actual <= allowed) {
            return CheckResult.pass();
        }
        double excess = actual - allowed;
        double severity = Math.min(8.0, 0.75 + excess * 10.0);
        return CheckResult.fail(
                severity,
                new dev.catac.api.CheckEvidence(actual, allowed, .035, "trusted-vector-envelope"));
    }

    private static double round(double value) {
        return Math.rint(value * 1_000.0) / 1_000.0;
    }
}
