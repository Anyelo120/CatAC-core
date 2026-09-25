package dev.catac.state;

import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.potion.PotionEffect;

/**
 * Per-player, allocation-free movement model. It intentionally predicts an
 * upper envelope instead of replaying client input, which is unavailable to
 * the server and would otherwise create false positives on packet bursts.
 */
public final class MovementPrediction {
    private static final double AIR_DRAG = 0.91;
    private static final double GRAVITY = 0.08;
    private static final double VERTICAL_DRAG = 0.98;
    private static final double JUMP_VELOCITY = 0.42;
    private static final double GROUND_ACCELERATION_FACTOR = 0.16277136;

    private boolean initialized;
    private boolean wasSupported;
    private double horizontalVelocity;
    private double verticalVelocity;
    private double horizontalLimit;
    private double verticalUpperBound;

    public void prepare(MovementFrame frame, PlayerSynchronization synchronization) {
        Player player = frame.player();
        double movementSpeed = player.getAttribute(Attribute.MOVEMENT_SPEED).getValue();
        double latencyAllowance = synchronization.latencyAllowanceMillis() * 0.00035;
        CollisionSnapshot collision = frame.collision();

        double previousHorizontal = initialized ? horizontalVelocity : frame.horizontalDistance();
        if (wasSupported) {
            double friction = Math.max(0.2, collision.surfaceFriction() * 0.91);
            double acceleration = groundAcceleration(movementSpeed, friction, player.isSneaking());
            double modeled = nextGroundHorizontal(previousHorizontal, acceleration, friction);
            double fallback = Math.max(0.33, movementSpeed * 3.30) * collision.surfaceSpeedFactor();
            horizontalLimit = Math.max(modeled, fallback) + 0.045 + latencyAllowance;
        } else {
            double modeled = nextAirHorizontal(previousHorizontal, movementSpeed, player.isSneaking());
            double carriedGroundSpeed = Math.max(0.33, movementSpeed * 3.30);
            horizontalLimit = Math.max(modeled, carriedGroundSpeed) + 0.055 + latencyAllowance;
        }

        if (!initialized || wasSupported) {
            verticalUpperBound = jumpVelocity(player) + 0.055 + latencyAllowance * 0.35;
        } else {
            verticalUpperBound = nextVertical(verticalVelocity) + 0.075 + latencyAllowance * 0.35;
        }
    }

    public void observe(MovementFrame frame) {
        horizontalVelocity = frame.horizontalDistance();
        verticalVelocity = frame.deltaY();
        wasSupported = frame.collision().supported();
        initialized = true;
    }

    public void reset() {
        initialized = false;
        wasSupported = false;
        horizontalVelocity = 0.0;
        verticalVelocity = 0.0;
        horizontalLimit = 0.0;
        verticalUpperBound = JUMP_VELOCITY + 0.055;
    }

    public boolean initialized() { return initialized; }
    public boolean wasSupported() { return wasSupported; }
    public double horizontalLimit() { return horizontalLimit; }
    public double verticalUpperBound() { return verticalUpperBound; }
    public double previousVerticalVelocity() { return verticalVelocity; }

    static double nextGroundHorizontal(double previous, double acceleration, double friction) {
        return previous * friction + acceleration;
    }

    static double nextAirHorizontal(double previous, double movementSpeed, boolean sneaking) {
        double acceleration = movementSpeed * 0.02 * (sneaking ? 0.3 : 1.0);
        return previous * AIR_DRAG + acceleration;
    }

    static double nextVertical(double previous) {
        return (previous - GRAVITY) * VERTICAL_DRAG;
    }

    private static double groundAcceleration(double movementSpeed, double friction, boolean sneaking) {
        double input = sneaking ? 0.3 : 1.0;
        return movementSpeed * GROUND_ACCELERATION_FACTOR / (friction * friction * friction) * input;
    }

    private static double jumpVelocity(Player player) {
        int level = player.getEffectLevel(PotionEffect.JUMP_BOOST);
        return JUMP_VELOCITY + Math.max(0, level + 1) * 0.1;
    }
}
