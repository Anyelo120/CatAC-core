package dev.catac.engine;

import dev.catac.api.*;
import dev.catac.check.*;
import dev.catac.check.builtin.*;
import dev.catac.config.CatACConfig;
import dev.catac.internal.*;
import dev.catac.state.*;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.*;
import net.minestom.server.event.*;
import net.minestom.server.event.entity.*;
import net.minestom.server.event.player.*;
import net.minestom.server.event.server.ServerTickMonitorEvent;
import net.minestom.server.network.packet.client.common.ClientPongPacket;
import net.minestom.server.network.packet.client.play.*;
import net.minestom.server.network.packet.server.play.EntityVelocityPacket;
import net.minestom.server.network.packet.server.play.PlayerPositionAndLookPacket;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Every mutable player component is serialized on that PlayerData's monitor. */
public final class CatEngine {
    private static final System.Logger LOGGER = System.getLogger(CatEngine.class.getName());
    private final CatACConfig config;
    private final TickHealth tickHealth;
    private final CheckRegistry registry;
    private final PlayerDataManager players;
    private final EnforcementEngine enforcement;
    private final EntityHistoryManager histories = new EntityHistoryManager();
    private final CollisionAnalyzer collisions = new CollisionAnalyzer();
    private final AuraDecoyManager decoys = new AuraDecoyManager();
    private final List<RegisteredPacketCheck> packetChecks;
    private final List<RegisteredMovementCheck> movementChecks;
    private final EventNode<Event> node = EventNode.all("catac-core");
    private final CallbackFaults callbackFaults = new CallbackFaults();
    private final AtomicBoolean exemptionProviderHealthy = new AtomicBoolean(true),
            classifierHealthy = new AtomicBoolean(true),
            floodHandlerHealthy = new AtomicBoolean(true),
            damageProviderHealthy = new AtomicBoolean(true);

    public CatEngine(CatACConfig config, List<CatCheck> additionalChecks) {
        this.config = Objects.requireNonNull(config);
        tickHealth = new TickHealth(config.lagCompensationThresholdMillis());
        registry = new CheckRegistry(config);
        players = new PlayerDataManager(registry::size, config);
        enforcement = new EnforcementEngine(config);
        registry.register(new InvalidMovementPacketCheck());
        registry.register(new MovementPacketRateCheck(tickHealth));
        registry.register(new WorldInteractionCheck());
        registry.register(new FastBreakCheck(tickHealth));
        registry.register(new InventorySanityCheck());
        registry.register(new InventoryMoveCheck());
        registry.register(new TargetValidityCheck());
        registry.register(new ReachCheck(histories, config));
        registry.register(new CombatRayCheck(histories, config));
        registry.register(new AuraDecoyCheck());
        registry.register(new ClientInputCheck());
        registry.register(new GroundStatusCheck(collisions));
        registry.register(new HorizontalSpeedCheck());
        registry.register(new VerticalPhysicsCheck());
        registry.register(new GroundSpoofCheck());
        registry.register(new PhaseCheck());
        registry.register(new MediumMovementCheck());
        registry.register(new VerticalDescentCheck());
        registry.register(new NoSlowCheck());
        registry.register(new KnockbackCheck());
        additionalChecks.forEach(registry::register);
        registry.freeze();
        packetChecks = registry.packetChecks();
        movementChecks = registry.movementChecks();
        configureEvents();
    }

    public EventNode<Event> eventNode() {
        return node;
    }

    private long now() {
        return config.clock().nanoTime();
    }

