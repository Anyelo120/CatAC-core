package dev.catac.check.builtin;

import dev.catac.api.CheckCategory;
import dev.catac.api.CheckDescriptor;
import dev.catac.check.CheckResult;
import dev.catac.check.PacketCheck;
import dev.catac.config.CheckPolicy;
import dev.catac.config.CatACConfig;
import dev.catac.internal.CombatGeometry;
import dev.catac.internal.EntityHistoryManager;
import dev.catac.state.PlayerData;
import dev.catac.state.PositionHistory;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.client.play.ClientInteractEntityPacket;

public final class ReachCheck implements PacketCheck {
    private static final CheckDescriptor DESCRIPTOR = new CheckDescriptor(
            "combat.reach",
            "Combat reach",
            CheckCategory.COMBAT,
            new CheckPolicy(true, 2, 5, 16, 0.18, 800),
            false
    );

    private final EntityHistoryManager entityHistories;
    private final CatACConfig config;

    public ReachCheck(EntityHistoryManager entityHistories, CatACConfig config) {
        this.entityHistories = entityHistories;
        this.config = config;
    }

    @Override
    public CheckDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public CheckResult evaluate(PlayerPacketEvent event, PlayerData data, long nowNanos) {
        if (!(event.getPacket() instanceof ClientInteractEntityPacket packet) ||
                !(packet.type() instanceof ClientInteractEntityPacket.Attack)) {
            return CheckResult.pass();
        }

        Player attacker = event.getPlayer();
        Instance instance = attacker.getInstance();
        if (instance == null) {
            return CheckResult.pass();
        }
        Entity target = instance.getEntityById(packet.targetId());
        if (target == null || target == attacker || !target.isViewer(attacker)) {
            return CheckResult.pass();
        }

        Pos attackerPosition = attacker.getPosition();
        double eyeX = attackerPosition.x();
        double eyeY = attackerPosition.y() + attacker.getEyeHeight();
        double eyeZ = attackerPosition.z();
        BoundingBox targetBox = target.getBoundingBox();

        long rewindNanos = data.synchronization().combatRewindNanos(
                config.combatRewindPaddingNanos(), config.combatMaxRewindNanos());
        PositionHistory history = entityHistories.find(target);
        PositionHistory.RewoundPosition targetPosition = history == null
                ? new PositionHistory.RewoundPosition(target.getPosition().x(), target.getPosition().y(),
                target.getPosition().z(), false)
                : history.rewind(nowNanos - rewindNanos, target.getPosition());

        double distanceSquared = CombatGeometry.distanceSquared(
                eyeX, eyeY, eyeZ, targetBox, targetPosition.x(), targetPosition.y(), targetPosition.z());

        double distance = Math.sqrt(distanceSquared);
        double allowed = attacker.getAttributeValue(Attribute.ENTITY_INTERACTION_RANGE) + 0.10;
        if (distance <= allowed) {
            double directionDot = CombatGeometry.directionDot(attackerPosition, eyeX, eyeY, eyeZ,
                    targetBox, targetPosition.x(), targetPosition.y(), targetPosition.z());
            if (directionDot <= 0.0) {
                // View direction can be stale for one client tick; reach is
                // still valid, so do not turn an angle heuristic into a ban.
                return CheckResult.pass();
            }
            CombatGeometry.LineOfSight lineOfSight = CombatGeometry.lineOfSight(instance, eyeX, eyeY, eyeZ,
                    targetBox, targetPosition.x(), targetPosition.y(), targetPosition.z());
            if (lineOfSight == CombatGeometry.LineOfSight.BLOCKED) {
                return CheckResult.fail(1.5, "attack ray intersected a solid collision shape");
            }
            return CheckResult.pass();
        }

        double excess = distance - allowed;
        double severity = Math.min(8.0, 1.0 + excess * 6.0);
        return CheckResult.cancel(severity,
                "reach=" + round(distance) + " allowed=" + round(allowed) +
                        " rewindMs=" + rewindNanos / 1_000_000L +
                        " historical=" + targetPosition.historical());
    }

    private static double round(double value) {
        return Math.rint(value * 1_000.0) / 1_000.0;
    }
}
