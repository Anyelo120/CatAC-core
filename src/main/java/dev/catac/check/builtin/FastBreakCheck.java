package dev.catac.check.builtin;

import dev.catac.api.CheckCategory;
import dev.catac.api.CheckDescriptor;
import dev.catac.check.CheckResult;
import dev.catac.check.PacketCheck;
import dev.catac.config.CheckPolicy;
import dev.catac.internal.TickHealth;
import dev.catac.state.DiggingState;
import dev.catac.state.PlayerData;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.client.play.ClientPlayerActionPacket;
import net.minestom.server.utils.block.BlockBreakCalculation;

public final class FastBreakCheck implements PacketCheck {
    private static final long TICK_NANOS = 50_000_000L;
    private static final CheckDescriptor DESCRIPTOR = new CheckDescriptor(
            "world.fast-break",
            "Fast break",
            CheckCategory.WORLD,
            new CheckPolicy(true, 3, 6, 18, 0.2, 1_000),
            false
    );

    private final TickHealth tickHealth;

    public FastBreakCheck(TickHealth tickHealth) {
        this.tickHealth = tickHealth;
    }

    @Override
    public CheckDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public CheckResult evaluate(PlayerPacketEvent event, PlayerData data, long nowNanos) {
        if (!(event.getPacket() instanceof ClientPlayerActionPacket packet)) {
            return CheckResult.pass();
        }
        Player player = event.getPlayer();
        DiggingState digging = data.digging();

        return switch (packet.status()) {
            case STARTED_DIGGING -> {
                start(player, packet, digging, nowNanos);
                yield CheckResult.pass();
            }
            case CANCELLED_DIGGING -> {
                digging.clear();
                yield CheckResult.pass();
            }
            case FINISHED_DIGGING -> finish(player, packet, digging, nowNanos);
            default -> CheckResult.pass();
        };
    }

    private void start(Player player, ClientPlayerActionPacket packet, DiggingState digging, long nowNanos) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            digging.clear();
            return;
        }
        Instance instance = player.getInstance();
        if (instance == null || !instance.isChunkLoaded(packet.blockPosition())) {
            digging.clear();
            return;
        }
        Block block = instance.getBlock(packet.blockPosition(), Block.Getter.Condition.TYPE);
        int expectedTicks = BlockBreakCalculation.breakTicks(block, player);
        if (expectedTicks <= 0 || expectedTicks == BlockBreakCalculation.UNBREAKABLE) {
            digging.clear();
            return;
        }
        digging.start(packet.blockPosition().blockX(), packet.blockPosition().blockY(),
                packet.blockPosition().blockZ(), expectedTicks, nowNanos);
    }

    private CheckResult finish(Player player, ClientPlayerActionPacket packet,
                               DiggingState digging, long nowNanos) {
        if (!digging.matches(packet.blockPosition().blockX(), packet.blockPosition().blockY(),
                packet.blockPosition().blockZ())) {
            digging.clear();
            return CheckResult.cancel(1.0, "finish-digging without a matching start");
        }
        if (tickHealth.isLagCompensating(nowNanos)) {
            digging.clear();
            return CheckResult.pass();
        }

        int expected = digging.expectedTicks();
        long elapsedNanos = Math.max(0L, nowNanos - digging.startedNanos());
        int elapsedTicks = (int) (elapsedNanos / TICK_NANOS);
        int latencyTicks = Math.max(0, (int) Math.ceil(player.getLatency() / 50.0));
        int tolerance = 2 + latencyTicks;
        digging.clear();

        if (elapsedTicks + tolerance >= expected) {
            return CheckResult.pass();
        }
        int missingTicks = expected - elapsedTicks - tolerance;
        double severity = Math.min(8.0, 1.0 + missingTicks / Math.max(1.0, expected * 0.2));
        return CheckResult.cancel(severity,
                "elapsed=" + elapsedTicks + "t expected=" + expected + "t tolerance=" + tolerance + "t");
    }
}
