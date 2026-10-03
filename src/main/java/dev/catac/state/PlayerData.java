package dev.catac.state;

import dev.catac.config.CatACConfig;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;

import java.util.Objects;

public final class PlayerData {
    private final Player player;
    private final ViolationState[] violations;
    private final ExemptionState exemptions;
    private final PlayerSynchronization synchronization;
    private final MovementPrediction prediction = new MovementPrediction();
    private final CollisionSnapshot collision = new CollisionSnapshot();
    private final MovementFrame movementFrame = new MovementFrame();
    private final DiggingState digging = new DiggingState();
    private final PositionHistory positionHistory = new PositionHistory();
    private final AuraDecoyState auraDecoy = new AuraDecoyState();
    private final DamageGuardState damageGuard = new DamageGuardState();
    private final PacketFloodState packetFlood;

    private boolean retired;
    private ActionReceipt receipt;
    private final java.util.concurrent.atomic.AtomicLong outboundOverflows =
            new java.util.concurrent.atomic.AtomicLong();
    private long consumedOverflows;

    public boolean retired() {
        return retired;
    }

    public void retire() {
        retired = true;
        pending = false;
    }

    public void receipt(ActionReceipt receipt) {
        this.receipt = receipt;
    }

    public ActionReceipt consumeReceipt() {
        var r = receipt;
        receipt = null;
        return r;
    }

    public long outboundOverflows() {
        return outboundOverflows.get();
    }

    public boolean outputDegraded() {
        long count = outboundOverflows.get();
        if (count == consumedOverflows) return false;
        consumedOverflows = count;
        return true;
    }

    private final java.util.concurrent.ArrayBlockingQueue<OutboundSignal> outbound =
            new java.util.concurrent.ArrayBlockingQueue<>(16);
    private final ImpulseState impulse = new ImpulseState();
    private final ClientInputState input = new ClientInputState();

    public void offerOutbound(OutboundSignal signal) {
        if (!outbound.offer(signal)) {
            outboundOverflows.incrementAndGet();
            outbound.poll();
            outbound.offer(signal);
        }
    }

    public OutboundSignal pollOutbound() {
        return outbound.poll();
    }

    public ImpulseState impulse() {
        return impulse;
    }

    public ClientInputState input() {
        return input;
    }

    private final TraceBuffer traces;
    private dev.catac.api.ClientProfile clientProfile;
    private net.minestom.server.instance.Instance safeInstance;
    private boolean hasSafePosition;
    private final MovementFrame pendingFrame = new MovementFrame();
    private final CollisionSnapshot pendingCollision = new CollisionSnapshot();
    private net.minestom.server.instance.Instance pendingInstance;
    private boolean pending, pendingClean;
    private Pos serverTeleport;
    private net.minestom.server.instance.Instance serverTeleportInstance;
    private final TimeWindow serverTeleportWindow = new TimeWindow();

    public void serverTeleport(Pos target, long now) {
        serverTeleport = target;
        serverTeleportInstance = player.getInstance();
        serverTeleportWindow.open(now, 3_000_000_000L);
        hasSafePosition = false;
        pending = false;
    }

    public Pos committedServerTeleport(long now) {
        if (serverTeleport == null) return null;
        if (!serverTeleportWindow.active(now) || serverTeleportInstance != player.getInstance()) {
            serverTeleport = null;
            return null;
        }
        if (!player.getPosition().samePoint(serverTeleport)) return null;
        Pos target = serverTeleport;
        serverTeleport = null;
        return target;
    }

    private Pos lastSafePosition;
    private long movementSequence;
    private long lastMovementPacketNanos;
    private boolean hasMovementPacket;

    public boolean hasMovementPacket() {
        return hasMovementPacket;
    }

    private double movementPacketBalance;
    private double previousHorizontalDistance;
    private double previousDeltaY;
    private int airFrames;
    private int stableGroundFrames;

    public PlayerData(Player player, int checkCount, CatACConfig config, long nowNanos) {
        this.player = Objects.requireNonNull(player, "player");
        this.violations = new ViolationState[checkCount];
        for (int i = 0; i < checkCount; i++) {
            violations[i] = new ViolationState();
        }
        this.exemptions = new ExemptionState(nowNanos, config.joinGraceNanos());
        this.synchronization =
                new PlayerSynchronization(
                        config.networkProbeIntervalNanos(),
                        config.networkAcknowledgementTimeoutNanos());
        this.packetFlood =
                new PacketFloodState(
                        config.packetFloodPolicy().totalBudget(),
                        config.packetFloodPolicy().heavyBudget(),
                        nowNanos);
        this.traces = new TraceBuffer(config.traceCapacity());
        try {
            this.clientProfile =
                    Objects.requireNonNull(config.clientProfileProvider().profile(player));
        } catch (RuntimeException ex) {
            this.clientProfile = dev.catac.api.ClientProfile.UNKNOWN;
        }
        this.safeInstance = player.getInstance();
        this.hasSafePosition = safeInstance != null;
        this.lastSafePosition = player.getPosition();
        this.positionHistory.add(
                player.getPosition(), player.getBoundingBox(), player.getInstance(), nowNanos);
    }

