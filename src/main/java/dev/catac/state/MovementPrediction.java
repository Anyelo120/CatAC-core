package dev.catac.state;

import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.potion.PotionEffect;

/** Conservative vector envelope trained exclusively on finally accepted clean frames. */
public final class MovementPrediction {
    private boolean initialized, wasSupported;
    private double vx, vy, vz, horizontalLimit, verticalUpperBound, verticalLowerBound;
    private double expectedX, expectedZ, residualLimit;
    private float previousFriction = .6f;

    public void prepare(MovementFrame f, PlayerSynchronization sync) {
        prepare(f, sync, new ImpulseState());
    }

    public void prepare(MovementFrame f, PlayerSynchronization sync, ImpulseState impulse) {
        Player p = f.player();
        CollisionSnapshot c = f.collision();
        double speed = Math.max(0, p.getAttributeValue(Attribute.MOVEMENT_SPEED));
        double tolerance = .035 + sync.latencyAllowanceMillis() * .0002;
        double friction = wasSupported ? Math.clamp(previousFriction * .91, .2, .99) : .91;
        double acceleration =
                wasSupported ? speed * .21600002 / (friction * friction * friction) * 1.3 : .026;
        expectedX = vx * friction;
        expectedZ = vz * friction;
        var external = impulse.allowance(f.nowNanos());
        double baseline = speed * 3.3 * c.surfaceSpeedFactor();
        residualLimit = acceleration + tolerance + .12 + Math.hypot(external.x(), external.z());
        horizontalLimit =
                Math.max(Math.hypot(expectedX, expectedZ) + acceleration, baseline)
                        + tolerance
                        + Math.hypot(external.x(), external.z());
        double jump =
                p.getAttributeValue(Attribute.JUMP_STRENGTH)
                        + Math.max(0, p.getEffectLevel(PotionEffect.JUMP_BOOST) + 1) * .1;
        double gravity = p.hasNoGravity() ? 0 : p.getAttributeValue(Attribute.GRAVITY);
        int levitation = p.getEffectLevel(PotionEffect.LEVITATION);
        double expected =
                levitation >= 0
                        ? (vy + (.05 * (levitation + 1) - vy) * .2) * .98
                        : (vy
                                        - (p.hasEffect(PotionEffect.SLOW_FALLING) && vy <= 0
                                                ? Math.min(gravity, .01)
                                                : gravity))
                                * .98;
        verticalUpperBound = (!initialized || wasSupported) ? jump + .045 : expected + .055;
        // A step is legal only when continuous geometry verified a collision-free route and
        // support.
        if (c.supported() && !c.sweptIntoSolid() && !c.insideSolid())
            verticalUpperBound =
                    Math.max(verticalUpperBound, p.getAttributeValue(Attribute.STEP_HEIGHT) + .01);
        verticalUpperBound += Math.max(0, external.y()) + tolerance * .25;
        verticalLowerBound =
                initialized && !wasSupported ? expected - .12 : Double.NEGATIVE_INFINITY;
    }

    public void observe(MovementFrame f) {
        vx = f.deltaX();
        vy = f.deltaY();
        vz = f.deltaZ();
        wasSupported = f.collision().supported();
        previousFriction = f.collision().surfaceFriction();
        initialized = true;
    }

    public void reset() {
        initialized = false;
        wasSupported = false;
        vx = vy = vz = expectedX = expectedZ = 0;
        horizontalLimit = .38;
        verticalUpperBound = .475;
        verticalLowerBound = Double.NEGATIVE_INFINITY;
    }

    public boolean initialized() {
        return initialized;
    }

    public boolean wasSupported() {
        return wasSupported;
    }

    public double horizontalLimit() {
        return horizontalLimit;
    }

    public double verticalUpperBound() {
        return verticalUpperBound;
    }

    public double verticalLowerBound() {
        return verticalLowerBound;
    }

    public double previousVerticalVelocity() {
        return vy;
    }

    public double residualLimit() {
        return residualLimit;
    }

    public double vectorResidual(MovementFrame f) {
        return Math.hypot(f.deltaX() - expectedX, f.deltaZ() - expectedZ);
    }

    static double nextGroundHorizontal(double previous, double acceleration, double friction) {
        return previous * friction + acceleration;
    }

    static double nextAirHorizontal(double previous, double speed, boolean sneaking) {
        return previous * .91 + speed * .02 * (sneaking ? .3 : 1);
    }

    static double nextVertical(double previous) {
        return (previous - .08) * .98;
    }
}
