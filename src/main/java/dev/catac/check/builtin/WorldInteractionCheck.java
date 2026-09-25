package dev.catac.check.builtin;

import dev.catac.api.CheckCategory;
import dev.catac.api.CheckDescriptor;
import dev.catac.check.CheckResult;
import dev.catac.check.PacketCheck;
import dev.catac.config.CheckPolicy;
import dev.catac.state.PlayerData;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.network.packet.client.play.ClientPlayerActionPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerBlockPlacementPacket;

/** Validates block interaction coordinates before listener-side world mutation. */
public final class WorldInteractionCheck implements PacketCheck {
    private static final CheckDescriptor DESCRIPTOR = new CheckDescriptor(
            "world.interaction", "Block interaction", CheckCategory.WORLD,
            new CheckPolicy(true, 2, 4, 14, 0.20, 900), false);

    @Override
    public CheckDescriptor descriptor() { return DESCRIPTOR; }

    @Override
    public CheckResult evaluate(PlayerPacketEvent event, PlayerData data, long nowNanos) {
        if (event.getPacket() instanceof ClientPlayerBlockPlacementPacket packet) {
            if (!validCursor(packet.cursorPositionX()) || !validCursor(packet.cursorPositionY()) ||
                    !validCursor(packet.cursorPositionZ())) {
                return CheckResult.cancel(2.0, "block placement has invalid cursor coordinates");
            }
            return validateReach(event.getPlayer(), packet.blockPosition(), "placement");
        }
        if (event.getPacket() instanceof ClientPlayerActionPacket packet && isDigging(packet)) {
            return validateReach(event.getPlayer(), packet.blockPosition(), "digging");
        }
        return CheckResult.pass();
    }

    private static boolean isDigging(ClientPlayerActionPacket packet) {
        return packet.status() == ClientPlayerActionPacket.Status.STARTED_DIGGING ||
                packet.status() == ClientPlayerActionPacket.Status.CANCELLED_DIGGING ||
                packet.status() == ClientPlayerActionPacket.Status.FINISHED_DIGGING;
    }

    private static CheckResult validateReach(Player player, Point block, String interaction) {
        if (!Double.isFinite(block.x()) || !Double.isFinite(block.y()) || !Double.isFinite(block.z())) {
            return CheckResult.disconnect(10.0, "non-finite block interaction position");
        }
        Instance instance = player.getInstance();
        if (instance == null || !instance.isChunkLoaded(block)) {
            return CheckResult.pass();
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return CheckResult.pass();
        }
        double eyeX = player.getPosition().x();
        double eyeY = player.getPosition().y() + player.getEyeHeight();
        double eyeZ = player.getPosition().z();
        double dx = eyeX - Math.clamp(eyeX, block.x(), block.x() + 1.0);
        double dy = eyeY - Math.clamp(eyeY, block.y(), block.y() + 1.0);
        double dz = eyeZ - Math.clamp(eyeZ, block.z(), block.z() + 1.0);
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double allowed = player.getGameMode() == GameMode.CREATIVE ? 7.0 : 6.0;
        if (distance <= allowed) {
            return CheckResult.pass();
        }
        return CheckResult.fail(Math.min(8.0, 1.0 + (distance - allowed) * 2.0),
                interaction + " reach=" + round(distance) + " allowed=" + allowed);
    }

    private static boolean validCursor(float value) {
        return Float.isFinite(value) && value >= -0.0001f && value <= 1.0001f;
    }

    private static double round(double value) { return Math.rint(value * 1_000.0) / 1_000.0; }
}
