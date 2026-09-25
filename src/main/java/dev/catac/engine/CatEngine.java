package dev.catac.engine;

import dev.catac.check.CatCheck;
import dev.catac.check.CheckResult;
import dev.catac.api.NetworkSnapshot;
import dev.catac.api.CatACMetrics;
import dev.catac.api.CheckDescriptor;
import dev.catac.api.DamageContext;
import dev.catac.api.DamageDecision;
import dev.catac.api.PacketCost;
import dev.catac.api.PacketFloodEvent;
import dev.catac.api.FloodAction;
import dev.catac.check.builtin.FastBreakCheck;
import dev.catac.check.builtin.GroundSpoofCheck;
import dev.catac.check.builtin.HorizontalSpeedCheck;
import dev.catac.check.builtin.InvalidMovementPacketCheck;
import dev.catac.check.builtin.InventoryMoveCheck;
import dev.catac.check.builtin.InventorySanityCheck;
import dev.catac.check.builtin.MovementPacketRateCheck;
import dev.catac.check.builtin.PhaseCheck;
import dev.catac.check.builtin.ReachCheck;
import dev.catac.check.builtin.VerticalPhysicsCheck;
import dev.catac.check.builtin.WorldInteractionCheck;
import dev.catac.config.CatACConfig;
import dev.catac.internal.CheckRegistry;
import dev.catac.internal.AuraDecoyManager;
import dev.catac.internal.CollisionAnalyzer;
import dev.catac.internal.EntityHistoryManager;
import dev.catac.internal.EnforcementDecision;
import dev.catac.internal.RegisteredMovementCheck;
import dev.catac.internal.RegisteredPacketCheck;
import dev.catac.internal.TickHealth;
import dev.catac.state.CollisionSnapshot;
import dev.catac.state.MovementFrame;
import dev.catac.state.PlayerData;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityTeleportEvent;
import net.minestom.server.event.entity.EntityVelocityEvent;
import net.minestom.server.event.entity.EntityDamageEvent;
import net.minestom.server.event.entity.EntityDespawnEvent;
import net.minestom.server.event.entity.EntitySpawnEvent;
import net.minestom.server.event.entity.EntityTickEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.server.ServerTickMonitorEvent;
import net.minestom.server.network.packet.client.common.ClientPongPacket;
import net.minestom.server.network.packet.client.play.ClientTeleportConfirmPacket;
import net.minestom.server.network.packet.client.play.ClientInteractEntityPacket;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CatEngine {
    private static final String INVALID_MOVEMENT_ID = "packet.invalid-movement";
    private static final String COMBAT_REACH_ID = "combat.reach";
    private static final CheckDescriptor AURA_DECOY_DESCRIPTOR = new CheckDescriptor(
            "combat.aura-decoy", "Aura decoy", dev.catac.api.CheckCategory.COMBAT,
            new dev.catac.config.CheckPolicy(true, 1, 1, 1, 0, 1_000), false);
    private static final System.Logger LOGGER = System.getLogger(CatEngine.class.getName());

    private final CatACConfig config;
    private final TickHealth tickHealth;
    private final CheckRegistry registry;
    private final PlayerDataManager playerDataManager;
    private final EnforcementEngine enforcement;
    private final EntityHistoryManager entityHistories = new EntityHistoryManager();
    private final CollisionAnalyzer collisionAnalyzer = new CollisionAnalyzer();
    private final AuraDecoyManager auraDecoys = new AuraDecoyManager();
    private final List<RegisteredPacketCheck> packetChecks;
    private final List<RegisteredMovementCheck> movementChecks;
    private final EventNode<Event> eventNode = EventNode.all("catac-core");
    private final AtomicBoolean exemptionProviderAvailable = new AtomicBoolean(true);
    private final AtomicBoolean damageDecisionProviderAvailable = new AtomicBoolean(true);
    private final AtomicBoolean packetCostClassifierAvailable = new AtomicBoolean(true);
    private final AtomicBoolean packetFloodHandlerAvailable = new AtomicBoolean(true);

    public CatEngine(CatACConfig config, List<CatCheck> additionalChecks) {
        this.config = Objects.requireNonNull(config, "config");
        this.tickHealth = new TickHealth(config.lagCompensationThresholdMillis());
        this.registry = new CheckRegistry(config);
        this.playerDataManager = new PlayerDataManager(registry::size, config);
        this.enforcement = new EnforcementEngine(config);

        registry.register(new InvalidMovementPacketCheck());
        registry.register(new MovementPacketRateCheck(tickHealth));
        registry.register(new WorldInteractionCheck());
        registry.register(new FastBreakCheck(tickHealth));
        registry.register(new InventorySanityCheck());
        registry.register(new InventoryMoveCheck());
        registry.register(new ReachCheck(entityHistories, config));
        registry.register(new HorizontalSpeedCheck());
        registry.register(new VerticalPhysicsCheck());
        registry.register(new GroundSpoofCheck());
        registry.register(new PhaseCheck());
        additionalChecks.forEach(registry::register);
        registry.freeze();

        this.packetChecks = registry.packetChecks();
        this.movementChecks = registry.movementChecks();
        configureEvents();
    }

    public EventNode<Event> eventNode() {
        return eventNode;
    }

    public void bootstrapOnlinePlayers() {
        long now = System.nanoTime();
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            playerDataManager.getOrCreate(player, now);
        }
    }

    public void exempt(Player player, Duration duration) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration cannot be negative");
        }
        long now = System.nanoTime();
        playerDataManager.getOrCreate(player, now).exemptions().markManual(now, duration.toNanos());
    }

    public void denyDamage(Player attacker, Entity victim, Duration duration, String reason) {
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(victim, "victim");
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("duration must be positive");
        }
        long now = System.nanoTime();
        playerDataManager.getOrCreate(attacker, now).damageGuard().arm(victim.getUuid(), reason, now, duration.toNanos());
    }

    public int trackedPlayers() {
        return playerDataManager.size();
    }

    public NetworkSnapshot networkSnapshot(Player player, long nowNanos) {
        PlayerData data = playerDataManager.find(player);
        return data == null ? null : data.synchronization().snapshot(nowNanos);
    }

    public CatACMetrics metrics() {
        EnforcementMetrics metrics = enforcement.metrics();
        return new CatACMetrics(metrics.violationSamples(), metrics.alerts(), metrics.cancelledPackets(),
                metrics.setbacks(), metrics.kicks(), metrics.floodDrops(), metrics.floodKicks(), playerDataManager.size());
    }

    public void clear() {
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            PlayerData data = playerDataManager.find(player);
            if (data != null) {
                auraDecoys.remove(player, data.auraDecoy());
            }
        }
        playerDataManager.clear();
        entityHistories.clear();
    }

    private void configureEvents() {
        eventNode.addListener(PlayerPacketEvent.class, this::onPacket);
        eventNode.addListener(PlayerMoveEvent.class, this::onMove);
        eventNode.addListener(PlayerSpawnEvent.class, this::onSpawn);
        eventNode.addListener(PlayerDisconnectEvent.class, event -> removePlayer(event.getPlayer()));
        eventNode.addListener(EntityTeleportEvent.class, this::onTeleport);
        eventNode.addListener(EntityVelocityEvent.class, this::onVelocity);
        eventNode.addListener(EntityDamageEvent.class, this::onDamage);
        eventNode.addListener(EntitySpawnEvent.class, event -> entityHistories.track(event.getEntity(), System.nanoTime()));
        eventNode.addListener(EntityTickEvent.class, event -> entityHistories.track(event.getEntity(), System.nanoTime()));
        eventNode.addListener(EntityDespawnEvent.class, event -> entityHistories.remove(event.getEntity()));
        eventNode.addListener(ServerTickMonitorEvent.class, event -> {
            long now = System.nanoTime();
            tickHealth.recordTick(event.getTickMonitor().getTickTime(), now);
            playerDataManager.tickSynchronizations(now);
            for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                PlayerData data = playerDataManager.find(player);
                if (data != null && data.auraDecoy().expired(now)) {
                    auraDecoys.remove(player, data.auraDecoy());
                }
            }
        });
    }

    private void onPacket(PlayerPacketEvent event) {
        if (event.isCancelled()) {
            return;
        }
        long now = System.nanoTime();
        PlayerData data = playerDataManager.getOrCreate(event.getPlayer(), now);
        if (!allowPacket(event, data, now)) {
            return;
        }
        if (event.getPacket() instanceof ClientInteractEntityPacket attack && auraDecoys.targetsDecoy(data.auraDecoy(), attack)) {
            boolean confirmed = auraDecoys.confirmAttack(event.getPlayer(), data.auraDecoy(), attack, now);
            auraDecoys.remove(event.getPlayer(), data.auraDecoy());
            if (confirmed) {
                event.setCancelled(true);
                if (enforcement.handleAuraConfirmation(data, AURA_DECOY_DESCRIPTOR,
                        "attacked armed private decoy while target remained behind view")) {
                    event.getPlayer().kick(config.auraDecoyPolicy().kickMessage());
                }
                return;
            }
        }
        if (event.getPacket() instanceof ClientPongPacket pong) {
            data.synchronization().onPong(pong.id(), now);
        } else if (event.getPacket() instanceof ClientTeleportConfirmPacket confirm) {
            data.synchronization().onTeleportConfirm(confirm.teleportId(), now);
        }
        for (RegisteredPacketCheck registered : packetChecks) {
            if (!registered.runtime().active() || !registered.policy().enabled()) {
                continue;
            }
            String id = registered.check().descriptor().id();
            boolean securityCritical = INVALID_MOVEMENT_ID.equals(id);
            if (!securityCritical && isExternallyExempt(data, id, now)) {
                continue;
            }

            CheckResult result = evaluatePacketCheck(registered, event, data, now);
            if (COMBAT_REACH_ID.equals(id) && !result.passed() &&
                    result.severity() >= config.auraDecoyPolicy().triggerSeverity() &&
                    canDeployAuraDecoy(event.getPlayer(), data, now)) {
                auraDecoys.deploy(event.getPlayer(), data.auraDecoy(), config.auraDecoyPolicy(), now);
            }
            EnforcementDecision decision = enforcement.handle(data, registered.slot(),
                    registered.check().descriptor(), registered.policy(), result, now);
            if (decision.cancel()) {
                armDamageGuardForAttack(event.getPlayer(), data, event.getPacket(), id, now);
            }
            if (decision.cancel()) {
                event.setCancelled(true);
                if (!decision.kick()) {
                    return;
                }
            }
            if (decision.kick()) {
                event.getPlayer().kick(config.kickMessage());
                return;
            }
        }
    }

    private void onMove(PlayerMoveEvent event) {
        if (event.isCancelled()) {
            return;
        }
        long now = System.nanoTime();
        PlayerData data = playerDataManager.getOrCreate(event.getPlayer(), now);
        CollisionSnapshot collision = data.collision();
        collisionAnalyzer.analyze(event.getPlayer(), event.getPlayer().getPosition(), event.getNewPosition(), collision);

        MovementFrame frame = data.movementFrame();
        frame.reset(event.getPlayer(), event.getPlayer().getPosition(), event.getNewPosition(),
                event.isOnGround(), now, data.nextMovementSequence(), collision);
        data.prediction().prepare(frame, data.synchronization());

        boolean skipTemporalChecks = data.exemptions().movementExempt(now) ||
                data.synchronization().movementUncertain(now) ||
                tickHealth.isLagCompensating(now);
        boolean suspicious = false;
        boolean cancel = false;
        boolean setback = false;

        if (!skipTemporalChecks) {
            for (RegisteredMovementCheck registered : movementChecks) {
                if (!registered.runtime().active() || !registered.policy().enabled()) {
                    continue;
                }
                String id = registered.check().descriptor().id();
                if (isExternallyExempt(data, id, now)) {
                    continue;
                }
                CheckResult result = evaluateMovementCheck(registered, frame, data);
                suspicious |= !result.passed();
                EnforcementDecision decision = enforcement.handle(data, registered.slot(),
                        registered.check().descriptor(), registered.policy(), result, now);
                setback |= decision.setback();
                cancel |= decision.cancel() && !decision.setback();
                if (decision.kick()) {
                    event.getPlayer().kick(config.kickMessage());
                    cancel = true;
                    break;
                }
            }
        }

        if (setback) {
            event.setNewPosition(data.lastSafePosition());
            data.exemptions().markTeleport(now, config.teleportGraceNanos());
            data.synchronization().markTeleport(now);
            data.resetMotion(data.lastSafePosition(), now);
            return;
        }
        if (cancel) {
            event.setCancelled(true);
            data.exemptions().markTeleport(now, config.teleportGraceNanos());
            data.synchronization().markTeleport(now);
            data.resetMotion(event.getPlayer().getPosition(), now);
            return;
        }
        updateAcceptedMovement(data, frame, !suspicious);
    }

    private void updateAcceptedMovement(PlayerData data, MovementFrame frame, boolean clean) {
        CollisionSnapshot collision = frame.collision();
        data.previousHorizontalDistance(frame.horizontalDistance());
        data.previousDeltaY(frame.deltaY());
        data.prediction().observe(frame);
        data.positionHistory().add(frame.to(), frame.nowNanos());

        if (collision.supported()) {
            data.airFrames(0);
            data.stableGroundFrames(data.stableGroundFrames() + 1);
            if (clean && collision.complete() && !collision.insideSolid() && data.stableGroundFrames() >= 2) {
                data.lastSafePosition(frame.to());
            }
        } else {
            data.airFrames(data.airFrames() + 1);
            data.stableGroundFrames(0);
        }
    }

    private void onSpawn(PlayerSpawnEvent event) {
        long now = System.nanoTime();
        PlayerData data = playerDataManager.getOrCreate(event.getPlayer(), now);
        data.exemptions().markTeleport(now, config.joinGraceNanos());
        data.resetMotion(event.getPlayer().getPosition(), now);
    }

    private void removePlayer(Player player) {
        PlayerData data = playerDataManager.find(player);
        if (data != null) {
            auraDecoys.remove(player, data.auraDecoy());
        }
        playerDataManager.remove(player);
    }

    private boolean canDeployAuraDecoy(Player player, PlayerData data, long nowNanos) {
        GameMode gameMode = player.getGameMode();
        return config.auraDecoyPolicy().enabled() &&
                (gameMode == GameMode.SURVIVAL || gameMode == GameMode.ADVENTURE) &&
                player.getVehicle() == null && !player.isFlyingWithElytra() &&
                !data.exemptions().manualExempt(nowNanos) && !data.synchronization().movementUncertain(nowNanos);
    }

    private void onTeleport(EntityTeleportEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        long now = System.nanoTime();
        PlayerData data = playerDataManager.getOrCreate(player, now);
        data.exemptions().markTeleport(now, config.teleportGraceNanos());
        data.synchronization().markTeleport(now);
        data.resetMotion(event.getNewPosition(), now);
    }

    private void onVelocity(EntityVelocityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        long now = System.nanoTime();
        PlayerData data = playerDataManager.getOrCreate(player, now);
        data.exemptions().markVelocity(now, config.velocityGraceNanos());
        data.synchronization().markVelocity(now);
    }

    private void onDamage(EntityDamageEvent event) {
        if (event.isCancelled() || !config.damageProtectionPolicy().enabled()) {
            return;
        }
        Entity source = event.getDamage().getAttacker();
        if (!(source instanceof Player attacker)) {
            return;
        }
        PlayerData data = playerDataManager.find(attacker);
        if (data == null || !data.damageGuard().appliesTo(event.getEntity().getUuid(), System.nanoTime())) {
            return;
        }
        String reason = data.damageGuard().reason();
        data.damageGuard().clear();
        DamageDecision decision = DamageDecision.DENY;
        if (damageDecisionProviderAvailable.get()) {
            try {
                decision = Objects.requireNonNull(config.damageProtectionPolicy().decisionProvider()
                        .decide(new DamageContext(attacker, event.getEntity(), event.getDamage(), reason)),
                        "DamageDecisionProvider returned null");
            } catch (RuntimeException exception) {
                if (damageDecisionProviderAvailable.compareAndSet(true, false)) {
                    LOGGER.log(System.Logger.Level.ERROR,
                            "CatAC disabled the damage decision provider after it threw an exception", exception);
                }
            }
        }
        if (decision == DamageDecision.DENY) {
            event.setCancelled(true);
        }
    }

    private boolean allowPacket(PlayerPacketEvent event, PlayerData data, long nowNanos) {
        var policy = config.packetFloodPolicy();
        if (!policy.enabled()) return true;
        PacketCost cost = classifyPacket(event.getPlayer(), event.getPacket());
        if (data.packetFlood().tryConsume(cost, policy.totalBudget(), policy.heavyBudget(), nowNanos)) {
            return true;
        }
        int strikes = data.packetFlood().strike(nowNanos, policy.strikeWindow().toNanos());
        boolean kick = strikes >= policy.strikesBeforeKick();
        event.setCancelled(true);
        if (config.telemetryEnabled()) enforcement.metrics().flood(kick);
        PacketFloodEvent floodEvent = new PacketFloodEvent(event.getPlayer(), event.getPacket().getClass(), cost,
                strikes, kick ? FloodAction.KICK : FloodAction.DROP);
        publishFlood(floodEvent);
        if (kick) event.getPlayer().kick(policy.kickMessage());
        return false;
    }

    private PacketCost classifyPacket(Player player, net.minestom.server.network.packet.client.ClientPacket packet) {
        if (!packetCostClassifierAvailable.get()) return PacketCost.NORMAL;
        try {
            return Objects.requireNonNull(config.packetFloodPolicy().classifier().classify(player, packet),
                    "PacketCostClassifier returned null");
        } catch (RuntimeException exception) {
            if (packetCostClassifierAvailable.compareAndSet(true, false)) {
                LOGGER.log(System.Logger.Level.ERROR,
                        "CatAC disabled the packet cost classifier after it threw an exception", exception);
            }
            return PacketCost.NORMAL;
        }
    }

    private void publishFlood(PacketFloodEvent event) {
        try {
            MinecraftServer.getGlobalEventHandler().call(event);
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "CatAC packet flood event listener failed", exception);
        }
        if (!packetFloodHandlerAvailable.get()) return;
        try {
            config.packetFloodPolicy().handler().onFlood(event);
        } catch (RuntimeException exception) {
            if (packetFloodHandlerAvailable.compareAndSet(true, false)) {
                LOGGER.log(System.Logger.Level.ERROR,
                        "CatAC disabled the packet flood handler after it threw an exception", exception);
            }
        }
    }

    private void armDamageGuardForAttack(Player attacker, PlayerData data, Object packet, String reason,
                                         long nowNanos) {
        if (!config.damageProtectionPolicy().enabled() || !(packet instanceof ClientInteractEntityPacket interact) ||
                !(interact.type() instanceof ClientInteractEntityPacket.Attack)) {
            return;
        }
        if (attacker.getInstance() == null) {
            return;
        }
        Entity target = attacker.getInstance().getEntityById(interact.targetId());
        if (target instanceof LivingEntity) {
            data.damageGuard().arm(target.getUuid(), reason, nowNanos,
                    config.damageProtectionPolicy().denialWindow().toNanos());
        }
    }

    private boolean isExternallyExempt(PlayerData data, String checkId, long nowNanos) {
        boolean manuallyExempt = data.exemptions().manualExempt(nowNanos);
        if (manuallyExempt || !exemptionProviderAvailable.get()) {
            return manuallyExempt;
        }
        try {
            return config.exemptionProvider().isExempt(data.player(), checkId);
        } catch (RuntimeException exception) {
            if (exemptionProviderAvailable.compareAndSet(true, false)) {
                LOGGER.log(System.Logger.Level.ERROR,
                        "CatAC disabled the exemption provider after it threw an exception", exception);
            }
            return false;
        }
    }

    private CheckResult evaluatePacketCheck(RegisteredPacketCheck registered, PlayerPacketEvent event,
                                            PlayerData data, long nowNanos) {
        try {
            return Objects.requireNonNull(registered.check().evaluate(event, data, nowNanos),
                    "PacketCheck returned null");
        } catch (RuntimeException exception) {
            disableFaultyCheck(registered.runtime(), registered.check().descriptor().id(), exception);
            return CheckResult.pass();
        }
    }

    private CheckResult evaluateMovementCheck(RegisteredMovementCheck registered, MovementFrame frame,
                                              PlayerData data) {
        try {
            return Objects.requireNonNull(registered.check().evaluate(frame, data),
                    "MovementCheck returned null");
        } catch (RuntimeException exception) {
            disableFaultyCheck(registered.runtime(), registered.check().descriptor().id(), exception);
            return CheckResult.pass();
        }
    }

    private static void disableFaultyCheck(dev.catac.internal.CheckRuntime runtime, String checkId,
                                           RuntimeException exception) {
        if (runtime.disableAfterFault()) {
            LOGGER.log(System.Logger.Level.ERROR,
                    "CatAC disabled check '" + checkId + "' after an evaluation failure", exception);
        }
    }
}
