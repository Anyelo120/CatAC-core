package dev.catac.engine;

import dev.catac.config.CatACConfig;
import dev.catac.state.PlayerData;

import net.minestom.server.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

public final class PlayerDataManager {
    private boolean closed;
    private final Object creation = new Object();
    private final ConcurrentHashMap<UUID, PlayerData> players = new ConcurrentHashMap<>();
    private final IntSupplier checkCount;
    private final CatACConfig config;

    public PlayerDataManager(IntSupplier checkCount, CatACConfig config) {
        this.checkCount = checkCount;
        this.config = config;
    }

    public PlayerData getOrCreate(Player player, long now) {
        PlayerData data = getOrCreateIfOpen(player, now);
        if (data == null) throw new IllegalStateException("Player manager closed");
        return data;
    }

    public PlayerData getOrCreateIfOpen(Player player, long now) {
        synchronized (creation) {
            if (closed) return null;
            return players.compute(
                    player.getUuid(),
                    (uuid, current) ->
                            current != null && current.player() == player
                                    ? current
                                    : new PlayerData(player, checkCount.getAsInt(), config, now));
        }
    }

    public java.util.List<PlayerData> closeAndSnapshot() {
        synchronized (creation) {
            closed = true;
            return java.util.List.copyOf(players.values());
        }
    }

    public PlayerData find(UUID playerId) {
        return players.get(playerId);
    }

    public PlayerData find(Player player) {
        PlayerData data = players.get(player.getUuid());
        return data != null && data.player() == player ? data : null;
    }

    public java.util.List<PlayerData> snapshot() {
        return java.util.List.copyOf(players.values());
    }

    public void tickSynchronizations(long nowNanos) {
        for (PlayerData data : players.values()) {
            synchronized (data) {
                data.synchronization().tick(data.player(), nowNanos);
            }
        }
    }

    public void remove(Player player) {
        players.computeIfPresent(
                player.getUuid(), (uuid, data) -> data.player() == player ? null : data);
    }

    public void clear() {
        players.clear();
    }

    public int size() {
        return players.size();
    }
}
