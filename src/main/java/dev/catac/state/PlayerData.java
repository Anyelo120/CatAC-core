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

    private Pos lastSafePosition;
    private long movementSequence;
    private long lastMovementPacketNanos;
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
        this.synchronization = new PlayerSynchronization(config.networkProbeIntervalNanos(),
                config.networkAcknowledgementTimeoutNanos());
        this.packetFlood = new PacketFloodState(config.packetFloodPolicy().totalBudget(),
                config.packetFloodPolicy().heavyBudget(), nowNanos);
        this.lastSafePosition = player.getPosition();
        this.positionHistory.add(player.getPosition(), nowNanos);
    }

    public Player player() { return player; }
    public ViolationState violation(int slot) { return violations[slot]; }
    public ExemptionState exemptions() { return exemptions; }
    public PlayerSynchronization synchronization() { return synchronization; }
    public MovementPrediction prediction() { return prediction; }
    public CollisionSnapshot collision() { return collision; }
    public MovementFrame movementFrame() { return movementFrame; }
    public DiggingState digging() { return digging; }
    public PositionHistory positionHistory() { return positionHistory; }
    public AuraDecoyState auraDecoy() { return auraDecoy; }
    public DamageGuardState damageGuard() { return damageGuard; }
    public PacketFloodState packetFlood() { return packetFlood; }
    public Pos lastSafePosition() { return lastSafePosition; }
    public void lastSafePosition(Pos position) { this.lastSafePosition = position; }
    public long nextMovementSequence() { return ++movementSequence; }
    public long lastMovementPacketNanos() { return lastMovementPacketNanos; }
    public void lastMovementPacketNanos(long value) { this.lastMovementPacketNanos = value; }
    public double movementPacketBalance() { return movementPacketBalance; }
    public void movementPacketBalance(double value) { this.movementPacketBalance = value; }
    public double previousHorizontalDistance() { return previousHorizontalDistance; }
    public void previousHorizontalDistance(double value) { this.previousHorizontalDistance = value; }
    public double previousDeltaY() { return previousDeltaY; }
    public void previousDeltaY(double value) { this.previousDeltaY = value; }
    public int airFrames() { return airFrames; }
    public void airFrames(int value) { this.airFrames = value; }
    public int stableGroundFrames() { return stableGroundFrames; }
    public void stableGroundFrames(int value) { this.stableGroundFrames = value; }

    public void resetMotion(Pos position, long nowNanos) {
        previousHorizontalDistance = 0.0;
        previousDeltaY = 0.0;
        airFrames = 0;
        stableGroundFrames = 0;
        prediction.reset();
        lastSafePosition = position;
        positionHistory.add(position, nowNanos);
    }
}
