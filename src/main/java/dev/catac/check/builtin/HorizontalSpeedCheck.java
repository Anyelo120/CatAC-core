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
    private static final CheckDescriptor DESCRIPTOR = new CheckDescriptor(
            "movement.speed",
            "Horizontal speed",
            CheckCategory.MOVEMENT,
            new CheckPolicy(true, 4, 8, 24, 0.20, 1_000),
            true
    );

    @Override
    public CheckDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public CheckResult evaluate(MovementFrame frame, PlayerData data) {
        CollisionSnapshot collision = frame.collision();
        if (MovementExemptions.physicalBypass(frame.player(), collision) || collision.insideSolid()) {
            return CheckResult.pass();
        }
        double allowed = data.prediction().horizontalLimit();

        double actual = frame.horizontalDistance();
        if (actual <= allowed) {
            return CheckResult.pass();
        }
        double excess = actual - allowed;
        double severity = Math.min(8.0, 0.75 + excess * 10.0);
        return CheckResult.fail(severity,
                "horizontal=" + round(actual) + " allowed=" + round(allowed) +
                        " model=prediction");
    }

    private static double round(double value) {
        return Math.rint(value * 1_000.0) / 1_000.0;
    }
}
