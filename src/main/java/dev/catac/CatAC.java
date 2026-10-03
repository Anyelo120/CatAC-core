package dev.catac;

import dev.catac.api.CatACMetrics;
import dev.catac.api.CatACState;
import dev.catac.api.NetworkSnapshot;
import dev.catac.check.CatCheck;
import dev.catac.config.CatACConfig;
import dev.catac.engine.CatEngine;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class CatAC implements AutoCloseable {
    private static final AtomicReference<CatAC> INSTALLED = new AtomicReference<>();

    private final CatACConfig config;
    private final CatEngine engine;
    private final AtomicReference<CatACState> state = new AtomicReference<>(CatACState.NEW);

    private CatAC(CatACConfig config, List<CatCheck> additionalChecks) {
        this.config = config;
        this.engine = new CatEngine(config, additionalChecks);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CatAC install(CatACConfig config) {
        return builder().config(config).build().start();
    }

    /**
     * Returns the CatAC instance currently attached to the global Minestom event handler, if there
     * is one.
     */
    public static Optional<CatAC> current() {
        return Optional.ofNullable(INSTALLED.get());
    }

    public synchronized CatAC start() {
        if (!state.compareAndSet(CatACState.NEW, CatACState.STARTED)) {
            throw new IllegalStateException("CatAC cannot start from state " + state.get());
        }
        if (!INSTALLED.compareAndSet(null, this)) {
            state.set(CatACState.NEW);
            throw new IllegalStateException("A CatAC instance is already installed");
        }

        boolean attached = false;
        try {
            MinecraftServer.getGlobalEventHandler().addChild(engine.eventNode());
            attached = true;
            engine.bootstrapOnlinePlayers();
            return this;
        } catch (RuntimeException exception) {
            if (attached) {
                MinecraftServer.getGlobalEventHandler().removeChild(engine.eventNode());
            }
            engine.clear();
            INSTALLED.compareAndSet(this, null);
            state.set(CatACState.STOPPED);
            throw exception;
        }
    }

    public synchronized void stop() {
        CatACState previous = state.getAndSet(CatACState.STOPPED);
        if (previous != CatACState.STARTED) {
            return;
        }
        try {
            MinecraftServer.getGlobalEventHandler().removeChild(engine.eventNode());
        } finally {
            engine.clear();
            INSTALLED.compareAndSet(this, null);
        }
    }

    @Override
    public void close() {
        stop();
    }

    public void exempt(Player player, Duration duration) {
        ensureStarted();
        engine.exempt(player, duration);
    }

    /**
     * Protects one target from the attacker's next matching damage event. This is intended for a
     * custom check that already invalidated a combat action.
     */
    public void denyDamage(Player attacker, Entity victim, Duration duration, String reason) {
        ensureStarted();
        engine.denyDamage(attacker, victim, duration, reason);
    }

    public Optional<dev.catac.api.SynchronizationDiagnostics> synchronizationDiagnostics(
            Player player) {
        ensureStarted();
        return Optional.ofNullable(
                engine.synchronizationDiagnostics(
                        Objects.requireNonNull(player), config.clock().nanoTime()));
    }

    public dev.catac.api.IntegrationHealth health() {
        ensureStarted();
        return engine.health();
    }

    public java.util.List<dev.catac.api.CheckDiagnostics> diagnostics() {
        ensureStarted();
        return engine.diagnostics();
    }

    public java.util.List<dev.catac.api.DetectionTrace> traces(Player player) {
        ensureStarted();
        return engine.traces(Objects.requireNonNull(player));
    }

    public void exempt(Player player, String checkId, Duration duration) {
        ensureStarted();
        engine.exempt(player, checkId, duration);
    }

    public void denyDamage(
            Player attacker, Entity victim, long actionId, Duration duration, String reason) {
        ensureStarted();
        engine.denyDamage(attacker, victim, actionId, duration, reason);
    }

    /** Consume this exact host action before applying custom delayed damage. */
    public boolean consumeDamageDenial(Player attacker, Entity victim, long actionId) {
        ensureStarted();
        return engine.consumeDamageDenial(attacker, victim, actionId);
    }

    public CatACConfig config() {
        return config;
    }

    public boolean isStarted() {
        return state.get() == CatACState.STARTED;
    }

    public CatACState state() {
        return state.get();
    }

    public int trackedPlayers() {
        return engine.trackedPlayers();
    }

    /**
     * Returns CatAC's bounded network synchronization view for a tracked player. This does not send
     * a packet or retain additional state.
     */
    public Optional<NetworkSnapshot> networkSnapshot(Player player) {
        ensureStarted();
        Objects.requireNonNull(player, "player");
        return Optional.ofNullable(engine.networkSnapshot(player, config.clock().nanoTime()));
    }

    /** Returns cumulative telemetry without retaining player or packet payloads. */
    public CatACMetrics metrics() {
        ensureStarted();
        return engine.metrics();
    }

    private void ensureStarted() {
        if (!isStarted()) {
            throw new IllegalStateException("CatAC is not started (state=" + state.get() + ')');
        }
    }

    public static final class Builder {
        private CatACConfig config = CatACConfig.defaults();
        private final List<CatCheck> additionalChecks = new ArrayList<>();

        private Builder() {}

        public Builder config(CatACConfig config) {
            this.config = Objects.requireNonNull(config, "config");
            return this;
        }

        public Builder addCheck(CatCheck check) {
            additionalChecks.add(Objects.requireNonNull(check, "check"));
            return this;
        }

        public CatAC build() {
            return new CatAC(config, List.copyOf(additionalChecks));
        }
    }
}
