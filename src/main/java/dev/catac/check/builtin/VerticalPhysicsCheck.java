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
import net.minestom.server.entity.Player;
import net.minestom.server.potion.PotionEffect;

public final class VerticalPhysicsCheck implements MovementCheck {
    private static final CheckDescriptor DESCRIPTOR = new CheckDescriptor(
            "movement.vertical",
            "Vertical physics",
            CheckCategory.MOVEMENT,
            new CheckPolicy(true, 4, 8, 24, 0.18, 1_000),
            true
    );

    @Override
    public CheckDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public CheckResult evaluate(MovementFrame frame, PlayerData data) {
        Player player = frame.player();
        CollisionSnapshot collision = frame.collision();
        if (MovementExemptions.physicalBypass(player, collision) || collision.insideSolid() ||
                player.hasEffect(PotionEffect.LEVITATION) || player.hasEffect(PotionEffect.SLOW_FALLING) ||
                player.hasEffect(PotionEffect.JUMP_BOOST)) {
            return CheckResult.pass();
        }
        if (collision.supported() || data.airFrames() < 2 || !data.prediction().initialized()) {
            return CheckResult.pass();
        }

        double previous = data.prediction().previousVerticalVelocity();
        double actual = frame.deltaY();
        double predicted = data.prediction().verticalUpperBound();

        if (actual > predicted) {
            double excess = actual - predicted;
            double severity = Math.min(8.0, 0.75 + excess * 12.0);
            return CheckResult.fail(severity,
                    "vertical=" + round(actual) + " upper=" + round(predicted));
        }

        if (data.airFrames() > 7 && Math.abs(actual) < 0.003 && Math.abs(previous) < 0.02) {
            return CheckResult.fail(1.0, "sustained near-zero vertical motion while airborne");
        }
        return CheckResult.pass();
    }

    private static double round(double value) {
        return Math.rint(value * 10_000.0) / 10_000.0;
    }
}
