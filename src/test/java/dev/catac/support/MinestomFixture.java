package dev.catac.support;

import dev.catac.config.CatACConfig;
import dev.catac.state.PlayerData;
import dev.catac.testing.DeterministicClock;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.*;
import net.minestom.server.entity.*;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.player.PlayerPacketOutEvent;
import net.minestom.server.instance.*;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.packet.client.play.ClientTeleportConfirmPacket;
import net.minestom.server.network.packet.server.*;
import net.minestom.server.network.player.*;

import java.net.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Real loaded Minestom chunks and Player; only the network transport is captured in memory. */
public final class MinestomFixture implements AutoCloseable {
    public final InstanceContainer world;
    public final CaptureConnection connection = new CaptureConnection();
    public final Player player;
    public final DeterministicClock clock = new DeterministicClock(-10_000_000_000L);

    public MinestomFixture() {
        if (MinecraftServer.process() == null) MinecraftServer.init();
        world = MinecraftServer.getInstanceManager().createInstanceContainer();
        world.setChunkLoader(ChunkLoader.noop());
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) world.loadChunk(x, z).join();
        connection.setClientState(ConnectionState.PLAY);
        connection.setServerState(ConnectionState.PLAY);
        player = new Player(connection, new GameProfile(UUID.randomUUID(), "ReplayPlayer"));
        var settings = ClientSettings.DEFAULT;
        player.refreshSettings(
                new ClientSettings(
                        settings.locale(),
                        (byte) 2,
                        settings.chatMessageType(),
                        settings.chatColors(),
                        settings.displayedSkinParts(),
                        settings.mainHand(),
                        settings.enableTextFiltering(),
                        settings.allowServerListings(),
                        settings.particleSetting()));
        for (int x = -8; x <= 15; x++)
            for (int z = -8; z <= 8; z++) world.setBlock(x, 0, z, Block.STONE);
        player.setInstance(world, new Pos(.5, 1, .5)).join();
        MinecraftServer.getPacketListenerManager()
                .processClientPacket(
                        new ClientTeleportConfirmPacket(player.getLastSentTeleportId()),
                        connection);
        connection.packets.clear();
    }

    public CatACConfig.Builder config() {
        return CatACConfig.builder()
                .clock(clock)
                .joinGrace(Duration.ZERO)
                .teleportGrace(Duration.ZERO)
                .velocityGrace(Duration.ZERO);
    }

    public PlayerData data(int checks) {
        return new PlayerData(player, checks, config().build(), clock.nanoTime());
    }

    public void move(Pos p) {
        player.refreshPosition(p);
    }

    public void close() {
        player.remove();
        MinecraftServer.getInstanceManager().unregisterInstance(world);
    }

    public static final class CaptureConnection extends PlayerConnection {
        public final List<ServerPacket> packets = new CopyOnWriteArrayList<>();

        public void sendPacket(SendablePacket packet) {
            var extracted = SendablePacket.extractServerPacket(getServerState(), packet);
            if (extracted != null) {
                var event = new PlayerPacketOutEvent(getPlayer(), extracted);
                if (getPlayer() != null) EventDispatcher.call(event);
                if (!event.isCancelled()) packets.add(extracted);
            }
        }

        public SocketAddress getRemoteAddress() {
            return new InetSocketAddress("127.0.0.1", 25565);
        }
    }
}