    public void bootstrapOnlinePlayers() {
        long now = now();
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers())
            players.getOrCreate(player, now);
    }

    public void exempt(Player player, Duration duration) {
        exempt(player, null, duration);
    }

    public void exempt(Player player, String id, Duration duration) {
        Objects.requireNonNull(player);
        long nanos = TimeWindow.checkedNanos(duration);
        long now = now();
        if (id != null
                && packetChecks.stream().noneMatch(c -> c.descriptor().id().equals(id))
                && movementChecks.stream().noneMatch(c -> c.descriptor().id().equals(id)))
            throw new IllegalArgumentException("Unknown check: " + id);
        PlayerData data = players.getOrCreate(player, now);
        synchronized (data) {
            if (id == null) data.exemptions().markManual(now, nanos);
            else data.exemptions().markScoped(id, now, nanos);
        }
    }

    /**
     * Legacy explicit next-damage guard, cleared at the next native attack. Prefer action IDs for
     * asynchronous damage.
     */
    public void denyDamage(Player attacker, Entity victim, Duration duration, String reason) {
        long nanos = TimeWindow.checkedNanos(duration);
        if (nanos == 0) throw new IllegalArgumentException("duration must be positive");
        PlayerData data = players.getOrCreate(Objects.requireNonNull(attacker), now());
        synchronized (data) {
            data.damageGuard().arm(Objects.requireNonNull(victim).getUuid(), reason, now(), nanos);
        }
    }

    public void denyDamage(
            Player attacker, Entity victim, long actionId, Duration duration, String reason) {
        if (actionId <= 0) throw new IllegalArgumentException("actionId must be positive");
        long nanos = TimeWindow.checkedNanos(duration);
        if (nanos == 0) throw new IllegalArgumentException("duration must be positive");
        PlayerData data = players.getOrCreate(attacker, now());
        synchronized (data) {
            data.damageGuard().arm(victim.getUuid(), actionId, reason, now(), nanos);
        }
    }

    public boolean consumeDamageDenial(Player attacker, Entity victim, long actionId) {
        if (actionId <= 0) throw new IllegalArgumentException("actionId must be positive");
        PlayerData data = players.find(attacker);
        if (data == null) return false;
        synchronized (data) {
            if (!data.damageGuard().appliesTo(victim.getUuid(), actionId, now())) return false;
            data.damageGuard().clear();
            return true;
        }
    }

    public int trackedPlayers() {
        return players.size();
    }

    public NetworkSnapshot networkSnapshot(Player player, long now) {
        PlayerData data = players.find(player);
        if (data == null) return null;
        synchronized (data) {
            return data.synchronization().snapshot(now);
        }
    }

    public SynchronizationDiagnostics synchronizationDiagnostics(Player player, long now) {
        PlayerData data = players.find(player);
        if (data == null) return null;
        synchronized (data) {
            return data.synchronization().diagnostics(now);
        }
    }

    public CatACMetrics metrics() {
        var m = enforcement.metrics();
        return new CatACMetrics(
                m.violationSamples(),
                m.alerts(),
                m.cancelledPackets(),
                m.setbacks(),
                m.kicks(),
                m.floodDrops(),
                m.floodKicks(),
                players.size());
    }

    public List<CheckDiagnostics> diagnostics() {
        var list = new ArrayList<CheckDiagnostics>();
        packetChecks.forEach(
                c -> list.add(c.runtime().snapshot(c.descriptor().id(), c.policy().enabled())));
        movementChecks.forEach(
                c -> list.add(c.runtime().snapshot(c.descriptor().id(), c.policy().enabled())));
        return List.copyOf(list);
    }

    public IntegrationHealth health() {
        long overflows = 0;
        for (PlayerData data : players.snapshot()) overflows += data.outboundOverflows();
        return new IntegrationHealth(
                exemptionProviderHealthy.get(),
                classifierHealthy.get(),
                floodHandlerHealthy.get(),
                damageProviderHealthy.get(),
                overflows,
                callbackFaults.count() + enforcement.callbackFaults());
    }

    public List<DetectionTrace> traces(Player player) {
        PlayerData data = players.find(player);
        if (data == null) return List.of();
        synchronized (data) {
            return data.traces().snapshot();
        }
    }

    public void clear() {
        for (PlayerData data : players.closeAndSnapshot())
            synchronized (data) {
                settle(data);
                data.retire();
                decoys.remove(data.player(), data.auraDecoy());
            }
        players.clear();
        histories.clear();
    }

    private void configureEvents() {
        node.addListener(PlayerPacketEvent.class, this::onPacket);
        node.addListener(PlayerMoveEvent.class, this::onMove);
        node.addListener(PlayerSpawnEvent.class, this::onSpawn);
        node.addListener(PlayerDisconnectEvent.class, e -> removePlayer(e.getPlayer()));
        node.addListener(EntityTeleportEvent.class, this::onTeleport);
        node.addListener(EntityVelocityEvent.class, this::onVelocity);
        node.addListener(EntityDamageEvent.class, this::onDamage);
        node.addListener(EntitySpawnEvent.class, e -> histories.track(e.getEntity(), now()));
        node.addListener(EntityTickEvent.class, this::onEntityTick);
        node.addListener(EntityDespawnEvent.class, e -> histories.remove(e.getEntity()));
        // Async output notifications enter a bounded mailbox, never the mutable player monitor.
        node.addListener(
                PlayerPacketOutEvent.class,
                e -> {
                    if (e.isCancelled()) return;
                    PlayerData data = players.find(e.getPlayer());
                    if (data == null) return;
                    if (e.getPacket() instanceof PlayerPositionAndLookPacket p)
                        data.offerOutbound(new OutboundSignal(p.teleportId(), null, now()));
                    else if (e.getPacket() instanceof EntityVelocityPacket p
                            && p.entityId() == e.getPlayer().getEntityId())
                        data.offerOutbound(new OutboundSignal(null, p.velocity(), now()));
                });
        node.addListener(
                ServerTickMonitorEvent.class,
                e -> tickHealth.recordTick(e.getTickMonitor().getTickTime(), now()));
    }

    private void settle(PlayerData data) {
        ActionReceipt receipt = data.consumeReceipt();
        if (receipt == null) return;
        if (receipt.packet() != null && receipt.packet().isCancelled()) {
            if (receipt.flood()) {
                if (config.telemetryEnabled()) enforcement.metrics().flood(false);
            } else enforcement.applied(true, false, false);
        } else if (receipt.movement() != null) {
            var event = receipt.movement();
            if (receipt.target() == null) {
                if (event.isCancelled()) enforcement.applied(true, false, false);
            } else if (!event.isCancelled()
                    && event.getPlayer().getInstance() == receipt.instance()
                    && event.getNewPosition().samePoint(receipt.target())
                    && event.getPlayer().getPosition().samePoint(receipt.target())
                    && event.getPlayer().getLastSentTeleportId() != receipt.previousTeleportId())
                enforcement.applied(false, true, false);
        }
    }

    private void drain(PlayerData data, long now) {
        settle(data);
        if (data.outputDegraded()) {
            data.prediction().reset();
            data.impulse().reset();
            data.exemptions().markVelocity(now, 1_000_000_000L);
        }
        OutboundSignal s;
        while ((s = data.pollOutbound()) != null) {
            if (s.teleportId() != null) {
                data.synchronization().sentTeleport(s.teleportId(), s.timeNanos());
                data.invalidatePending();
                data.positionHistory().clear();
                histories.discontinuity(data.player());
            }
            if (s.velocity() != null) {
                data.synchronization().markVelocity(s.velocity(), s.timeNanos());
                data.impulse().sent(s.velocity(), s.timeNanos());
            }
        }
        data.synchronization()
                .observeNativeTeleport(
                        data.player().getLastSentTeleportId(),
                        data.player().getLastReceivedTeleportId());
        var committedTeleport = data.committedServerTeleport(now);
        if (committedTeleport != null) {
            CollisionSnapshot verified = new CollisionSnapshot();
            collisions.analyze(data.player(), committedTeleport, verified);
            if (verified.complete() && !verified.insideSolid())
                data.resetMotion(committedTeleport, now);
        }
        data.confirmMovement();
        var vector = data.synchronization().consumeAcknowledgedVelocity();
        if (vector != null) data.impulse().acknowledge(vector, now);
    }

    private void onEntityTick(EntityTickEvent event) {
        long now = now();
        histories.track(event.getEntity(), now);
        if (!(event.getEntity() instanceof Player p)) return;
        PlayerData data = players.find(p);
        if (data == null) return;
        synchronized (data) {
            if (data.retired()) return;
            drain(data, now);
            data.synchronization().tick(p, now);
            if (data.auraDecoy().expired(now)) decoys.remove(p, data.auraDecoy());
        }
    }

    private void onPacket(PlayerPacketEvent event) {
        if (event.isCancelled()) return;
        long now = now();
        PlayerData data = players.getOrCreateIfOpen(event.getPlayer(), now);
        if (data == null) return;
        synchronized (data) {
            if (data.retired()) return;
            drain(data, now);
            boolean control =
                    event.getPacket() instanceof ClientPongPacket
                            || event.getPacket() instanceof ClientTeleportConfirmPacket
                            || event.getPacket()
                                    instanceof
                                    net.minestom.server.network.packet.client.common
                                            .ClientKeepAlivePacket
                            || event.getPacket() instanceof ClientChunkBatchReceivedPacket;
            if (!allowPacket(event, data, now, control)) return;
            if (event.getPacket() instanceof ClientPongPacket p)
                data.synchronization().onPong(p.id(), now);
            else if (event.getPacket() instanceof ClientTeleportConfirmPacket p)
                data.synchronization().onTeleportConfirm(p.teleportId(), now);
            if (event.getPacket() instanceof ClientInteractEntityPacket attack
                    && attack.type() instanceof ClientInteractEntityPacket.Attack) {
                data.damageGuard().clearLegacy();
                if (decoys.targetsDecoy(data.auraDecoy(), attack)) {
                    event.setCancelled(true);
                    data.receipt(ActionReceipt.packet(event, false));
                    try {
                        for (var c : packetChecks)
                            if (c.descriptor().id().equals("combat.aura-decoy")
                                    && c.policy().enabled()
                                    && c.runtime().active()) {
                                if (externallyExempt(data, c.descriptor().id(), now)) {
                                    if (config.telemetryEnabled())
                                        c.runtime().bypass(SkipReason.EXEMPT);
                                    continue;
                                }
                                enforcement.handle(
                                        data,
                                        c.slot(),
                                        c.descriptor(),
                                        c.policy(),
                                        evaluate(c, event, data, now),
                                        now);
                            }
                    } finally {
                        decoys.remove(event.getPlayer(), data.auraDecoy());
                    }
                    return;
                }
            }
            for (var c : packetChecks) {
                if (!c.runtime().active() || !c.policy().enabled() || !c.accepts(event.getPacket()))
                    continue;
                boolean hardening = c.descriptor().capabilities().hardening();
                if (!hardening && externallyExempt(data, c.descriptor().id(), now)) {
                    if (config.telemetryEnabled()) c.runtime().bypass(SkipReason.EXEMPT);
                    continue;
                }
                CheckResult result = evaluate(c, event, data, now);
                if (c.descriptor().id().equals("combat.reach")
                        && result.failed()
                        && result.severity() >= config.auraDecoyPolicy().triggerSeverity()
                        && canDeployDecoy(data, now))
                    decoys.deploy(data.player(), data.auraDecoy(), config.auraDecoyPolicy(), now);
                var decision =
                        enforcement.handle(data, c.slot(), c.descriptor(), c.policy(), result, now);
                if (decision.cancel()) {
                    event.setCancelled(true);
                    reconcile(event);
                    data.receipt(ActionReceipt.packet(event, false));
                }
                if (decision.kick()) {
                    event.getPlayer().kick(config.kickMessage());
                    enforcement.applied(false, false, true);
                    return;
                }
                if (decision.cancel()) return;
            }
        }
    }

    private void reconcile(PlayerPacketEvent event) {
        Player p = event.getPlayer();
        var world = p.getInstance();
        if (event.getPacket() instanceof ClientClickWindowPacket
                || event.getPacket() instanceof ClientCreativeInventoryActionPacket) {
            p.getInventory().update(p);
            if (p.getOpenInventory() != null) p.getOpenInventory().update(p);
            return;
        }
        net.minestom.server.coordinate.Point block = null, adjacent = null;
        int sequence = -1;
        if (event.getPacket() instanceof ClientPlayerActionPacket a) {
            block = a.blockPosition();
            sequence = a.sequence();
        } else if (event.getPacket() instanceof ClientPlayerBlockPlacementPacket a) {
            block = a.blockPosition();
            adjacent = block.add(a.blockFace().toDirection().vec());
            sequence = a.sequence();
        }
        if (block == null
                || world == null
                || sequence < 0
                || !Double.isFinite(block.x())
                || !Double.isFinite(block.y())
                || !Double.isFinite(block.z())) return;
        if (world.isChunkLoaded(block))
            p.sendPacket(
                    new net.minestom.server.network.packet.server.play.BlockChangePacket(
                            block, world.getBlock(block)));
        if (adjacent != null && world.isChunkLoaded(adjacent))
            p.sendPacket(
                    new net.minestom.server.network.packet.server.play.BlockChangePacket(
                            adjacent, world.getBlock(adjacent)));
        p.sendPacket(
                new net.minestom.server.network.packet.server.play.AcknowledgeBlockChangePacket(
                        sequence));
    }

    private void onMove(PlayerMoveEvent event) {
        if (event.isCancelled()) return;
        long now = now();
        PlayerData data = players.getOrCreateIfOpen(event.getPlayer(), now);
        if (data == null) return;
        synchronized (data) {
            if (data.retired()) return;
            drain(data, now);
            var proposed = event.getNewPosition();
            if (!Double.isFinite(proposed.x())
                    || !Double.isFinite(proposed.y())
                    || !Double.isFinite(proposed.z())
                    || !Float.isFinite(proposed.yaw())
                    || !Float.isFinite(proposed.pitch())
                    || Math.abs(proposed.x()) > 30_000_000
                    || Math.abs(proposed.y()) > 30_000_000
                    || Math.abs(proposed.z()) > 30_000_000) {
                event.setCancelled(true);
                data.receipt(ActionReceipt.movement(event, null));
                data.invalidatePending();
                return;
            }
            // Rotation-only packets cannot become a physics tick or teach a velocity of zero.
            if (event.getPlayer().getPosition().samePoint(event.getNewPosition())) return;
            collisions.analyze(
                    event.getPlayer(),
                    event.getPlayer().getPosition(),
                    event.getNewPosition(),
                    data.collision());
            MovementFrame frame = data.movementFrame();
            frame.reset(
                    data.player(),
                    data.player().getPosition(),
                    event.getNewPosition(),
                    event.isOnGround(),
                    now,
                    data.nextMovementSequence(),
                    data.collision());
            data.prediction().prepare(frame, data.synchronization(), data.impulse());
            boolean temporal =
                    data.exemptions().movementExempt(now)
                            || data.synchronization().movementUncertain(now)
                            || tickHealth.isLagCompensating(now);
            boolean modelAvailable = true;
            for (var required : movementChecks)
                if (required.descriptor().capabilities().correctMovement()
                        && (!required.policy().enabled() || !required.runtime().active())) {
                    modelAvailable = false;
                    break;
                }
            boolean clean = !temporal && data.collision().complete() && modelAvailable,
                    cancel = false,
                    setback = false;
            int evaluated = 0;
            for (var c : movementChecks) {
                if (!c.policy().enabled() || !c.runtime().active()) {
                    if (c.descriptor().capabilities().correctMovement()) clean = false;
                    continue;
                }
                boolean geometric = c.descriptor().id().equals("movement.phase");
                if (((temporal || !modelAvailable) && !geometric)
                        || externallyExempt(data, c.descriptor().id(), now)) {
                    if (config.telemetryEnabled())
                        c.runtime()
                                .bypass(
                                        !modelAvailable
                                                ? SkipReason.INTEGRATION_FAILURE
                                                : temporal
                                                        ? SkipReason.SYNCHRONIZING
                                                        : SkipReason.EXEMPT);
                    if (c.descriptor().capabilities().correctMovement()) clean = false;
                    continue;
                }
                CheckResult result = evaluate(c, frame, data);
                if (result.evaluated()) evaluated++;
                if (c.descriptor().capabilities().correctMovement()
                        && (result.failed() || result.outcome() == CheckResult.Outcome.UNCERTAIN))
                    clean = false;
                var decision =
                        enforcement.handle(data, c.slot(), c.descriptor(), c.policy(), result, now);
                cancel |= decision.cancel();
                setback |= decision.setback();
                if (decision.kick()) {
                    event.setCancelled(true);
                    data.player().kick(config.kickMessage());
                    enforcement.applied(true, false, true);
                    data.invalidatePending();
                    return;
                }
            }
            if (setback && data.hasSafePosition()) {
                CollisionSnapshot anchor = new CollisionSnapshot();
                collisions.analyze(data.player(), data.lastSafePosition(), anchor);
                if (!anchor.complete() || anchor.insideSolid()) {
                    event.setCancelled(true);
                    data.receipt(ActionReceipt.movement(event, null));
                    data.invalidatePending();
                    return;
                }
                event.setNewPosition(data.lastSafePosition());
                data.receipt(ActionReceipt.movement(event, data.lastSafePosition()));
                data.synchronization().markTeleport(now, data.player().getLastSentTeleportId());
                data.exemptions().markTeleport(now, config.teleportGraceNanos());
                data.resetMotion(data.lastSafePosition(), now);
                return;
            }
            if (cancel) {
                event.setCancelled(true);
                data.receipt(ActionReceipt.movement(event, null));
                data.synchronization().markTeleport(now, data.player().getLastSentTeleportId());
                data.exemptions().markTeleport(now, config.teleportGraceNanos());
                data.invalidatePending();
                data.prediction().reset();
                return;
            }
            data.stageMovement(frame, clean && evaluated > 0);
        }
    }

    private void onSpawn(PlayerSpawnEvent event) {
        long now = now();
        PlayerData data = players.getOrCreateIfOpen(event.getPlayer(), now);
        if (data == null) return;
        synchronized (data) {
            if (data.retired()) return;
            data.exemptions().markTeleport(now, config.joinGraceNanos());
            data.resetMotion(data.player().getPosition(), now);
        }
    }

    private void onTeleport(EntityTeleportEvent event) {
        histories.discontinuity(event.getEntity());
        if (!(event.getEntity() instanceof Player p)) return;
        long now = now();
        PlayerData data = players.getOrCreateIfOpen(p, now);
        if (data == null) return;
        synchronized (data) {
            if (data.retired()) return;
            data.exemptions().markTeleport(now, config.teleportGraceNanos());
            data.synchronization().markTeleport(now, p.getLastSentTeleportId());
            // The event is pre-commit: do not make its candidate a trusted anchor.
            data.serverTeleport(event.getNewPosition(), now);
            data.prediction().reset();
            data.impulse().reset();
            data.digging().clear();
            data.positionHistory().clear();
        }
    }

    private void onVelocity(EntityVelocityEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player p)) return;
        long now = now();
        PlayerData data = players.getOrCreateIfOpen(p, now);
        if (data == null) return;
        synchronized (data) {
            if (data.retired()) return;
            data.exemptions().markVelocity(now, config.velocityGraceNanos());
            data.prediction().reset();
        }
    }

    private void onDamage(EntityDamageEvent event) {
        if (event.isCancelled()
                || !config.damageProtectionPolicy().enabled()
                || !(event.getDamage().getAttacker() instanceof Player p)) return;
        PlayerData data = players.find(p);
        if (data == null) return;
        synchronized (data) {
            if (!data.damageGuard().appliesTo(event.getEntity().getUuid(), now())) return;
            String reason = data.damageGuard().reason();
            data.damageGuard().clear();
            DamageDecision decision = DamageDecision.DENY;
            if (damageProviderHealthy.get())
                try {
                    decision =
                            Objects.requireNonNull(
                                    config.damageProtectionPolicy()
                                            .decisionProvider()
                                            .decide(
                                                    new DamageContext(
                                                            p,
                                                            event.getEntity(),
                                                            event.getDamage(),
                                                            reason)));
                } catch (RuntimeException ex) {
                    report(damageProviderHealthy, "damage provider", ex);
                }
            if (decision == DamageDecision.DENY) event.setCancelled(true);
        }
    }

    private void removePlayer(Player p) {
        PlayerData data = players.find(p);
        if (data != null)
            synchronized (data) {
                settle(data);
                data.retire();
                decoys.remove(p, data.auraDecoy());
            }
        players.remove(p);
        histories.remove(p);
    }

    private boolean canDeployDecoy(PlayerData d, long now) {
        Player p = d.player();
        return config.auraDecoyPolicy().enabled()
                && d.clientProfile() == ClientProfile.JAVA_1_21_11
                && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)
                && p.getVehicle() == null
                && !p.isFlyingWithElytra()
                && !d.exemptions().manualExempt(now)
                && !d.synchronization().movementUncertain(now);
    }

    private boolean externallyExempt(PlayerData d, String id, long now) {
        if (d.exemptions().manualExempt(now) || d.exemptions().scopedExempt(id, now)) return true;
        if (!exemptionProviderHealthy.get()) return true;
        try {
            return config.exemptionProvider().isExempt(d.player(), id);
        } catch (RuntimeException ex) {
            report(exemptionProviderHealthy, "exemption provider", ex);
            return true;
        }
    }

    private boolean allowPacket(
            PlayerPacketEvent event, PlayerData data, long now, boolean control) {
        var policy = config.packetFloodPolicy();
        if (!policy.enabled()) return true;
        PacketCost cost = PacketCost.NORMAL;
        if (classifierHealthy.get())
            try {
                cost =
                        Objects.requireNonNull(
                                policy.classifier().classify(data.player(), event.getPacket()));
            } catch (RuntimeException ex) {
                report(classifierHealthy, "packet classifier", ex);
            }
        // A separate bounded reserve preserves confirmations after an ordinary burst.
        if (control
                ? data.packetFlood().tryControl(now)
                : data.packetFlood()
                        .tryConsume(cost, policy.totalBudget(), policy.heavyBudget(), now))
            return true;
        int strikes = data.packetFlood().strike(now, policy.strikeWindow().toNanos());
        boolean kick = policy.kickEnabled() && strikes >= policy.strikesBeforeKick();
        event.setCancelled(true);
        if (kick) {
            if (config.telemetryEnabled()) enforcement.metrics().flood(true);
        } else data.receipt(ActionReceipt.packet(event, true));
        if (data.packetFlood().notifyAllowed(now, policy.notificationCooldown().toNanos())
                || kick) {
            var e =
                    new PacketFloodEvent(
                            data.player(),
                            event.getPacket().getClass(),
                            cost,
                            strikes,
                            kick ? FloodAction.KICK : FloodAction.DROP);
            try {
                MinecraftServer.getGlobalEventHandler().call(e);
            } catch (RuntimeException ex) {
                callbackFaults.report(CallbackFaults.Kind.FLOOD_EVENT, now, ex);
            }
            if (floodHandlerHealthy.get())
                try {
                    policy.handler().onFlood(e);
                } catch (RuntimeException ex) {
                    report(floodHandlerHealthy, "flood handler", ex);
                }
        }
        if (kick) data.player().kick(policy.kickMessage());
        return false;
    }

    private CheckResult evaluate(
            RegisteredPacketCheck c, PlayerPacketEvent e, PlayerData d, long now) {
        long start = config.telemetryEnabled() ? System.nanoTime() : 0;
        CheckResult result;
        try {
            result = Objects.requireNonNull(c.check().evaluate(e, d, now));
        } catch (RuntimeException ex) {
            fault(c.runtime(), c.descriptor().id(), ex);
            result = CheckResult.uncertain(SkipReason.INTEGRATION_FAILURE);
        }
        if (config.telemetryEnabled()) c.runtime().record(result, System.nanoTime() - start);
        return result;
    }

    private CheckResult evaluate(RegisteredMovementCheck c, MovementFrame f, PlayerData d) {
        long start = config.telemetryEnabled() ? System.nanoTime() : 0;
        CheckResult result;
        try {
            result = Objects.requireNonNull(c.check().evaluate(f, d));
        } catch (RuntimeException ex) {
            fault(c.runtime(), c.descriptor().id(), ex);
            result = CheckResult.uncertain(SkipReason.INTEGRATION_FAILURE);
        }
        if (config.telemetryEnabled()) c.runtime().record(result, System.nanoTime() - start);
        return result;
    }

    private static void fault(CheckRuntime runtime, String id, RuntimeException ex) {
        if (runtime.disableAfterFault())
            LOGGER.log(System.Logger.Level.ERROR, "Check disabled after fault: " + id, ex);
    }

    private static void report(AtomicBoolean healthy, String name, RuntimeException ex) {
        if (healthy.compareAndSet(true, false))
            LOGGER.log(System.Logger.Level.ERROR, "Integration degraded: " + name, ex);
    }
}
