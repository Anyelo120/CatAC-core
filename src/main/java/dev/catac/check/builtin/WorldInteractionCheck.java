package dev.catac.check.builtin;

import dev.catac.api.*;
import dev.catac.check.*;
import dev.catac.config.CheckPolicy;
import dev.catac.internal.CombatGeometry;
import dev.catac.state.PlayerData;

import net.minestom.server.coordinate.*;
import net.minestom.server.entity.*;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.play.*;

import java.util.Set;

public final class WorldInteractionCheck implements PacketCheck {
    private static final CheckDescriptor D =
            new CheckDescriptor(
                    "world.interaction",
                    "Block interaction",
                    CheckCategory.WORLD,
                    new CheckPolicy(true, 2, 4, 14, .2, 900),
                    false,
                    new CheckCapabilities(true, false, true, true));

    public CheckDescriptor descriptor() {
        return D;
    }

    public Set<Class<? extends ClientPacket>> packetTypes() {
        return Set.of(ClientPlayerBlockPlacementPacket.class, ClientPlayerActionPacket.class);
    }

    public CheckResult evaluate(PlayerPacketEvent e, PlayerData d, long now) {
        Point block;
        Vec hit;
        if (e.getPacket() instanceof ClientPlayerBlockPlacementPacket p) {
            if (p.sequence() < 0
                    || !valid(p.cursorPositionX())
                    || !valid(p.cursorPositionY())
                    || !valid(p.cursorPositionZ()))
                return CheckResult.reject(2, "invalid placement sequence/cursor");
            block = p.blockPosition();
            hit =
                    new Vec(
                            block.x() + p.cursorPositionX(),
                            block.y() + p.cursorPositionY(),
                            block.z() + p.cursorPositionZ());
        } else if (e.getPacket() instanceof ClientPlayerActionPacket p) {
            if (p.status() != ClientPlayerActionPacket.Status.STARTED_DIGGING
                    && p.status() != ClientPlayerActionPacket.Status.CANCELLED_DIGGING
                    && p.status() != ClientPlayerActionPacket.Status.FINISHED_DIGGING)
                return CheckResult.skip();
            if (p.sequence() < 0) return CheckResult.reject(2, "negative digging sequence");
            block = p.blockPosition();
            var pos = e.getPlayer().getPosition();
            double eyeY = pos.y() + e.getPlayer().getEyeHeight();
            hit =
                    new Vec(
                            Math.clamp(pos.x(), block.x(), block.x() + 1),
                            Math.clamp(eyeY, block.y(), block.y() + 1),
                            Math.clamp(pos.z(), block.z(), block.z() + 1));
        } else return CheckResult.skip();
        if (!finite(block)
                || Math.abs(block.x()) > 30_000_000
                || Math.abs(block.y()) > 30_000_000
                || Math.abs(block.z()) > 30_000_000)
            return CheckResult.disconnect(10, "unsafe block coordinates");
        Player p = e.getPlayer();
        var world = p.getInstance();
        if (world == null || !world.isChunkLoaded(block))
            return CheckResult.uncertainCancel(SkipReason.WORLD_UNAVAILABLE);
        if (p.getGameMode() == GameMode.SPECTATOR) return CheckResult.skip();
        if (d.synchronization().movementUncertain(now))
            return CheckResult.uncertainCancel(SkipReason.SYNCHRONIZING);
        Vec eye =
                new Vec(
                        p.getPosition().x(),
                        p.getPosition().y() + p.getEyeHeight(),
                        p.getPosition().z());
        double nearest =
                Math.sqrt(
                        dev.catac.internal.CombatGeometry.distanceSquared(
                                eye.x(),
                                eye.y(),
                                eye.z(),
                                new net.minestom.server.collision.BoundingBox(1, 1, 1, Vec.ZERO),
                                block.x(),
                                block.y(),
                                block.z()));
        double allowed = p.getAttributeValue(Attribute.BLOCK_INTERACTION_RANGE) + .10;
        if (nearest > allowed)
            return CheckResult.cancel(
                    Math.min(8, 1 + (nearest - allowed) * 2),
                    new CheckEvidence(nearest, allowed, .10, "block-interaction-attribute"));
        var los = CombatGeometry.segment(world, eye, hit, block);
        if (los == CombatGeometry.LineOfSight.INCOMPLETE)
            return CheckResult.uncertainCancel(SkipReason.WORLD_UNAVAILABLE);
        return los == CombatGeometry.LineOfSight.BLOCKED
                ? CheckResult.cancel(1.5, "block interaction occluded by native shape")
                : CheckResult.pass();
    }

    private static boolean finite(Point p) {
        return Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z());
    }

    private static boolean valid(float x) {
        return Float.isFinite(x) && x >= -.0001f && x <= 1.0001f;
    }
}
