package dev.catac.check.builtin;

import dev.catac.api.CheckCategory;
import dev.catac.api.CheckDescriptor;
import dev.catac.check.CheckResult;
import dev.catac.check.PacketCheck;
import dev.catac.config.CheckPolicy;
import dev.catac.internal.TickHealth;
import dev.catac.state.PlayerData;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionAndRotationPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionStatusPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerRotationPacket;

public final class MovementPacketRateCheck implements PacketCheck {
    private static final double TICK_NANOS = 50_000_000.0;
    private static final double BURST_ALLOWANCE = 10.0;
    private static final CheckDescriptor DESCRIPTOR = new CheckDescriptor(
            "packet.timer",
            "Movement timer",
            CheckCategory.PACKET,
            new CheckPolicy(true, 6, 12, 30, 0.15, 1_500),
            false
    );

    private final TickHealth tickHealth;

    public MovementPacketRateCheck(TickHealth tickHealth) {
        this.tickHealth = tickHealth;
    }

    @Override
    public CheckDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public CheckResult evaluate(PlayerPacketEvent event, PlayerData data, long nowNanos) {
        if (!isMovementPacket(event.getPacket())) {
            return CheckResult.pass();
        }
        long previousNanos = data.lastMovementPacketNanos();
        data.lastMovementPacketNanos(nowNanos);
        if (previousNanos == 0 || tickHealth.isLagCompensating(nowNanos)) {
            data.movementPacketBalance(0.0);
            return CheckResult.pass();
        }

        double elapsedTicks = Math.max(0.0, (nowNanos - previousNanos) / TICK_NANOS);
        double balance = Math.max(0.0, data.movementPacketBalance() - elapsedTicks) + 1.0;
        data.movementPacketBalance(balance);
        if (balance <= BURST_ALLOWANCE) {
            return CheckResult.pass();
        }

        double excess = balance - BURST_ALLOWANCE;
        double severity = Math.min(8.0, 0.5 + excess / 3.0);
        String evidence = "movement packet balance=" + round(balance);
        return balance >= 30.0
                ? CheckResult.cancel(severity, evidence)
                : CheckResult.fail(severity, evidence);
    }

    private static boolean isMovementPacket(ClientPacket packet) {
        return packet instanceof ClientPlayerPositionPacket ||
                packet instanceof ClientPlayerPositionAndRotationPacket ||
                packet instanceof ClientPlayerRotationPacket ||
                packet instanceof ClientPlayerPositionStatusPacket;
    }

    private static double round(double value) {
        return Math.rint(value * 100.0) / 100.0;
    }
}
