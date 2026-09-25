package dev.catac.internal;

import dev.catac.state.PositionHistory;
import net.minestom.server.entity.Entity;

import java.util.concurrent.ConcurrentHashMap;

/** Bounded temporal position history for combat-relevant entities. */
public final class EntityHistoryManager {
    private final ConcurrentHashMap<Integer, TrackedHistory> histories = new ConcurrentHashMap<>();

    public void track(Entity entity, long nowNanos) {
        int entityId = entity.getEntityId();
        TrackedHistory history = histories.get(entityId);
        if (history != null && history.entity == entity) {
            history.positions.add(entity.getPosition(), nowNanos);
            return;
        }
        histories.compute(entityId, (id, current) -> {
            if (current != null && current.entity == entity) {
                current.positions.add(entity.getPosition(), nowNanos);
                return current;
            }
            TrackedHistory replacement = new TrackedHistory(entity);
            replacement.positions.add(entity.getPosition(), nowNanos);
            return replacement;
        });
    }

    public PositionHistory find(Entity entity) {
        TrackedHistory history = histories.get(entity.getEntityId());
        return history != null && history.entity == entity ? history.positions : null;
    }

    public void remove(Entity entity) {
        histories.computeIfPresent(entity.getEntityId(), (id, history) -> history.entity == entity ? null : history);
    }

    public void clear() {
        histories.clear();
    }

    private static final class TrackedHistory {
        private final Entity entity;
        private final PositionHistory positions = new PositionHistory();

        private TrackedHistory(Entity entity) {
            this.entity = entity;
        }
    }
}