    public Player player() {
        return player;
    }

    public ViolationState violation(int slot) {
        return violations[slot];
    }

    public ExemptionState exemptions() {
        return exemptions;
    }

    public PlayerSynchronization synchronization() {
        return synchronization;
    }

    public MovementPrediction prediction() {
        return prediction;
    }

    public CollisionSnapshot collision() {
        return collision;
    }

    public MovementFrame movementFrame() {
        return movementFrame;
    }

    public DiggingState digging() {
        return digging;
    }

    public PositionHistory positionHistory() {
        return positionHistory;
    }

    public AuraDecoyState auraDecoy() {
        return auraDecoy;
    }

    public DamageGuardState damageGuard() {
        return damageGuard;
    }

    public PacketFloodState packetFlood() {
        return packetFlood;
    }

    public TraceBuffer traces() {
        return traces;
    }

    public dev.catac.api.ClientProfile clientProfile() {
        return clientProfile;
    }

    public long movementSequence() {
        return movementSequence;
    }

    public boolean hasSafePosition() {
        return hasSafePosition && safeInstance == player.getInstance();
    }

    public void stageMovement(MovementFrame f, boolean clean) {
        pendingCollision.copyFrom(f.collision());
        pendingFrame.reset(
                player,
                f.from(),
                f.to(),
                f.clientOnGround(),
                f.nowNanos(),
                f.sequence(),
                pendingCollision);
        pendingInstance = player.getInstance();
        pending = true;
        pendingClean = clean;
    }

    /** Called after native listeners completed, never from the move callback itself. */
    public void confirmMovement() {
        if (!pending) return;
        pending = false;
        if (player.getInstance() != pendingInstance
                || !player.getPosition().samePoint(pendingFrame.to())) {
            prediction.reset();
            return;
        }
        positionHistory.add(
                player.getPosition(),
                player.getBoundingBox(),
                player.getInstance(),
                pendingFrame.nowNanos());
        if (!pendingClean) {
            prediction.reset();
            stableGroundFrames = 0;
            return;
        }
        prediction.observe(pendingFrame);
        previousHorizontalDistance = pendingFrame.horizontalDistance();
        previousDeltaY = pendingFrame.deltaY();
        if (pendingCollision.supported()) {
            airFrames = 0;
            stableGroundFrames = Math.min(1000, stableGroundFrames + 1);
            if (pendingCollision.complete()
                    && !pendingCollision.insideSolid()
                    && stableGroundFrames >= 2) lastSafePosition(pendingFrame.to());
        } else {
            airFrames = Math.min(1000, airFrames + 1);
            stableGroundFrames = 0;
        }
    }

    public void invalidatePending() {
        pending = false;
    }

    public Pos lastSafePosition() {
        return lastSafePosition;
    }

    public void lastSafePosition(Pos position) {
        this.lastSafePosition = position;
        this.safeInstance = player.getInstance();
        this.hasSafePosition = safeInstance != null;
    }

    public long nextMovementSequence() {
        return ++movementSequence;
    }

    public long lastMovementPacketNanos() {
        return lastMovementPacketNanos;
    }

    public void lastMovementPacketNanos(long value) {
        this.lastMovementPacketNanos = value;
        this.hasMovementPacket = true;
    }

    public double movementPacketBalance() {
        return movementPacketBalance;
    }

    public void movementPacketBalance(double value) {
        this.movementPacketBalance = value;
    }

    public double previousHorizontalDistance() {
        return previousHorizontalDistance;
    }

    public void previousHorizontalDistance(double value) {
        this.previousHorizontalDistance = value;
    }

    public double previousDeltaY() {
        return previousDeltaY;
    }

    public void previousDeltaY(double value) {
        this.previousDeltaY = value;
    }

    public int airFrames() {
        return airFrames;
    }

    public void airFrames(int value) {
        this.airFrames = value;
    }

    public int stableGroundFrames() {
        return stableGroundFrames;
    }

    public void stableGroundFrames(int value) {
        this.stableGroundFrames = value;
    }

    public void resetMotion(Pos position, long nowNanos) {
        previousHorizontalDistance = 0.0;
        previousDeltaY = 0.0;
        airFrames = 0;
        stableGroundFrames = 0;
        prediction.reset();
        impulse.reset();
        pending = false;
        lastSafePosition(position);
        digging.clear();
        positionHistory.clear();
        positionHistory.add(position, player.getBoundingBox(), player.getInstance(), nowNanos);
    }
}
