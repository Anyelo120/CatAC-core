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

public final class GroundSpoofCheck implements MovementCheck {
    private static final CheckDescriptor DESCRIPTOR = new CheckDescriptor(
            "movement.ground-spoof",
            "Ground spoof",
            CheckCategory.MOVEMENT,
            new CheckPolicy(true, 5, 10, 30, 0.25, 1_200),
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
        if (frame.clientOnGround() && !collision.supported() && data.airFrames() > 2 &&
                data.prediction().initialized() && !data.prediction().wasSupported() &&
                Math.abs(frame.deltaY()) > 0.01) {
            return CheckResult.fail(1.0,
                    "client claimed ground without collision support; dy=" + frame.deltaY() +
                            " predicted-air=true");
        }
        return CheckResult.pass();
    }
}
