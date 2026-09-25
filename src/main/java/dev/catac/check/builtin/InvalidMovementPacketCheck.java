package dev.catac.check.builtin;

import dev.catac.api.CheckCategory;
import dev.catac.api.CheckDescriptor;
import dev.catac.check.CheckResult;
import dev.catac.check.PacketCheck;
import dev.catac.config.CheckPolicy;
import dev.catac.state.PlayerData;
import net.minestom.server.coordinate.Point;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionAndRotationPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerRotationPacket;

public final class InvalidMovementPacketCheck implements PacketCheck {
    private static final double MAX_COORDINATE = 30_000_000.0;
    private static final CheckDescriptor DESCRIPTOR = new CheckDescriptor(
            "packet.invalid-movement",
            "Invalid movement packet",
            CheckCategory.PACKET,
            new CheckPolicy(true, 1, 1, 3, 0, 1_000),
            false
    );

    @Override
    public CheckDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public CheckResult evaluate(PlayerPacketEvent event, PlayerData data, long nowNanos) {
        ClientPacket packet = event.getPacket();
        if (packet instanceof ClientPlayerPositionPacket positionPacket) {
            return validatePosition(positionPacket.position());
        }
        if (packet instanceof ClientPlayerPositionAndRotationPacket positionRotationPacket) {
            CheckResult position = validatePosition(positionRotationPacket.position());
            if (!position.passed()) {
                return position;
            }
            return validateRotation(positionRotationPacket.position().yaw(), positionRotationPacket.position().pitch());
        }
        if (packet instanceof ClientPlayerRotationPacket rotationPacket) {
            return validateRotation(rotationPacket.yaw(), rotationPacket.pitch());
        }
        return CheckResult.pass();
    }

    private static CheckResult validatePosition(Point point) {
        if (!Double.isFinite(point.x()) || !Double.isFinite(point.y()) || !Double.isFinite(point.z())) {
            return CheckResult.disconnect(10, "non-finite position");
        }
        if (Math.abs(point.x()) > MAX_COORDINATE || Math.abs(point.y()) > MAX_COORDINATE ||
                Math.abs(point.z()) > MAX_COORDINATE) {
            return CheckResult.disconnect(10, "position outside protocol-safe world bounds");
        }
        return CheckResult.pass();
    }

    private static CheckResult validateRotation(float yaw, float pitch) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            return CheckResult.disconnect(10, "non-finite rotation");
        }
        if (Math.abs(pitch) > 90.0001f) {
            return CheckResult.cancel(2, "pitch outside [-90, 90]: " + pitch);
        }
        return CheckResult.pass();
    }
}
