package dev.catac.check.builtin;

import dev.catac.api.CheckCategory;
import dev.catac.api.CheckDescriptor;
import dev.catac.check.CheckResult;
import dev.catac.check.MovementCheck;
import dev.catac.config.CheckPolicy;
import dev.catac.internal.MovementExemptions;
import dev.catac.state.MovementFrame;
import dev.catac.state.PlayerData;

public final class PhaseCheck implements MovementCheck {
    private static final CheckDescriptor DESCRIPTOR = new CheckDescriptor(
            "movement.phase",
            "Solid block phase",
            CheckCategory.MOVEMENT,
            new CheckPolicy(true, 2, 3, 12, 0.15, 1_000),
            true
    );

    @Override
    public CheckDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public CheckResult evaluate(MovementFrame frame, PlayerData data) {
        if (!frame.collision().complete() || MovementExemptions.physicalBypass(frame.player(), frame.collision())) {
            return CheckResult.pass();
        }
        if ((frame.collision().insideSolid() || frame.collision().sweptIntoSolid()) &&
                (frame.horizontalDistance() > 0.001 || Math.abs(frame.deltaY()) > 0.001)) {
            return CheckResult.fail(1.5, frame.collision().sweptIntoSolid()
                    ? "movement AABB crossed a solid collision shape"
                    : "destination bounding box intersects a solid collision shape");
        }
        return CheckResult.pass();
    }
}
