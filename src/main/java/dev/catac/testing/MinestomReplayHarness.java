package dev.catac.testing;

import dev.catac.CatAC;
import dev.catac.api.*;
import dev.catac.config.CatACConfig;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.entity.EntityTickEvent;
import net.minestom.server.network.packet.client.ClientPacket;

import java.util.List;

/** Runs CatAC AND the pinned native packet listeners in an isolated, unstarted Minestom process. */
public final class MinestomReplayHarness implements AutoCloseable {
    private final CatAC anticheat;
    private final DeterministicClock clock;
    private final Player player;

    public MinestomReplayHarness(Player player, DeterministicClock clock, CatACConfig config) {
        if (MinecraftServer.isStarted())
            throw new IllegalStateException("Replay is for an isolated unstarted process");
        if (config.clock() != clock)
            throw new IllegalArgumentException("Config must use the same replay clock");
        if (player.getInstance() == null)
            throw new IllegalArgumentException("Replay player must have a loaded instance");
        this.player = player;
        this.clock = clock;
        anticheat = CatAC.install(config);
    }

    public ReplayReport<FrameResult> run(List<ReplayFrame<ClientPacket>> frames) {
        if (frames.size() > 100_000)
            throw new IllegalArgumentException("Replay exceeds 100000 inputs");
        return ReplayRunner.run(
                frames,
                (packet, time) -> {
                    clock.setNanos(time);
                    var before = anticheat.metrics();
                    MinecraftServer.getPacketListenerManager()
                            .processClientPacket(packet, player.getPlayerConnection());
                    EventDispatcher.call(new EntityTickEvent(player));
                    var after = anticheat.metrics();
                    return new FrameResult(
                            player.getPosition(),
                            after.violationSamples() - before.violationSamples(),
                            after.cancelledPackets() - before.cancelledPackets(),
                            after.setbacks() - before.setbacks(),
                            after.kicks() - before.kicks());
                });
    }

    public CatAC anticheat() {
        return anticheat;
    }

    public void close() {
        anticheat.close();
    }

    public record FrameResult(
            Pos position, long detections, long cancellations, long setbacks, long kicks) {}
}
