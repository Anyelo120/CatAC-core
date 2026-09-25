package dev.catac.engine;

import dev.catac.config.CatACConfig;
import dev.catac.state.PlayerData;
import net.minestom.server.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

public final class PlayerDataManager {
    private final ConcurrentHashMap<UUID, PlayerData> players = new ConcurrentHashMap<>();
    private final IntSupplier checkCount;
    private final CatACConfig config;

    public PlayerDataManager(IntSupplier checkCount, CatACConfig config) {
        this.checkCount = checkCount;
        this.config = config;
    }

    public PlayerData getOrCreate(Player player, long nowNanos) {
        PlayerData existing = players.get(player.getUuid());
        if (existing != null && existing.player() == player) {
            return existing;
        }
        return players.compute(player.getUuid(), (uuid, current) ->
                current != null && current.player() == player
                        ? current
                        : new PlayerData(player, checkCount.getAsInt(), config, nowNanos));
    }

    public PlayerData find(UUID playerId) {
        return players.get(playerId);
    }

    public PlayerData find(Player player) {
        PlayerData data = players.get(player.getUuid());
        return data != null && data.player() == player ? data : null;
    }

    public void tickSynchronizations(long nowNanos) {
        for (PlayerData data : players.values()) {
            data.synchronization().tick(data.player(), nowNanos);
        }
    }

    public void remove(Player player) {
        players.computeIfPresent(player.getUuid(), (uuid, data) -> data.player() == player ? null : data);
    }

    public void clear() {
        players.clear();
    }

    public int size() {
        return players.size();
    }
}
