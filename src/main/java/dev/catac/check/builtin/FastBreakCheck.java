package dev.catac.check.builtin;

import dev.catac.api.*;
import dev.catac.check.*;
import dev.catac.config.CheckPolicy;
import dev.catac.internal.*;
import dev.catac.state.*;

import net.minestom.server.entity.*;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerActionPacket;
import net.minestom.server.utils.block.BlockBreakCalculation;

import java.util.Set;

public final class FastBreakCheck implements PacketCheck {
    private static final CheckDescriptor D =
            new CheckDescriptor(
                    "world.fast-break",
                    "Dig operation timing",
                    CheckCategory.WORLD,
                    new CheckPolicy(true, 3, 6, 18, .2, 1000),
                    false);
    private final TickHealth tickHealth;
    private final CollisionAnalyzer collisions = new CollisionAnalyzer();

    public FastBreakCheck(TickHealth health) {
        tickHealth = health;
    }

    public CheckDescriptor descriptor() {
        return D;
    }

    public Set<Class<? extends ClientPacket>> packetTypes() {
        return Set.of(ClientPlayerActionPacket.class);
    }

    public CheckResult evaluate(PlayerPacketEvent e, PlayerData d, long now) {
        if (!(e.getPacket() instanceof ClientPlayerActionPacket p)) return CheckResult.skip();
        Player player = e.getPlayer();
        DiggingState state = d.digging();
        if (p.status() == ClientPlayerActionPacket.Status.CANCELLED_DIGGING) {
            state.clear();
            return CheckResult.pass();
        }
        if (p.status() != ClientPlayerActionPacket.Status.STARTED_DIGGING
                && p.status() != ClientPlayerActionPacket.Status.FINISHED_DIGGING)
            return CheckResult.skip();
        if (player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) {
            state.clear();
            return CheckResult.skip();
        }
        var world = player.getInstance();
        if (world == null
                || !world.isChunkLoaded(p.blockPosition())
                || !world.isChunkLoaded(player.getPosition())) {
            state.clear();
            return CheckResult.uncertainCancel(SkipReason.WORLD_UNAVAILABLE);
        }
        Block block = world.getBlock(p.blockPosition(), Block.Getter.Condition.TYPE);
        int expected = BlockBreakCalculation.breakTicks(block, player);
        if (expected == BlockBreakCalculation.UNBREAKABLE) {
            state.clear();
            return CheckResult.uncertainCancel(SkipReason.UNMODELED);
        }
        if (expected == 0) {
            state.clear();
            return CheckResult.pass();
        }
        CollisionSnapshot support = new CollisionSnapshot();
        collisions.analyze(player, player.getPosition(), support);
        if (!support.complete()) {
            state.clear();
            return CheckResult.uncertainCancel(SkipReason.WORLD_UNAVAILABLE);
        }
        // Minestom's native break calculation reads the client-derived onGround flag.
        if (!support.supported() && player.isOnGround())
            expected = (int) Math.min(Integer.MAX_VALUE, (long) expected * 5);
        if (p.status() == ClientPlayerActionPacket.Status.STARTED_DIGGING) {
            state.start(
                    p.blockPosition().blockX(),
                    p.blockPosition().blockY(),
                    p.blockPosition().blockZ(),
                    expected,
                    world,
                    block.stateId(),
                    player.getItemInMainHand(),
                    now);
            return CheckResult.pass();
        }
        if (!state.matches(
                p.blockPosition().blockX(),
                p.blockPosition().blockY(),
                p.blockPosition().blockZ())) {
            state.clear();
            return CheckResult.cancel(1, "finish without matching dig start");
        }
        if (!state.contextMatches(world, block.stateId(), player.getItemInMainHand())
                || expected != state.expectedTicks()) {
            state.clear();
            return CheckResult.uncertainCancel(SkipReason.DISCONTINUITY);
        }
        if (tickHealth.isLagCompensating(now)) {
            state.clear();
            return CheckResult.uncertainCancel(SkipReason.SERVER_LAG);
        }
        long elapsed = now - state.startedNanos();
        int required = state.expectedTicks();
        state.clear();
        if (elapsed < 0 || elapsed > 90_000_000_000L)
            return CheckResult.uncertainCancel(SkipReason.DISCONTINUITY);
        double elapsedTicks = elapsed / 50_000_000.0;
        double tolerance = 2 + Math.min(2, d.synchronization().latencyAllowanceMillis() / 50.0);
        if (elapsedTicks + tolerance >= required) return CheckResult.pass();
        return CheckResult.cancel(
                Math.min(8, 1 + (required - elapsedTicks - tolerance) / Math.max(1, required * .2)),
                new CheckEvidence(elapsedTicks, required, tolerance, "bounded-dig-ticks"));
    }
}
